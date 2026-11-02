package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;

import java.util.List;
import java.util.UUID;

/** Lo que consumió una cuenta en un mes (#192): el total y el detalle por empresa. */
public record ConsumoCuentaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID cuentaId,
        @Schema(example = "2026-10", description = "El mes calendario (America/Lima) al que corresponde") String mes,
        @Schema(example = "450", description = "La suma de lo que consumió cada una de sus empresas") long documentos,
        @Schema(description = "Cada empresa de la cuenta, por RUC, aunque no haya emitido nada ese mes") List<ConsumoEmpresaResponse> empresas) {
    public static ConsumoCuentaResponse de(ConsumoDeCuenta c) {
        return new ConsumoCuentaResponse(c.cuentaId(), c.mes().toString(), c.documentos(), c.empresas().stream().map(ConsumoEmpresaResponse::de).toList());
    }
}
