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
        @Schema(description = "Lo que pide que el emisor haga algo: rechazados, con error de envío y fuera de plazo") AtencionRequerida atencionRequerida,
        @Schema(description = "Lo facturado por moneda, ordenado por moneda; vacío si no hay nada. Cuentan los aceptados y los que están en camino; no los rechazados, fuera de plazo ni dados de baja") List<Facturado> facturado) {

    public record AtencionRequerida(
            @Schema(example = "7", description = "La suma de los tres") long total,
            @Schema(example = "3", description = "Rechazados por SUNAT: hay que corregir y reemitir") long rechazados,
            @Schema(example = "2", description = "Con error de envío: se reintentan solos o con `POST /v1/facturas/{id}/enviar`") long erroresDeEnvio,
            @Schema(example = "2", description = "No llegaron a SUNAT dentro del plazo: hay que emitir de nuevo") long fueraDePlazo) {}

    public record Facturado(
            @Schema(example = "PEN") String moneda,
            @Schema(example = "12345.67", description = "Facturas, boletas y notas de débito suman; las notas de crédito restan. Negativo solo si las notas superan lo emitido") BigDecimal total) {}

    public static ResumenDeComprobantesResponse de(Resumen r) {
        return new ResumenDeComprobantesResponse(r.desde(), r.hasta(), r.emitidos(), r.aceptadosConCdr(),
                new AtencionRequerida(r.atencionRequerida(), r.rechazados(), r.erroresDeEnvio(), r.fueraDePlazo()),
                r.facturado().stream().map(t -> new Facturado(t.moneda(), t.total())).toList());
    }
}
