package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase;

@Schema(name = "PropuestaRvie", description = "EXPERIMENTAL. El ticket con que SUNAT prepara la propuesta del RVIE de un periodo, y cómo va")
public record PropuestaRvieResponse(
        @Schema(example = "202610", description = "Periodo de la propuesta, `AAAAMM`") String periodo,
        @Schema(example = "202600000123", description = "Número de ticket de SUNAT, con el que se consulta y se concilia") String ticket,
        @Schema(example = "EN_PROCESO", description = "`EN_PROCESO` (SUNAT la prepara: vuelva a consultar), `TERMINADO` (lista para conciliar) o `CON_ERRORES` (SUNAT no pudo prepararla: pídala de nuevo)",
                allowableValues = {"EN_PROCESO", "TERMINADO", "CON_ERRORES"}) String estado,
        @Schema(example = "05", description = "Código de estado de envío de SUNAT (Anexo III del manual del servicio API del RVIE); vacío al pedir la propuesta") String codigoSunat,
        @Schema(example = "En proceso", description = "Descripción del estado de envío que da SUNAT; vacía al pedir la propuesta") String descripcionSunat,
        @Schema(example = "1", description = "Archivos que SUNAT preparó (un periodo grande llega partido); 0 mientras no termina") int archivos) {

    /** Recién pedida: SUNAT todavía no dice nada del ticket. */
    public static PropuestaRvieResponse de(RegistroDeVentasSireUseCase.PropuestaSolicitada p) {
        return new PropuestaRvieResponse(p.periodo().aaaamm(), p.ticket(), "EN_PROCESO", "", "", 0);
    }

    public static PropuestaRvieResponse de(RegistroDeVentasSireUseCase.EstadoDePropuesta e) {
        return new PropuestaRvieResponse(e.periodo().aaaamm(), e.ticket(), e.fase().name(), e.codigoSunat(), e.descripcionSunat(), e.archivos());
    }
}
