package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ResumirComprobantesUseCase.PorDia;

import java.time.LocalDate;
import java.util.List;

/** La emisión de la empresa día por día en un rango (#24), para el gráfico del dashboard del portal. Mismos criterios que el resumen (#15). */
public record EmisionPorDiaResponse(
        @Schema(example = "2026-10-01", description = "El `desde` pedido") LocalDate desde,
        @Schema(example = "2026-10-31", description = "El `hasta` pedido") LocalDate hasta,
        @Schema(description = "Solo los días con algo emitido o facturado, ordenados por fecha; un día sin comprobantes no viene. Vacío si no hay nada en el rango") List<DiaDeEmision> dias) {

    public record DiaDeEmision(
            @Schema(example = "2026-10-05", description = "Fecha de emisión") LocalDate fecha,
            @Schema(example = "12", description = "Comprobantes firmados ese día, en cualquier estado, como `emitidos` del resumen") long emitidos,
            @Schema(description = "Lo facturado ese día por moneda, ordenado por moneda, como `facturado` del resumen; vacío si no hay nada") List<ResumenDeComprobantesResponse.Facturado> facturado) {}

    public static EmisionPorDiaResponse de(PorDia p) {
        return new EmisionPorDiaResponse(p.desde(), p.hasta(), p.dias().stream()
                .map(d -> new DiaDeEmision(d.fecha(), d.emitidos(), d.facturado().stream().map(t -> new ResumenDeComprobantesResponse.Facturado(t.moneda(), t.total())).toList()))
                .toList());
    }
}
