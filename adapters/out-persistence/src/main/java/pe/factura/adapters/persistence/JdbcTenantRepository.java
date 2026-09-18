package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.tenant.*;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcTenantRepository implements TenantRepository {
    private static final String COLS = "id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta, "
            + "dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, dom_establecimiento, cuenta_detracciones";
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    @Override public void guardar(Tenant t) {
        byte[] su = t.sol() == null ? null : cipher.cifrar(t.sol().usuario().getBytes(StandardCharsets.UTF_8));
        byte[] sc = t.sol() == null ? null : cipher.cifrar(t.sol().clave().getBytes(StandardCharsets.UTF_8));
        byte[] cp = t.certificado() == null ? null : cipher.cifrar(t.certificado().pkcs12());
        byte[] cc = t.certificado() == null ? null : cipher.cifrar(t.certificado().clave().getBytes(StandardCharsets.UTF_8));
        Date cv = t.certificado() == null || t.certificado().vigenciaHasta() == null ? null : Date.valueOf(t.certificado().vigenciaHasta());
        Domicilio d = t.domicilio();
        jdbc.update("""
            INSERT INTO tenant (id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta,
              dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, dom_establecimiento, cuenta_detracciones)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET razon_social = EXCLUDED.razon_social, entorno = EXCLUDED.entorno,
              sol_usuario_enc = EXCLUDED.sol_usuario_enc, sol_clave_enc = EXCLUDED.sol_clave_enc,
              cert_pkcs12_enc = EXCLUDED.cert_pkcs12_enc, cert_clave_enc = EXCLUDED.cert_clave_enc,
              cert_vigencia_hasta = EXCLUDED.cert_vigencia_hasta,
              dom_ubigeo = EXCLUDED.dom_ubigeo, dom_direccion = EXCLUDED.dom_direccion, dom_urbanizacion = EXCLUDED.dom_urbanizacion,
              dom_distrito = EXCLUDED.dom_distrito, dom_provincia = EXCLUDED.dom_provincia, dom_departamento = EXCLUDED.dom_departamento,
              dom_establecimiento = EXCLUDED.dom_establecimiento, cuenta_detracciones = EXCLUDED.cuenta_detracciones, updated_at = now()
            """, t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), su, sc, cp, cc, cv,
                d == null ? null : d.ubigeo(), d == null ? null : d.direccion(), d == null ? null : d.urbanizacion(), d == null ? null : d.distrito(),
                d == null ? null : d.provincia(), d == null ? null : d.departamento(), d == null ? null : d.codigoEstablecimiento(), t.cuentaDetracciones());
    }
    @Override public Optional<Tenant> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE id = ?", this::mapear, id).stream().findFirst();
    }
    @Override public List<Tenant> listarPorCuenta(UUID cuentaId) {
        return jdbc.query("SELECT " + COLS + " FROM tenant WHERE cuenta_id = ? ORDER BY created_at", this::mapear, cuentaId);
    }
    @Override public void asignarCuenta(UUID tenantId, UUID cuentaId) {
        jdbc.update("UPDATE tenant SET cuenta_id = ?, updated_at = now() WHERE id = ?", cuentaId, tenantId);
    }
    @Override public Optional<UUID> cuentaDe(UUID tenantId) {
        return jdbc.queryForList("SELECT cuenta_id FROM tenant WHERE id = ? AND cuenta_id IS NOT NULL", UUID.class, tenantId).stream().findFirst();
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
        Domicilio dom = rs.getString("dom_ubigeo") == null ? null
                : new Domicilio(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"), rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"),
                        rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("dom_establecimiento"));
        return new Tenant(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"),
                Entorno.valueOf(rs.getString("entorno")), sol, cert, dom, rs.getString("cuenta_detracciones"));
    }
    private String txt(byte[] enc) { return new String(cipher.descifrar(enc), StandardCharsets.UTF_8); }
}
