package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase.MiCuenta;
import pe.factura.domain.plan.Limite;

/** Lo que el cliente ve de su cuenta (C1, C7): su nombre, su plan con su estado de pago y lo que consumió este mes contra su tope. */
public record MiCuentaResponse(
        @Schema(example = "Ferretería Torres") String nombre,
        @Schema(description = "El plan de hoy, su estado de pago y una bajada programada, si la hay (la misma forma que ve el backoffice)") PlanDeCuentaResponse plan,
        ConsumoDelMesResponse consumo) {

    public static MiCuentaResponse de(MiCuenta m) {
        Limite tope = m.plan().plan().limites().documentosAlMes();
        return new MiCuentaResponse(m.nombre(), PlanDeCuentaResponse.de(m.plan()),
                new ConsumoDelMesResponse(m.consumo().mes().toString(), m.consumo().documentos(), tope.ilimitado() ? null : tope.maximo()));
    }

    public record ConsumoDelMesResponse(
            @Schema(example = "2026-10", description = "El mes calendario en curso (America/Lima)") String mes,
            @Schema(example = "12", description = "Lo que consumieron todas las empresas de la cuenta: lo que SUNAT aceptó, aunque después se anule") long documentos,
            @Schema(example = "300", description = "El tope de documentos al mes del plan de hoy; ausente si es ilimitado") Integer maximo) {}
}
