package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * El padrón de SUNAT son ~18 millones de filas (#361): se carga con {@code COPY}, no con inserciones por fila, a una tabla de paso y de ahí se mezcla con {@code padron_ruc} en
 * la misma transacción. La mezcla solo toca lo que cambió (SUNAT cambia unas decenas de miles de RUC por día), así que la carga diaria no reescribe la tabla ni deja 18 millones
 * de filas muertas; y como todo va en una transacción, quien consulta ve el padrón anterior hasta el {@code COMMIT}.
 */
@RequiredArgsConstructor
public class JdbcPadronRucRepository implements PadronRucRepository {
    /** Para que dos instancias (o dos disparos a mano) no carguen a la vez; se suelta sola al terminar la transacción. */
    private static final long CANDADO_DE_CARGA = 361_361L;
    private static final int TAMANO_DEL_BLOQUE = 1 << 16;

    private final JdbcTemplate jdbc;

    @Override public Optional<Contribuyente> buscar(String ruc) {
        return jdbc.query("SELECT ruc, razon_social, estado, condicion, ubigeo, direccion FROM padron_ruc WHERE ruc = ?",
                (rs, n) -> new Contribuyente(rs.getString("ruc"), rs.getString("razon_social"), rs.getString("estado"), rs.getString("condicion"), rs.getString("ubigeo"), rs.getString("direccion")),
                ruc).stream().findFirst();
    }

    @Override public Optional<CargaRegistrada> ultimaCargaExitosa() {
        return jdbc.query("""
                SELECT iniciada, terminada, origen, filas, descartadas, nuevas, cambiadas, borradas
                FROM carga_padron_ruc WHERE resultado = 'EXITOSA' ORDER BY terminada DESC LIMIT 1
                """, (rs, n) -> new CargaRegistrada(rs.getTimestamp("iniciada").toInstant(), rs.getTimestamp("terminada").toInstant(), true, rs.getString("origen"),
                rs.getLong("filas"), rs.getLong("descartadas"), rs.getLong("nuevas"), rs.getLong("cambiadas"), rs.getLong("borradas"), null)).stream().findFirst();
    }

    @Override public void registrar(CargaRegistrada c) {
        jdbc.update("""
                INSERT INTO carga_padron_ruc (iniciada, terminada, resultado, origen, filas, descartadas, nuevas, cambiadas, borradas, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Timestamp.from(c.iniciada()), Timestamp.from(c.terminada()), c.exitosa() ? "EXITOSA" : "FALLIDA", c.origen(), c.filas(), c.descartadas(), c.nuevas(), c.cambiadas(),
                c.borradas(), c.error());
    }

    @Override public ResultadoCarga reemplazar(Stream<Contribuyente> filas, long minimoDeFilas) {
        ResultadoCarga r = jdbc.execute((ConnectionCallback<ResultadoCarga>) con -> {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                ResultadoCarga hecho = cargarYMezclar(con, filas, minimoDeFilas);
                con.commit();
                return hecho;
            } catch (RuntimeException | SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        });
        // Fuera de la transacción: ANALYZE no necesita ir dentro y, tras una primera carga de millones de filas, el planificador no debe seguir creyendo que la tabla está vacía.
        jdbc.execute("ANALYZE padron_ruc");
        return r;
    }

    private ResultadoCarga cargarYMezclar(Connection con, Stream<Contribuyente> filas, long minimoDeFilas) throws SQLException {
        try (Statement st = con.createStatement()) {
            if (!tomarCandado(st)) throw new DomainException("CARGA_EN_CURSO", "Ya hay una carga del padrón de RUC en curso");
            st.execute("TRUNCATE padron_ruc_carga");
            long cargadas = copiar(con, filas);
            if (cargadas < minimoDeFilas) {
                throw new DomainException("PADRON_INCOMPLETO", "El archivo del padrón trae " + cargadas + " filas y se esperaban al menos " + minimoDeFilas + ": se conserva el padrón anterior");
            }
            long cambiadas = st.executeUpdate("""
                    UPDATE padron_ruc p SET razon_social = c.razon_social, estado = c.estado, condicion = c.condicion, ubigeo = c.ubigeo, direccion = c.direccion
                    FROM padron_ruc_carga c
                    WHERE p.ruc = c.ruc
                      AND (p.razon_social, p.estado, p.condicion, p.ubigeo, p.direccion) IS DISTINCT FROM (c.razon_social, c.estado, c.condicion, c.ubigeo, c.direccion)
                    """);
            long nuevas = st.executeUpdate("""
                    INSERT INTO padron_ruc (ruc, razon_social, estado, condicion, ubigeo, direccion)
                    SELECT c.ruc, c.razon_social, c.estado, c.condicion, c.ubigeo, c.direccion FROM padron_ruc_carga c
                    WHERE NOT EXISTS (SELECT 1 FROM padron_ruc p WHERE p.ruc = c.ruc)
                    """);
            long borradas = st.executeUpdate("DELETE FROM padron_ruc p WHERE NOT EXISTS (SELECT 1 FROM padron_ruc_carga c WHERE c.ruc = p.ruc)");
            // Vaciarla dentro de la transacción devuelve el espacio al confirmar: son gigabytes que no deben quedar ocupados hasta la carga siguiente.
            st.execute("TRUNCATE padron_ruc_carga");
            return new ResultadoCarga(cargadas, nuevas, cambiadas, borradas);
        }
    }

    private static boolean tomarCandado(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT pg_try_advisory_xact_lock(" + CANDADO_DE_CARGA + ")")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    /** {@code COPY … FROM STDIN} en formato de texto de Postgres; devuelve cuántas filas escribió. */
    private static long copiar(Connection con, Stream<Contribuyente> filas) throws SQLException {
        CopyIn copia = con.unwrap(PGConnection.class).getCopyAPI().copyIn("COPY padron_ruc_carga (ruc, razon_social, estado, condicion, ubigeo, direccion) FROM STDIN");
        long n = 0;
        try {
            StringBuilder bloque = new StringBuilder(TAMANO_DEL_BLOQUE + 1024);
            Iterator<Contribuyente> it = filas.iterator();
            while (it.hasNext()) {
                Contribuyente c = it.next();
                bloque.append(c.ruc()).append('\t').append(campo(c.razonSocial())).append('\t').append(campo(c.estado())).append('\t').append(campo(c.condicion())).append('\t')
                        .append(campo(c.ubigeo())).append('\t').append(campo(c.direccion())).append('\n');
                n++;
                if (bloque.length() >= TAMANO_DEL_BLOQUE) {
                    escribir(copia, bloque);
                    bloque.setLength(0);
                }
            }
            escribir(copia, bloque);
            copia.endCopy();
            return n;
        } catch (RuntimeException | SQLException e) {
            if (copia.isActive()) copia.cancelCopy();
            throw e;
        }
    }

    private static void escribir(CopyIn copia, StringBuilder bloque) throws SQLException {
        if (bloque.length() == 0) return;
        byte[] bytes = bloque.toString().getBytes(StandardCharsets.UTF_8);
        copia.writeToCopy(bytes, 0, bytes.length);
    }

    /** Un valor del formato de texto de {@code COPY}: {@code \N} es nulo, y la barra, el tabulador y los saltos de línea se escapan. */
    static String campo(String valor) {
        if (valor == null) return "\\N";
        StringBuilder s = new StringBuilder(valor.length() + 8);
        for (int i = 0; i < valor.length(); i++) {
            char ch = valor.charAt(i);
            switch (ch) {
                case '\\' -> s.append("\\\\");
                case '\t' -> s.append("\\t");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\u0000' -> { }
                default -> s.append(ch);
            }
        }
        return s.toString();
    }
}
