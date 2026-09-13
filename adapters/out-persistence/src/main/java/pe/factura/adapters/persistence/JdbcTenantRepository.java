package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.tenant.*;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class JdbcTenantRepository implements TenantRepository {
    private static final String COLS = "id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta";
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    public JdbcTenantRepository(JdbcTemplate jdbc, SecretCipher cipher) { this.jdbc = jdbc; this.cipher = cipher; }

    @Override public void guardar(Tenant t) {
        byte[] su = t.sol() == null ? null : cipher.cifrar(t.sol().usuario().getBytes(StandardCharsets.UTF_8));
        byte[] sc = t.sol() == null ? null : cipher.cifrar(t.sol().clave().getBytes(StandardCharsets.UTF_8));
        byte[] cp = t.certificado() == null ? null : cipher.cifrar(t.certificado().pkcs12());
        byte[] cc = t.certificado() == null ? null : cipher.cifrar(t.certificado().clave().getBytes(StandardCharsets.UTF_8));
        Date cv = t.certificado() == null || t.certificado().vigenciaHasta() == null ? null : Date.valueOf(t.certificado().vigenciaHasta());
        jdbc.update("""
            INSERT INTO tenant (id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET razon_social = EXCLUDED.razon_social, entorno = EXCLUDED.entorno,
              sol_usuario_enc = EXCLUDED.sol_usuario_enc, sol_clave_enc = EXCLUDED.sol_clave_enc,
              cert_pkcs12_enc = EXCLUDED.cert_pkcs12_enc, cert_clave_enc = EXCLUDED.cert_clave_enc,
              cert_vigencia_hasta = EXCLUDED.cert_vigencia_hasta, updated_at = now()
            """, t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), su, sc, cp, cc, cv);
    }
    @Override public Optional<Tenant> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE id = ?", this::mapear, id).stream().findFirst();
    }
    @Override public Optional<Tenant> buscarPorRuc(String ruc) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE ruc = ?", this::mapear, ruc).stream().findFirst();
    }
    private Tenant mapear(ResultSet rs, int i) throws SQLException {
        CredencialesSol sol = rs.getBytes("sol_usuario_enc") == null ? null
                : new CredencialesSol(txt(rs.getBytes("sol_usuario_enc")), txt(rs.getBytes("sol_clave_enc")));
        Date cv = rs.getDate("cert_vigencia_hasta");
        CertificadoDigital cert = rs.getBytes("cert_pkcs12_enc") == null ? null
                : new CertificadoDigital(cipher.descifrar(rs.getBytes("cert_pkcs12_enc")), txt(rs.getBytes("cert_clave_enc")), cv == null ? null : cv.toLocalDate());
        return new Tenant(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"),
                Entorno.valueOf(rs.getString("entorno")), sol, cert);
    }
    private String txt(byte[] enc) { return new String(cipher.descifrar(enc), StandardCharsets.UTF_8); }
}
