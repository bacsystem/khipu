package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.BajaDeFicha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.Ficha;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La ficha de un comprobante en el backoffice (#251). Sin el contenido de sus archivos: solo si están guardados. */
public record FichaDeComprobanteResponse(
        UUID id,
        @Schema(description = "La empresa dueña del comprobante") UUID empresaId,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(description = "La cuenta dueña de la empresa; ausente en una empresa dada de alta por una integración") UUID cuentaId,
        @Schema(example = "20100066603-01-F001-7", description = "RUC-tipo-serie-número: la identidad del comprobante ante SUNAT") String nombreArchivo,
        @Schema(example = "01") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "7") Long numero,
        @Schema(example = "2026-10-12") LocalDate fechaEmision,
        @Schema(example = "ERROR_ENVIO") String estado,
        @Schema(example = "3", description = "Cuántas veces falló el envío") int intentos,
        @Schema(example = "0109 - El sistema no puede responder", description = "El último fallo de envío; ausente si no hubo") String ultimoError,
        @Schema(description = "Lo que respondió SUNAT en el CDR; ausente si todavía no respondió") RespuestaSunat respuestaSunat,
        @Schema(description = "Si el XML firmado está guardado") boolean tieneXml,
        @Schema(description = "Si el CDR de SUNAT está guardado") boolean tieneCdr,
        @Schema(description = "Sus comunicaciones de baja (`RA-…`) y resúmenes diarios (`RC-…`: la baja o el alta de una boleta o de una nota de boleta), de la más reciente a la más antigua; vacía si no tiene. Solo lectura") List<BajaDto> bajas) {

    public record RespuestaSunat(@Schema(example = "0") String codigo, @Schema(example = "La Factura numero F001-7, ha sido aceptada") String descripcion) {}

    /**
     * La baja o el resumen diario de la ficha (#362). El {@code motivo} es el que se pidió al anular; en una boleta o una nota de boleta el RC no lo lleva (queda solo en khipu).
     * Lo que SUNAT no ha dado (ticket, CDR, último error) va ausente.
     */
    public record BajaDto(
            UUID id,
            @Schema(example = "RC-20261013-2", description = "`RA-yyyymmdd-N` (comunicación de baja) o `RC-yyyymmdd-N` (resumen diario)") String identificador,
            @Schema(example = "BAJA", description = "`BAJA` anula el comprobante; `ALTA` informa en el resumen diario una boleta que pasó el envío individual") String condicion,
            @Schema(example = "ACEPTADA", description = "`GENERADA` → `ENVIADA` → `ACEPTADA` / `RECHAZADA`; `ERROR_ENVIO` (se reintenta)") String estado,
            @Schema(example = "1789768174685", description = "Ticket del proceso en SUNAT; ausente si todavía no lo dio") String ticket,
            @Schema(example = "Se cobró dos veces") String motivo,
            @Schema(example = "2026-10-13", description = "Fecha de generación del RA o RC (la del identificador)") LocalDate fechaGeneracion,
            @Schema(example = "2026-10-12", description = "Fecha de emisión del comprobante tal como se comunicó a SUNAT") LocalDate fechaReferencia,
            @Schema(description = "Solo en un `ALTA`: se generó pasado el 7.º día desde la emisión (previsión de khipu; la que cuenta es `cdr` del comprobante)") boolean fueraDePlazo,
            @Schema(description = "Lo que respondió SUNAT; ausente si todavía no respondió") RespuestaSunat cdr,
            @Schema(example = "0", description = "Cuántas veces falló el envío o la consulta del ticket") int intentos,
            @Schema(description = "El último fallo; ausente si no hubo") String ultimoError) {

        static BajaDto de(BajaDeFicha b) {
            return new BajaDto(b.id(), b.identificador(), b.condicion(), b.estado(), b.ticket(), b.motivo(), b.fechaGeneracion(), b.fechaReferencia(), b.fueraDePlazo(),
                    b.cdr() == null ? null : new RespuestaSunat(b.cdr().codigo(), b.cdr().descripcion()), b.intentos(), b.ultimoError());
        }
    }

    public static FichaDeComprobanteResponse de(Ficha f) {
        return new FichaDeComprobanteResponse(f.id(), f.empresaId(), f.ruc(), f.razonSocial(), f.cuentaId(), f.nombreArchivo(), f.tipo(), f.serie(), f.numero(), f.fechaEmision(),
                f.estado().name(), f.intentos(), f.ultimoError(), f.cdr() == null ? null : new RespuestaSunat(f.cdr().codigo(), f.cdr().descripcion()), f.tieneXml(), f.tieneCdr(),
                f.bajas().stream().map(BajaDto::de).toList());
    }
}
