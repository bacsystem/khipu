package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.ComunicacionBaja;

import java.time.LocalDate;
import java.util.UUID;

/** Baja de un comprobante: identificador RA (o RC si es una boleta), estado del trámite ante SUNAT y, cuando existe, el CDR. */
public record BajaResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "RA-20260918-1", description = "Identificador ante SUNAT: `RA-yyyymmdd-N` (comunicación de baja) o `RC-yyyymmdd-N` (resumen diario, para una boleta). Correlativo por empresa y día") String identificador,
        @Schema(example = "F001-125", description = "Comprobante dado de baja") String comprobante,
        @Schema(example = "01", description = "Tipo del comprobante (01 factura, 03 boleta, 07/08 notas)") String tipoComprobante,
        @Schema(example = "2026-09-18") LocalDate fechaGeneracion,
        @Schema(example = "2026-09-15", description = "Fecha de emisión del comprobante tal como se comunicó a SUNAT (cbc:ReferenceDate, reglas 2375/2671)") LocalDate fechaReferencia,
        @Schema(example = "Error en el RUC del cliente") String motivo,
        @Schema(example = "BAJA", description = "`BAJA`: anula el comprobante. `ALTA`: informa en el resumen diario una boleta que pasó el envío individual (SUNAT ya no la recibe sola, 1079); al aceptarse, la boleta queda `ACEPTADO`, o `ACEPTADO_CON_OBS` si se informó fuera de plazo (`fuera_de_plazo`)") String condicion,
        @Schema(example = "false", description = "Solo en un `ALTA`: `true` si el resumen se generó pasado el 7.º día desde la emisión de la boleta. SUNAT no la rechaza: la acepta con la observación 4443 «El comprobante fue informado fuera del plazo establecido» y la boleta queda `ACEPTADO_CON_OBS`. SUNAT cuenta los 7 días hasta que recibe el resumen: si su envío falló y se reintentó otro día, puede observarlo con 4443 aunque aquí diga `false`; lo que manda es `cdr.observaciones` del comprobante. Una `BAJA` nunca sale fuera de plazo (2957)") boolean fueraDePlazo,
        @Schema(example = "ACEPTADA", description = "`GENERADA` (firmada, sin enviar) → `ENVIADA` (SUNAT devolvió ticket, en proceso) → `ACEPTADA` / `RECHAZADA`; `ERROR_ENVIO` cuando SUNAT no respondió y khipu reintentará; `DESCARTADA` cuando un resumen diario con gratuitas generado hasta el 2026-12-31 (código 05) no llegó a SUNAT antes del 2027-01-01, en que SUNAT desactivó ese código: khipu genera otro `RC` con la fecha y el formato del día (`ultimo_error` dice cuál) y sigue con ese. Si para entonces la baja ya pasó los 7 días, no se regenera: queda `RECHAZADA` con `cdr.codigo` 2957") String estado,
        @Schema(example = "1789768174685", description = "Ticket del proceso en SUNAT (sendSummary), o `null`") String ticket,
        @Schema(description = "Constancia de SUNAT: `0` aceptada, 2000–3999 rechazada") ComprobanteResponse.CdrDto cdr,
        @Schema(example = "1") int intentos,
        @Schema(example = "null") String ultimoError) {

    public static BajaResponse de(ComunicacionBaja b) {
        return new BajaResponse(b.id(), b.identificador(), b.serie() + "-" + b.numero(), b.tipoComprobante().codigo(), b.fechaGeneracion(), b.fechaReferencia(), b.motivo(), b.condicion().name(), b.fueraDePlazo(), b.estado().name(), b.ticket(),
                b.cdr() == null ? null : new ComprobanteResponse.CdrDto(b.cdr().codigo(), b.cdr().descripcion(), b.cdr().observaciones()), b.intentos(), b.ultimoError());
    }
}
