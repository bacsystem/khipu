package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Resumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** El resumen de los comprobantes de la empresa en un período (#15), para las métricas del portal. */
public record ResumenDeComprobantesResponse(
        @Schema(example = "2026-09-01", description = "El `desde` pedido; ausente si el rango estaba abierto de ese lado") LocalDate desde,
        @Schema(example = "2026-09-30", description = "El `hasta` pedido; ausente si el rango estaba abierto de ese lado") LocalDate hasta,
        @Schema(example = "120", description = "Comprobantes firmados del período, en cualquier estado (rechazados y dados de baja incluidos)") long emitidos,
        @Schema(example = "100", description = "Aceptados por SUNAT, con o sin observaciones: los que ya tienen su CDR") long aceptadosConCdr,
        @Schema(example = "8", description = "En camino a SUNAT, sin nada que corregir: firmados que aún no se envían, enviados que esperan su CDR y boletas que esperan su resumen diario."
                + " Las boletas sin enviar desde el 5.º día cuentan acá y también en `atencion_requerida.boletas_sin_enviar`") long enCamino,
        @Schema(description = "Lo que pide que el emisor haga algo: rechazados, con error de envío, fuera de plazo y boletas sin enviar") AtencionRequerida atencionRequerida,
        @Schema(description = "Lo facturado por moneda, ordenado por moneda; vacío si no hay nada. Cuentan los aceptados y los que están en camino; no los rechazados, fuera de plazo ni dados de baja") List<Facturado> facturado) {

    public record AtencionRequerida(
            @Schema(example = "9", description = "La suma de los cuatro") long total,
            @Schema(example = "3", description = "Rechazados por SUNAT: hay que corregir y reemitir") long rechazados,
            @Schema(example = "2", description = "Con error de envío: se reintentan solos o con `POST /v1/facturas/{id}/enviar`") long erroresDeEnvio,
            @Schema(example = "2", description = "No llegaron a SUNAT dentro del plazo: hay que emitir de nuevo") long fueraDePlazo,
            @Schema(example = "2", description = "Boletas (o notas que las modifican) que quedaron `FIRMADAS` porque se emitieron con `enviar_automatico: false` y ya están en el 5.º día o más:"
                    + " nadie las ha enviado y el resumen diario cierra el 7.º. Se envían con `POST /v1/facturas/{id}/enviar`") long boletasSinEnviar) {}

    public record Facturado(
            @Schema(example = "PEN") String moneda,
            @Schema(example = "12345.67", description = "Facturas, boletas y notas de débito suman; las notas de crédito restan. Negativo solo si las notas superan lo emitido") BigDecimal total) {}

    public static ResumenDeComprobantesResponse de(Resumen r) {
        return new ResumenDeComprobantesResponse(r.desde(), r.hasta(), r.emitidos(), r.aceptadosConCdr(), r.enCamino(),
                new AtencionRequerida(r.atencionRequerida(), r.rechazados(), r.erroresDeEnvio(), r.fueraDePlazo(), r.boletasSinEnviar()),
                r.facturado().stream().map(t -> new Facturado(t.moneda(), t.total())).toList());
    }
}
