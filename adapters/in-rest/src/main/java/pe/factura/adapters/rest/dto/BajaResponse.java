package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.ComunicacionBaja;

import java.time.LocalDate;
import java.util.UUID;

/** Comunicación de baja de un comprobante: identificador RA, estado del trámite ante SUNAT y, cuando existe, el CDR. */
public record BajaResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "RA-20260918-1", description = "Identificador de la comunicación (uno por empresa y día, correlativo)") String identificador,
        @Schema(example = "F001-125", description = "Comprobante dado de baja") String comprobante,
        @Schema(example = "01", description = "Tipo del comprobante (01 factura, 07/08 notas)") String tipoComprobante,
        @Schema(example = "2026-09-18") LocalDate fechaGeneracion,
        @Schema(example = "2026-09-15", description = "Fecha de emisión del comprobante tal como se comunicó a SUNAT (cbc:ReferenceDate, reglas 2375/2671)") LocalDate fechaReferencia,
        @Schema(example = "Error en el RUC del cliente") String motivo,
        @Schema(example = "ACEPTADA", description = "`GENERADA` (firmada, sin enviar) → `ENVIADA` (SUNAT devolvió ticket, en proceso) → `ACEPTADA` / `RECHAZADA`; `ERROR_ENVIO` cuando SUNAT no respondió y khipu reintentará") String estado,
        @Schema(example = "1789768174685", description = "Ticket del proceso en SUNAT (sendSummary), o `null`") String ticket,
        @Schema(description = "Constancia de SUNAT: `0` aceptada, 2000–3999 rechazada") ComprobanteResponse.CdrDto cdr,
        @Schema(example = "1") int intentos,
        @Schema(example = "null") String ultimoError) {

    public static BajaResponse de(ComunicacionBaja b) {
        return new BajaResponse(b.id(), b.identificador(), b.serie() + "-" + b.numero(), b.tipoComprobante().codigo(), b.fechaGeneracion(), b.fechaReferencia(), b.motivo(), b.estado().name(), b.ticket(),
                b.cdr() == null ? null : new ComprobanteResponse.CdrDto(b.cdr().codigo(), b.cdr().descripcion(), b.cdr().observaciones()), b.intentos(), b.ultimoError());
    }
}
