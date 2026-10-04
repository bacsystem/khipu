package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.CuentaDetalle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalle de una cuenta para el backoffice (#181). Solo lectura y sin secretos. */
public record CuentaDetalleResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "Mi negocio") String nombre,
        @Schema(example = "ana@negocio.pe") String email,
        @Schema(example = "987654321", description = "Celular de contacto; ausente en cuentas anteriores a ese campo") String telefono,
        @Schema(example = "2026-09-01T10:00:00Z", description = "Fecha de alta") Instant creadaEn,
        List<UsuarioResponse> usuarios,
        List<EmpresaResponse> empresas,
        @Schema(description = "Los 10 comprobantes de fecha de emisión más reciente entre todas las empresas de la cuenta") List<ComprobanteResponse> comprobantes,
        @Schema(description = "Las últimas 10 acciones del administrador sobre esta cuenta (bitácora)") List<EventoResponse> eventos) {

    public record UsuarioResponse(
            UUID id,
            @Schema(example = "ana@negocio.pe") String email,
            @Schema(example = "ADMIN", description = "ADMIN, EMISOR o LECTURA") String rol,
            boolean activo,
            @Schema(description = "Cuándo verificó su correo (#22); ausente si todavía no") Instant correoVerificadoEn,
            @Schema(description = "Su sesión más reciente en el portal; el uso por API key no cuenta. Ausente si nunca inició sesión") Instant ultimoAcceso) {}

    public record EmpresaResponse(
            UUID id,
            @Schema(example = "20100066603") String ruc,
            @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
            @Schema(example = "PRODUCCION", description = "BETA o PRODUCCION") String entorno,
            @Schema(description = "Si hay un certificado digital cargado; nunca se expone su contenido ni su clave") boolean tieneCertificado,
            @Schema(example = "2027-03-01", description = "Hasta cuándo vale el certificado; ausente si no hay certificado") LocalDate certificadoVigenteHasta,
            @Schema(description = "Si están cargados el usuario y la clave SOL; nunca se exponen") boolean tieneCredencialesSol) {}

    public record ComprobanteResponse(
            UUID id,
            UUID empresaId,
            @Schema(example = "20100066603", description = "RUC de la empresa de la cuenta a la que pertenece") String ruc,
            @Schema(example = "01", description = "Tipo de comprobante (catálogo 01)") String tipo,
            @Schema(example = "F001") String serie,
            @Schema(example = "15") long numero,
            @Schema(example = "2026-09-30") LocalDate fechaEmision,
            @Schema(example = "ACEPTADO") String estado,
            @Schema(example = "PEN") String moneda,
            @Schema(example = "118.00") BigDecimal total) {}

    public record EventoResponse(
            @Schema(example = "CREAR_CUENTA") String accion,
            @Schema(example = "ADMINISTRADOR", description = "ADMINISTRADOR o CLAVE_PLATAFORMA") String actor,
            @Schema(example = "2026-09-01T10:00:05Z") Instant ocurridoEn,
            @Schema(example = "ruc=20100066603 serie=F001", description = "Contexto de la acción; nunca lleva secretos") String detalle) {}

    public static CuentaDetalleResponse de(CuentaDetalle d) {
        return new CuentaDetalleResponse(d.id(), d.nombre(), d.email(), d.telefono(), d.creadaEn(),
                d.usuarios().stream().map(u -> new UsuarioResponse(u.id(), u.email(), u.rol(), u.activo(), u.correoVerificadoEn(), u.ultimoAcceso())).toList(),
                d.empresas().stream().map(e -> new EmpresaResponse(e.id(), e.ruc(), e.razonSocial(), e.entorno(), e.tieneCertificado(), e.certificadoVigenteHasta(),
                        e.tieneCredencialesSol())).toList(),
                d.comprobantes().stream().map(c -> new ComprobanteResponse(c.id(), c.empresaId(), c.ruc(), c.tipo(), c.serie(), c.numero(), c.fechaEmision(), c.estado(),
                        c.moneda(), c.total())).toList(),
                d.eventos().stream().map(e -> new EventoResponse(e.accion(), e.actor(), e.ocurridoEn(), e.detalle())).toList());
    }
}
