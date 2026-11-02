package pe.factura.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una cuenta para el backoffice (#181): quiénes entran, qué empresas tiene y qué hizo hace poco. Lectura pura, sin acciones
 * (llegan en sus propios issues) y sin secretos: del certificado y de la clave SOL solo se dice si existen y hasta cuándo vale el
 * certificado. Como el listado (#180), no se audita: la bitácora de #178 registra acciones, no consultas.
 */
public interface DetalleCuentaAdminUseCase {
    /** {@code NO_ENCONTRADO} si la cuenta no existe. */
    CuentaDetalle detalle(UUID cuentaId);

    /**
     * {@code suspendidaEn} es nulo si la cuenta está activa (#182); {@code bajaEn}, si no está dada de baja (#201). Una cuenta de baja se abre
     * igual, con todo lo suyo: sus comprobantes se conservan y siguen siendo consultables.
     */
    record CuentaDetalle(UUID id, String nombre, String email, String telefono, Instant creadaEn, Instant suspendidaEn, Instant bajaEn,
                         List<UsuarioDeCuenta> usuarios, List<EmpresaDeCuenta> empresas,
                         List<ComprobanteReciente> comprobantes, List<EventoReciente> eventos, List<EventoReciente> historialEstado) {}

    /** {@code ultimoAcceso}: su sesión más reciente en el portal; el uso por API key no cuenta. Nulo si nunca inició sesión. */
    record UsuarioDeCuenta(UUID id, String email, String rol, boolean activo, Instant correoVerificadoEn, Instant ultimoAcceso) {}

    /**
     * {@code certificadoVigenteHasta} es nulo si no hay certificado (o si el .p12 no informó vigencia); {@code tieneCertificado} y
     * {@code tieneCredencialesSol} dicen si están cargados, sin exponer nada de ellos.
     */
    record EmpresaDeCuenta(UUID id, String ruc, String razonSocial, String entorno, boolean tieneCertificado, LocalDate certificadoVigenteHasta,
                           boolean tieneCredencialesSol) {}

    /** Un comprobante de cualquiera de las empresas de la cuenta, con la empresa a la que pertenece. */
    record ComprobanteReciente(UUID id, UUID empresaId, String ruc, String tipo, String serie, long numero, LocalDate fechaEmision,
                               String estado, String moneda, BigDecimal total) {}

    /**
     * Una acción del administrador sobre esta cuenta, de la bitácora (#178). {@code administrador} es el correo de quien la hizo (H11), para saber a quién
     * preguntar; es nulo si fue la clave de la plataforma o si ese administrador ya no existe. Sin IP: es contexto para soporte, no la auditoría completa.
     * El {@code historialEstado} usa la misma forma con solo las suspensiones, reactivaciones, bajas y reposiciones, todas (H15).
     */
    record EventoReciente(String accion, String actor, String administrador, Instant ocurridoEn, String detalle) {}
}
