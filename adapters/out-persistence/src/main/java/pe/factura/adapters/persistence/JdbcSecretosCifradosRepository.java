package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.SecretosCifradosRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Las columnas cifradas con la MASTER_KEY, y el recifrado para rotarla (S2). Una columna nueva cifrada con la MASTER_KEY tiene que agregarse a
 * {@link #COLUMNAS}: si no, al quitar MASTER_KEY_ANTERIOR quedaría ilegible.
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcSecretosCifradosRepository implements SecretosCifradosRepository {
    record Columna(String tabla, List<String> clave, String columna) {}

    static final List<Columna> COLUMNAS = List.of(
            new Columna("tenant", List.of("id"), "sol_usuario_enc"),
            new Columna("tenant", List.of("id"), "sol_clave_enc"),
            new Columna("tenant", List.of("id"), "cert_pkcs12_enc"),
            new Columna("tenant", List.of("id"), "cert_clave_enc"),
            new Columna("administrador_segundo_factor", List.of("administrador_id"), "secreto_cifrado"),
            new Columna("idempotencia", List.of("alcance", "clave"), "respuesta_cifrada"));

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    /**
     * Lo que no abre con la clave vigente: lo que todavía está con la anterior y lo que no abre con ninguna. Se mide también sin rotación: un valor que una
     * instancia vieja escribió con la clave anterior después del recifrado (despliegue con solapamiento) queda ilegible al quitar MASTER_KEY_ANTERIOR, y
     * contarlo es lo que permite avisarlo al arrancar en vez de enterarse al emitir.
     */
    @Override public int pendientes() {
        int n = 0;
        for (Columna c : COLUMNAS) for (Fila f : filas(c)) if (estado(f.valor()) != Estado.VIGENTE) n++;
        return n;
    }

    @Override public int recifrar() {
        if (!cipher.rotando()) return 0;
        int n = 0;
        for (Columna c : COLUMNAS) {
            String condicion = String.join(" AND ", c.clave().stream().map(k -> k + " = ?").toList());
            for (Fila f : filas(c)) {
                Estado e = estado(f.valor());
                // Una fila que no abre con ninguna clave (dato alterado, o de otro entorno) no frena las demás: queda en `pendientes`, y se dice cuál.
                if (e == Estado.ILEGIBLE) log.error("MASTER_KEY: {}.{} de {} no abre con la clave vigente ni con la anterior; no se recifra", c.tabla(), c.columna(), f.clave());
                if (e != Estado.ANTERIOR) continue;
                byte[] nuevo = cipher.cifrar(cipher.descifrar(f.valor()));
                List<Object> args = new ArrayList<>();
                args.add(nuevo);
                args.addAll(f.clave());
                args.add(f.valor());
                // Solo si sigue siendo el valor leído: lo que otra réplica (o el usuario) cambió mientras tanto no se pisa.
                n += jdbc.update("UPDATE " + c.tabla() + " SET " + c.columna() + " = ? WHERE " + condicion + " AND " + c.columna() + " = ?", args.toArray());
            }
        }
        return n;
    }

    private enum Estado { VIGENTE, ANTERIOR, ILEGIBLE }

    /** Con rotación, `necesitaRecifrar` distingue vigente de anterior; sin ella no mira nada, así que se prueba abrir con la única clave que hay. */
    private Estado estado(byte[] valor) {
        try {
            if (cipher.necesitaRecifrar(valor)) return Estado.ANTERIOR;
            if (!cipher.rotando()) cipher.descifrar(valor);
            return Estado.VIGENTE;
        } catch (IllegalStateException e) {
            return Estado.ILEGIBLE;
        }
    }

    private record Fila(List<Object> clave, byte[] valor) {}

    private List<Fila> filas(Columna c) {
        String select = String.join(", ", c.clave()) + ", " + c.columna();
        return jdbc.query("SELECT " + select + " FROM " + c.tabla() + " WHERE " + c.columna() + " IS NOT NULL", (rs, i) -> {
            List<Object> clave = new ArrayList<>();
            for (String k : c.clave()) clave.add(rs.getObject(k));
            return new Fila(clave, rs.getBytes(c.columna()));
        });
    }
}
