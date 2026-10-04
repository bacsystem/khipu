package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ConsumoCuentaResponse;
import pe.factura.adapters.rest.dto.ConsumoEmpresaResponse;
import pe.factura.application.port.in.ConsultarConsumoUseCase;

import java.util.UUID;

/**
 * Cuántos documentos consumió una cuenta o una empresa en un mes (#192). Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador.
 * Solo lectura.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminConsumoController {
    private final ConsultarConsumoUseCase consumo;

    @GetMapping("/cuentas/{id}/consumo")
    @Operation(summary = "Consumo mensual de una cuenta", description = """
            Los documentos que consumió la cuenta en un mes, con el detalle por empresa (cada una, aunque no haya emitido nada). **Solo cuentan los comprobantes
            que SUNAT aceptó** (`ACEPTADO` o `ACEPTADO_CON_OBS`, y también los que después se dieron de baja, `ANULADO`: lo aceptado ya consumió), por su fecha de
            emisión, dentro del mes calendario en America/Lima. No cuentan los rechazados, los errores de envío, los fuera de plazo ni los que están en camino; la
            comunicación de baja no suma otro documento, y un comprobante reintentado hasta que lo aceptan cuenta una sola vez.
            `mes` es `AAAA-MM`; sin él, el mes en curso (`400 PARAMETRO_INVALIDO` si está mal escrito). `404 NO_ENCONTRADO` si la cuenta no existe.""")
    public ApiResponse<ConsumoCuentaResponse> deCuenta(@Parameter(description = "Id de la cuenta") @PathVariable UUID id,
                                                       @Parameter(description = "Mes, `AAAA-MM`; por defecto el mes en curso", example = "2026-10") @RequestParam(required = false) String mes) {
        return ApiResponse.ok(ConsumoCuentaResponse.de(consumo.deCuenta(id, ParametroMes.de(mes))));
    }

    @GetMapping("/empresas/{id}/consumo")
    @Operation(summary = "Consumo mensual de una empresa", description = """
            Los documentos que consumió la empresa en un mes, con la misma definición que el consumo de la cuenta: solo comprobantes aceptados por SUNAT, por fecha de
            emisión, en el mes calendario de Lima. `mes` es `AAAA-MM`; sin él, el mes en curso. `404 NO_ENCONTRADO` si la empresa no existe.""")
    public ApiResponse<ConsumoEmpresaResponse> deEmpresa(@Parameter(description = "Id de la empresa") @PathVariable UUID id,
                                                         @Parameter(description = "Mes, `AAAA-MM`; por defecto el mes en curso", example = "2026-10") @RequestParam(required = false) String mes) {
        return ApiResponse.ok(ConsumoEmpresaResponse.de(consumo.deEmpresa(id, ParametroMes.de(mes))));
    }
}
