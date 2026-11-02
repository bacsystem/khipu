package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AvisarAlClienteUseCase.AvisoEnviado;
import pe.factura.application.port.in.ConsultarAvisosUseCase.CertificadoEnRiesgo;
import pe.factura.application.port.in.ConsultarAvisosUseCase.CuentaDelCliente;
import pe.factura.application.port.in.ConsultarAvisosUseCase.SolFallando;
import pe.factura.application.port.in.ConsultarAvisosUseCase.UltimoAviso;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Las respuestas de los avisos a los clientes del backoffice (#197). */
public final class AvisosResponse {
    private AvisosResponse() {}

    /** A quién le llegaría el aviso: la cuenta dueña de la empresa. */
    public record Cuenta(UUID id, @Schema(example = "Ana Pérez") String nombre, @Schema(example = "ana@negocio.pe") String email) {
        static Cuenta de(CuentaDelCliente c) { return c == null ? null : new Cuenta(c.id(), c.nombre(), c.email()); }
    }

    /** El último aviso de este motivo a esta empresa. */
    public record Ultimo(@Schema(example = "2026-10-10T15:00:00Z") Instant enviadoEn, @Schema(example = "ana@negocio.pe") String destinatario) {
        static Ultimo de(UltimoAviso u) { return u == null ? null : new Ultimo(u.enviadoEn(), u.destinatario()); }
    }

    public record Certificado(
            UUID empresaId,
            @Schema(example = "20100066603") String ruc,
            @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
            @Schema(description = "La cuenta a la que se le avisaría; ausente en una empresa de integración, que no tiene cuenta") Cuenta cuenta,
            @Schema(example = "CERTIFICADO_POR_VENCER", allowableValues = {"CERTIFICADO_POR_VENCER", "CERTIFICADO_VENCIDO"}) String motivo,
            @Schema(example = "2026-10-25", description = "El último día que vale el certificado") LocalDate vigenteHasta,
            @Schema(example = "10", description = "Días que le quedan; negativo si ya venció") int diasRestantes,
            @Schema(description = "El último aviso de este motivo; ausente si nunca se avisó") Ultimo ultimoAviso,
            @Schema(example = "2026-10-17T15:00:00Z", description = "Desde cuándo se puede repetir el aviso; ausente si nunca se avisó o la espera ya pasó") Instant avisarDesde,
            @Schema(description = "Hay a quién avisarle y no se avisó lo mismo en la última semana") boolean puedeAvisar) {
        public static Certificado de(CertificadoEnRiesgo c) {
            return new Certificado(c.empresaId(), c.ruc(), c.razonSocial(), Cuenta.de(c.cuenta()), c.motivo().name(), c.vigenteHasta(), c.diasRestantes(), Ultimo.de(c.ultimoAviso()), c.avisarDesde(),
                    c.puedeAvisar());
        }
    }

    public record Sol(
            UUID empresaId,
            @Schema(example = "20100066603") String ruc,
            @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
            Cuenta cuenta,
            @Schema(example = "4", description = "Cuántos comprobantes están en error de envío por las credenciales ahora mismo") long comprobantesAfectados,
            @Schema(example = "2026-10-15T14:50:00Z", description = "Cuándo falló el último") Instant ultimoFallo,
            @Schema(example = "0102 - Usuario o contraseña incorrectos", description = "Lo que dijo SUNAT en el último fallo") String ultimoError,
            Ultimo ultimoAviso,
            Instant avisarDesde,
            boolean puedeAvisar) {
        public static Sol de(SolFallando s) {
            return new Sol(s.empresaId(), s.ruc(), s.razonSocial(), Cuenta.de(s.cuenta()), s.comprobantesAfectados(), s.ultimoFallo(), s.ultimoError(), Ultimo.de(s.ultimoAviso()), s.avisarDesde(),
                    s.puedeAvisar());
        }
    }

    public record Enviado(
            UUID empresaId,
            @Schema(example = "CERTIFICADO_POR_VENCER", allowableValues = {"CERTIFICADO_POR_VENCER", "CERTIFICADO_VENCIDO", "CREDENCIALES_SOL_INVALIDAS"}) String motivo,
            @Schema(example = "ana@negocio.pe", description = "A quién se le escribió") String destinatario,
            Instant enviadoEn,
            @Schema(description = "Desde cuándo se puede repetir este aviso") Instant avisarDesde) {
        public static Enviado de(AvisoEnviado a) { return new Enviado(a.empresaId(), a.motivo().name(), a.destinatario(), a.enviadoEn(), a.avisarDesde()); }
    }
}
