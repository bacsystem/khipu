package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeEmpresa;

import java.util.UUID;

/** Lo que consumió una empresa en un mes (#192): solo los comprobantes que SUNAT aceptó. */
public record ConsumoEmpresaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID empresaId,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(example = "2026-10", description = "El mes calendario (America/Lima) al que corresponde") String mes,
        @Schema(example = "312", description = "Comprobantes aceptados por SUNAT (con o sin observaciones) con fecha de emisión en el mes") long documentos) {
    public static ConsumoEmpresaResponse de(ConsumoDeEmpresa c) {
        return new ConsumoEmpresaResponse(c.tenantId(), c.ruc(), c.razonSocial(), c.mes().toString(), c.documentos());
    }
}
