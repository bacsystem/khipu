package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.ErrorDeEmision;
import pe.factura.domain.documento.FaultSunat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un comprobante de la cola global de errores del backoffice (#196), con su empresa, el fault de SUNAT y los intentos que lleva. */
public record ErrorDeEmisionResponse(
        UUID comprobanteId,
        @Schema(description = "La empresa dueña del comprobante") UUID empresaId,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(description = "La cuenta dueña de la empresa; ausente en una empresa dada de alta por una integración") UUID cuentaId,
        @Schema(example = "Ana Pérez", description = "El nombre de esa cuenta; ausente si no hay cuenta") String cuentaNombre,
        @Schema(example = "20100066603-01-F001-7", description = "RUC-tipo-serie-número: la identidad del comprobante ante SUNAT") String nombreArchivo,
        @Schema(example = "01") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "7") long numero,
        @Schema(example = "2026-10-12") LocalDate fechaEmision,
        @Schema(example = "ERROR_ENVIO", description = "El estado del comprobante: `ERROR_ENVIO`, `FUERA_DE_PLAZO` o `RECHAZADO` (con un fault de formato)") String estado,
        @Schema(example = "ERROR_DE_ENVIO", allowableValues = {"ERROR_DE_ENVIO", "ERROR_DE_FORMATO", "FUERA_DE_PLAZO"},
                description = "Qué tipo de problema es. `ERROR_DE_FORMATO` es un rechazo por fault de SUNAT 1000–1999: terminal, no se reintenta") String clase,
        @Schema(example = "3", description = "Cuántas veces falló el envío") int intentos,
        @Schema(description = "El fault de SUNAT; ausente si no hay nada que mostrar") Fault fault,
        @Schema(example = "2026-10-15T18:30:00Z", description = "Cuándo lo reintentará el trabajo del outbox; ausente si no hay un reintento programado") Instant proximoIntento,
        @Schema(example = "2026-10-15T15:00:00Z", description = "Cuándo cambió por última vez") Instant actualizadoEn,
        @Schema(description = "Si un administrador puede reintentarlo o descartarlo: solo un error de envío; las otras dos clases ya son terminales") boolean accionable) {

    public record Fault(
            @Schema(example = "0109", description = "El código de SUNAT; ausente en un fallo propio (`INFRA - …`) o un texto sin código") String codigo,
            @Schema(example = "El sistema no puede responder su solicitud") String mensaje) {
        static Fault de(FaultSunat f) { return f == null ? null : new Fault(f.codigo(), f.mensaje()); }
    }

    public static ErrorDeEmisionResponse de(ErrorDeEmision e) {
        return new ErrorDeEmisionResponse(e.comprobanteId(), e.empresaId(), e.ruc(), e.razonSocial(), e.cuentaId(), e.cuentaNombre(), e.nombreArchivo(), e.tipo(), e.serie(), e.numero(),
                e.fechaEmision(), e.estado().name(), e.clase().name(), e.intentos(), Fault.de(e.fault()), e.proximoIntento(), e.actualizadoEn(), e.accionable());
    }
}
