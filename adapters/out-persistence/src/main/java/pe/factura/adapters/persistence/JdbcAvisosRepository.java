package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.VisibilidadDeBajas;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.tenant.FalloDeAutenticacionSol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los avisos a los clientes del backoffice (#197). El estado del certificado sale de la misma regla del listado de empresas ({@code JdbcEmpresasAdminRepository.ESTADO}); un fallo de
 * credenciales es lo que dice {@code FalloDeAutenticacionSol} sobre el último error de un envío que **sigue** atascado (un comprobante ya aceptado no cuenta). Las cuentas dadas de
 * baja no se avisan. Reservar un aviso toma un candado de la base por empresa y motivo: dos administradores a la vez se serializan y el segundo ve la reserva del primero.
 */
@RequiredArgsConstructor
public class JdbcAvisosRepository implements AvisosRepository {
    /** El último error de un envío es de credenciales: la misma regla que {@code FalloDeAutenticacionSol}, con sus constantes. */
    private static final String FALLO_DE_SOL = "(d.ultimo_error LIKE '%" + FalloDeAutenticacionSol.MARCA_HTTP_401 + "%' OR d.ultimo_error ~ '^(" + String.join("|", FalloDeAutenticacionSol.CODIGOS) + ") - ')";

    private static final String ENVIO_ATASCADO = "d.estado = '" + EstadoDocumento.ERROR_ENVIO.name() + "'";

    private static final String EN_RIESGO = "('" + EstadoCertificado.VENCIDO.name() + "', '" + EstadoCertificado.POR_VENCER.name() + "')";

    /** Las empresas de las cuentas dadas de baja ya no se avisan. */
    private static final String EN_SERVICIO = BajasEnListado.deEmpresa(VisibilidadDeBajas.OCULTAS);

    private static final String CUENTA = "c.id AS cuenta_id, c.nombre AS cuenta_nombre, c.email AS cuenta_email";

    private final JdbcTemplate jdbc;

    @Override public List<FilaCertificado> certificados(LocalDate hoy, int pagina, int porPagina) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT t.id, t.ruc, t.razon_social, %s, %s AS estado, t.cert_vigencia_hasta AS vigente_hasta, t.cert_vigencia_hasta - ?::date AS dias
                    FROM tenant t LEFT JOIN cuenta c ON c.id = t.cuenta_id WHERE %s
                ) x WHERE x.estado IN %s ORDER BY x.vigente_hasta, x.id LIMIT ? OFFSET ?
                """.formatted(CUENTA, JdbcEmpresasAdminRepository.ESTADO, EN_SERVICIO, EN_RIESGO), (rs, i) -> new FilaCertificado(rs.getObject("id", UUID.class), rs.getString("ruc"),
                rs.getString("razon_social"), destino(rs), EstadoCertificado.valueOf(rs.getString("estado")), rs.getObject("vigente_hasta", LocalDate.class), rs.getInt("dias")),
                hoy, hoy, hoy, porPagina, (long) (pagina - 1) * porPagina);
    }

    @Override public long contarCertificados(LocalDate hoy) {
        return jdbc.queryForObject("SELECT count(*) FROM (SELECT %s AS estado FROM tenant t WHERE %s) x WHERE x.estado IN %s".formatted(JdbcEmpresasAdminRepository.ESTADO, EN_SERVICIO, EN_RIESGO),
                Long.class, hoy, hoy);
    }

    @Override public List<FilaSol> credencialesSol(int pagina, int porPagina) {
        return jdbc.query("""
                SELECT t.id, t.ruc, t.razon_social, %s, count(*) AS afectados, max(d.updated_at) AS ultimo_fallo,
                       (array_agg(d.ultimo_error ORDER BY d.updated_at DESC))[1] AS ultimo_error
                FROM documento d JOIN tenant t ON t.id = d.tenant_id LEFT JOIN cuenta c ON c.id = t.cuenta_id
                WHERE %s AND %s AND %s
                GROUP BY t.id, c.id ORDER BY afectados DESC, t.ruc, t.id LIMIT ? OFFSET ?
                """.formatted(CUENTA, ENVIO_ATASCADO, FALLO_DE_SOL, EN_SERVICIO), (rs, i) -> new FilaSol(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"),
                destino(rs), rs.getLong("afectados"), rs.getTimestamp("ultimo_fallo").toInstant(), rs.getString("ultimo_error")), porPagina, (long) (pagina - 1) * porPagina);
    }

    @Override public long contarCredencialesSol() {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT d.tenant_id) FROM documento d JOIN tenant t ON t.id = d.tenant_id WHERE %s AND %s AND %s
                """.formatted(ENVIO_ATASCADO, FALLO_DE_SOL, EN_SERVICIO), Long.class);
    }

    @Override public Optional<Situacion> situacionDe(UUID empresaId, LocalDate hoy) {
        return jdbc.query("""
                SELECT t.id, t.ruc, t.razon_social, %s, %s AS estado,
                       CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta END AS vigente_hasta,
                       CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta - ?::date END AS dias,
                       (SELECT count(*) FROM documento d WHERE d.tenant_id = t.id AND %s AND %s) AS fallos
                FROM tenant t LEFT JOIN cuenta c ON c.id = t.cuenta_id WHERE t.id = ?
                """.formatted(CUENTA, JdbcEmpresasAdminRepository.ESTADO, ENVIO_ATASCADO, FALLO_DE_SOL), (rs, i) -> new Situacion(rs.getObject("id", UUID.class), rs.getString("ruc"),
                rs.getString("razon_social"), destino(rs), EstadoCertificado.valueOf(rs.getString("estado")), rs.getObject("vigente_hasta", LocalDate.class),
                rs.getObject("dias", Integer.class), rs.getLong("fallos")), hoy, hoy, hoy, empresaId).stream().findFirst();
    }

    @Override public Map<UUID, AvisoRegistrado> ultimosAvisos(Collection<UUID> empresas, MotivoDeAviso motivo) {
        if (empresas.isEmpty()) return Map.of();
        List<Object> args = new ArrayList<>();
        args.add(motivo.name());
        args.addAll(empresas);
        String marcas = String.join(", ", Collections.nCopies(empresas.size(), "?"));
        Map<UUID, AvisoRegistrado> r = new HashMap<>();
        for (AvisoRegistrado a : jdbc.query("""
                SELECT DISTINCT ON (tenant_id) id, tenant_id, cuenta_id, motivo, destinatario, enviado_en, enviado_por
                FROM aviso_a_cliente WHERE motivo = ? AND tenant_id IN (%s) ORDER BY tenant_id, enviado_en DESC
                """.formatted(marcas), (rs, i) -> aviso(rs), args.toArray()))
            r.put(a.empresaId(), a);
        return r;
    }

    @Override public Optional<AvisoRegistrado> reservar(AvisoRegistrado aviso, Instant desde) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("reservar debe ejecutarse dentro de una transacción (UnitOfWork)");
        // El candado vale hasta el fin de la transacción: quien llega después espera acá y, al entrar, ya ve la reserva del primero.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, aviso.empresaId() + ":" + aviso.motivo().name());
        List<AvisoRegistrado> previo = jdbc.query("""
                SELECT id, tenant_id, cuenta_id, motivo, destinatario, enviado_en, enviado_por FROM aviso_a_cliente
                WHERE tenant_id = ? AND motivo = ? AND enviado_en > ? ORDER BY enviado_en DESC LIMIT 1
                """, (rs, i) -> aviso(rs), aviso.empresaId(), aviso.motivo().name(), Timestamp.from(desde));
        if (!previo.isEmpty()) return Optional.of(previo.get(0));
        jdbc.update("INSERT INTO aviso_a_cliente (id, tenant_id, cuenta_id, motivo, destinatario, enviado_en, enviado_por) VALUES (?, ?, ?, ?, ?, ?, ?)",
                aviso.id(), aviso.empresaId(), aviso.cuentaId(), aviso.motivo().name(), aviso.destinatario(), Timestamp.from(aviso.enviadoEn()), aviso.enviadoPor());
        return Optional.empty();
    }

    @Override public void anular(UUID avisoId) { jdbc.update("DELETE FROM aviso_a_cliente WHERE id = ?", avisoId); }

    private static Destino destino(ResultSet rs) throws SQLException {
        return new Destino(rs.getObject("cuenta_id", UUID.class), rs.getString("cuenta_nombre"), rs.getString("cuenta_email"));
    }

    private static AvisoRegistrado aviso(ResultSet rs) throws SQLException {
        return new AvisoRegistrado(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("cuenta_id", UUID.class), MotivoDeAviso.valueOf(rs.getString("motivo")),
                rs.getString("destinatario"), rs.getTimestamp("enviado_en").toInstant(), rs.getObject("enviado_por", UUID.class));
    }
}
