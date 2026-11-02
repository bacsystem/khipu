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
            + "dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, dom_establecimiento, cuenta_detracciones, nombre_comercial, "
            + "pdf_plantilla, pdf_color, pdf_logo_key, pdf_pie, pdf_observaciones, padron_tasa_especial_igv, api_sunat_client_id_enc, api_sunat_client_secret_enc, "
            + "agente_retencion, agente_percepcion";
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    /**
     * La designación de agente (#40) no se escribe aquí: la cambia solo el administrador, con {@code JdbcAccionesDeEmpresaRepository.designarAgente}, de forma condicional. Si guardar
     * la reescribiera, unos datos fiscales guardados con el tenant leído antes de una designación la borrarían. LOG-05: por la misma razón, sobre una fila existente tampoco
     * se reescriben el entorno ({@code cambiarEntorno}) ni los secretos (certificado, SOL, API SUNAT: cada uno con su método). Los secretos sí van en el INSERT: el alta
     * asistida crea la empresa con ellos.
     */
    @Override public void guardar(Tenant t) {
        byte[] su = t.sol() == null ? null : cipher.cifrar(t.sol().usuario().getBytes(StandardCharsets.UTF_8));
        byte[] sc = t.sol() == null ? null : cipher.cifrar(t.sol().clave().getBytes(StandardCharsets.UTF_8));
        byte[] cp = t.certificado() == null ? null : cipher.cifrar(t.certificado().pkcs12());
        byte[] cc = t.certificado() == null ? null : cipher.cifrar(t.certificado().clave().getBytes(StandardCharsets.UTF_8));
        byte[] ai = t.apiSunat() == null ? null : cipher.cifrar(t.apiSunat().clientId().getBytes(StandardCharsets.UTF_8));
        byte[] as = t.apiSunat() == null ? null : cipher.cifrar(t.apiSunat().clientSecret().getBytes(StandardCharsets.UTF_8));
        Date cv = t.certificado() == null || t.certificado().vigenciaHasta() == null ? null : Date.valueOf(t.certificado().vigenciaHasta());
        Domicilio d = t.domicilio();
        PersonalizacionPdf p = t.personalizacionPdf();
        jdbc.update("""
            INSERT INTO tenant (id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc, cert_pkcs12_enc, cert_clave_enc, cert_vigencia_hasta,
              dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, dom_establecimiento, cuenta_detracciones, nombre_comercial,
              pdf_plantilla, pdf_color, pdf_logo_key, pdf_pie, pdf_observaciones, padron_tasa_especial_igv, api_sunat_client_id_enc, api_sunat_client_secret_enc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET razon_social = EXCLUDED.razon_social,
              dom_ubigeo = EXCLUDED.dom_ubigeo, dom_direccion = EXCLUDED.dom_direccion, dom_urbanizacion = EXCLUDED.dom_urbanizacion,
              dom_distrito = EXCLUDED.dom_distrito, dom_provincia = EXCLUDED.dom_provincia, dom_departamento = EXCLUDED.dom_departamento,
              dom_establecimiento = EXCLUDED.dom_establecimiento, cuenta_detracciones = EXCLUDED.cuenta_detracciones, nombre_comercial = EXCLUDED.nombre_comercial,
              pdf_plantilla = EXCLUDED.pdf_plantilla, pdf_color = EXCLUDED.pdf_color, pdf_logo_key = EXCLUDED.pdf_logo_key, pdf_pie = EXCLUDED.pdf_pie,
              pdf_observaciones = EXCLUDED.pdf_observaciones, padron_tasa_especial_igv = EXCLUDED.padron_tasa_especial_igv, updated_at = now()
            """, t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), su, sc, cp, cc, cv,
                d == null ? null : d.ubigeo(), d == null ? null : d.direccion(), d == null ? null : d.urbanizacion(), d == null ? null : d.distrito(),
                d == null ? null : d.provincia(), d == null ? null : d.departamento(), d == null ? null : d.codigoEstablecimiento(), t.cuentaDetracciones(), t.nombreComercial(),
                p.plantilla().name(), p.colorPrimario(), p.logoKey(), p.pieDePagina(), p.observacionesPorDefecto(), t.padronTasaEspecialIgv(), ai, as);
    }

    // LOG-05: escrituras por sección. Cada UPDATE toca solo sus columnas; lo que otro guardó en otra sección (o el entorno, o la designación) no se pisa.
    @Override public void guardarDatosFiscales(UUID tenantId, Domicilio d, String cuentaDetracciones, String nombreComercial, boolean padronTasaEspecialIgv) {
        exigirUnaFila(jdbc.update("""
            UPDATE tenant SET dom_ubigeo = ?, dom_direccion = ?, dom_urbanizacion = ?, dom_distrito = ?, dom_provincia = ?, dom_departamento = ?, dom_establecimiento = ?,
              cuenta_detracciones = ?, nombre_comercial = ?, padron_tasa_especial_igv = ?, updated_at = now() WHERE id = ?
            """, d == null ? null : d.ubigeo(), d == null ? null : d.direccion(), d == null ? null : d.urbanizacion(), d == null ? null : d.distrito(),
                d == null ? null : d.provincia(), d == null ? null : d.departamento(), d == null ? null : d.codigoEstablecimiento(), cuentaDetracciones, nombreComercial,
                padronTasaEspecialIgv, tenantId));
    }
    @Override public void guardarCertificado(UUID tenantId, CertificadoDigital c) {
        Date cv = c == null || c.vigenciaHasta() == null ? null : Date.valueOf(c.vigenciaHasta());
        exigirUnaFila(jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = ?, updated_at = now() WHERE id = ?",
                c == null ? null : cipher.cifrar(c.pkcs12()), c == null ? null : cipher.cifrar(c.clave().getBytes(StandardCharsets.UTF_8)), cv, tenantId));
    }
    @Override public void guardarCredencialesSol(UUID tenantId, CredencialesSol s) {
        exigirUnaFila(jdbc.update("UPDATE tenant SET sol_usuario_enc = ?, sol_clave_enc = ?, updated_at = now() WHERE id = ?",
                s == null ? null : cipher.cifrar(s.usuario().getBytes(StandardCharsets.UTF_8)), s == null ? null : cipher.cifrar(s.clave().getBytes(StandardCharsets.UTF_8)), tenantId));
    }
    @Override public void guardarCredencialesApiSunat(UUID tenantId, CredencialesApiSunat a) {
        exigirUnaFila(jdbc.update("UPDATE tenant SET api_sunat_client_id_enc = ?, api_sunat_client_secret_enc = ?, updated_at = now() WHERE id = ?",
                a == null ? null : cipher.cifrar(a.clientId().getBytes(StandardCharsets.UTF_8)), a == null ? null : cipher.cifrar(a.clientSecret().getBytes(StandardCharsets.UTF_8)), tenantId));
    }
    @Override public void guardarPersonalizacionPdf(UUID tenantId, PersonalizacionPdf p) {
        exigirUnaFila(jdbc.update("UPDATE tenant SET pdf_plantilla = ?, pdf_color = ?, pdf_logo_key = ?, pdf_pie = ?, pdf_observaciones = ?, updated_at = now() WHERE id = ?",
                p.plantilla().name(), p.colorPrimario(), p.logoKey(), p.pieDePagina(), p.observacionesPorDefecto(), tenantId));
    }
    private static void exigirUnaFila(int filas) {
        if (filas != 1) throw new pe.factura.domain.DomainException("NO_ENCONTRADO", "Tenant no encontrado");
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
        PersonalizacionPdf pdf = new PersonalizacionPdf(PlantillaPdf.valueOf(rs.getString("pdf_plantilla")), rs.getString("pdf_color"), rs.getString("pdf_logo_key"),
                rs.getString("pdf_pie"), rs.getString("pdf_observaciones"));
        CredencialesApiSunat api = rs.getBytes("api_sunat_client_id_enc") == null ? null
                : new CredencialesApiSunat(txt(rs.getBytes("api_sunat_client_id_enc")), txt(rs.getBytes("api_sunat_client_secret_enc")));
        return new Tenant(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"),
                Entorno.valueOf(rs.getString("entorno")), sol, cert, dom, rs.getString("cuenta_detracciones"), rs.getString("nombre_comercial"), pdf, rs.getBoolean("padron_tasa_especial_igv"), api,
                new DesignacionDeAgente(rs.getBoolean("agente_retencion"), rs.getBoolean("agente_percepcion")));
    }
    private String txt(byte[] enc) { return new String(cipher.descifrar(enc), StandardCharsets.UTF_8); }
}
