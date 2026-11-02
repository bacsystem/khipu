package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase;
import pe.factura.domain.sire.ConciliacionRvie;

import java.util.List;

@Schema(name = "ConciliacionRvie", description = "EXPERIMENTAL. Resultado de conciliar el registro de ventas de khipu con la propuesta del RVIE de SUNAT, comprobante por comprobante (tipo-serie-número)")
public record ConciliacionRvieResponse(
        @Schema(example = "202610", description = "Periodo conciliado, `AAAAMM`") String periodo,
        @Schema(example = "true", description = "`true` si nada falta, nada sobra y nada difiere") boolean cuadra,
        @Schema(example = "120", description = "Comprobantes de khipu en el registro (aceptados y anulados por SUNAT)") long registrosKhipu,
        @Schema(example = "121", description = "Filas leídas de la propuesta de SUNAT") long registrosSunat,
        @Schema(example = "118", description = "Comprobantes que están en los dos con los mismos datos") long coinciden,
        @Schema(example = "1", description = "Comprobantes que khipu todavía no tiene resueltos por SUNAT (firmados, enviados o con error de envío): no se concilian y pueden explicar parte de `solo_en_sunat`")
        long pendientesDeSunat,
        @Schema(example = "0", description = "Filas de la propuesta con el campo 10 (un rango de boletas o tickets del día consolidados): no se concilian una por una, y sus boletas pueden aparecer en `solo_en_khipu`")
        long filasConsolidadasNoConciliadas,
        @Schema(example = "1", description = "Cuántos están en la propuesta de SUNAT y no en el registro de khipu") long totalSoloEnSunat,
        @Schema(example = "0", description = "Cuántos están en khipu y no en la propuesta de SUNAT") long totalSoloEnKhipu,
        @Schema(example = "1", description = "Cuántos comprobantes están en los dos con algún dato distinto") long totalDifieren,
        @Schema(example = "1", description = "Cuántos datos distintos hay en total (un comprobante puede diferir en varios)") long totalDiferencias,
        @Schema(description = "Los que están solo en SUNAT, `tipo-serie-número` (hasta 500)", example = "[\"01-F001-130\"]") List<String> soloEnSunat,
        @Schema(description = "Los que están solo en khipu, `tipo-serie-número` (hasta 500)") List<String> soloEnKhipu,
        @Schema(description = "Cada dato distinto (hasta 500)") List<DiferenciaRvie> difieren) {

    @Schema(name = "DiferenciaRvie", description = "Un dato que khipu y SUNAT registran distinto para el mismo comprobante")
    public record DiferenciaRvie(
            @Schema(example = "01-F001-125", description = "`tipo-serie-número`") String comprobante,
            @Schema(example = "total", description = "`fecha_emision`, `moneda`, `estado`, `base_gravada`, `igv`, `exonerado`, `inafecto` o `total`") String campo,
            @Schema(example = "118.00", description = "Lo que dice khipu, como en el archivo") String khipu,
            @Schema(example = "120.00", description = "Lo que dice la propuesta de SUNAT") String sunat) {}

    public static ConciliacionRvieResponse de(RegistroDeVentasSireUseCase.Conciliacion c) {
        ConciliacionRvie.Resultado r = c.resultado();
        return new ConciliacionRvieResponse(c.periodo().aaaamm(), r.cuadra(), r.registrosKhipu(), r.registrosSunat(), r.coinciden(), c.pendientesDeSunat(), c.filasConsolidadas(),
                r.totalSoloEnSunat(), r.totalSoloEnKhipu(), r.totalDifieren(), r.totalDiferencias(), r.soloEnSunat(), r.soloEnKhipu(),
                r.difieren().stream().map(d -> new DiferenciaRvie(d.comprobante(), d.campo(), d.khipu(), d.sunat())).toList());
    }
}
