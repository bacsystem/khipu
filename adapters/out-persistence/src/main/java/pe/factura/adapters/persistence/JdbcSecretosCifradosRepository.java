package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.SecretosCifradosRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Las columnas cifradas con la MASTER_KEY, y el recifrado para rotarla (S2). Una columna nueva cifrada con la MASTER_KEY tiene que agregarse a
 * {@link #COLUMNAS}: si no, al quitar MASTER_KEY_ANTERIOR quedaría ilegible.
 */
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

    @Override public int pendientes() {
        if (!cipher.rotando()) return 0;
        int n = 0;
        for (Columna c : COLUMNAS) for (Fila f : filas(c)) if (cipher.necesitaRecifrar(f.valor())) n++;
        return n;
    }

    @Override public int recifrar() {
        if (!cipher.rotando()) return 0;
        int n = 0;
        for (Columna c : COLUMNAS) {
            String condicion = String.join(" AND ", c.clave().stream().map(k -> k + " = ?").toList());
            for (Fila f : filas(c)) {
                if (!cipher.necesitaRecifrar(f.valor())) continue;
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
