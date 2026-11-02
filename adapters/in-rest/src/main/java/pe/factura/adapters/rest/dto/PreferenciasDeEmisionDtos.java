package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase.PreferenciasDeEmision;

/** Las preferencias de emisión de la empresa en el portal. */
public final class PreferenciasDeEmisionDtos {
    private PreferenciasDeEmisionDtos() {}

    private static final String OPCIONES_AVANZADAS = "Si el modal de emisión del portal ofrece las opciones avanzadas: tipo de operación/exportación, anticipos, percepción,"
            + " retención, más opciones y referencias. Crédito, detracción y correo se ofrecen siempre. Solo cambia lo que muestra el portal; la API acepta los mismos campos";

    @Schema(name = "PreferenciasDeEmisionRequest")
    public record PreferenciasRequest(
            @NotNull @Schema(example = "true", description = OPCIONES_AVANZADAS) Boolean opcionesAvanzadas) {
        public PreferenciasDeEmision aDominio() { return new PreferenciasDeEmision(opcionesAvanzadas); }
    }

    @Schema(name = "PreferenciasDeEmision")
    public record PreferenciasResponse(
            @Schema(example = "false", description = OPCIONES_AVANZADAS) boolean opcionesAvanzadas) {
        public static PreferenciasResponse de(PreferenciasDeEmision p) { return new PreferenciasResponse(p.opcionesAvanzadas()); }
    }
}
