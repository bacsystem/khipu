package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.PadronRucResponse;
import pe.factura.adapters.rest.dto.ValidezResponse;
import pe.factura.application.port.in.ConsultarPadronRucUseCase;
import pe.factura.application.port.in.ConsultarValidezUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/v1")
@Tag(name = "Consultas", description = "Consultas a SUNAT. La validez de un comprobante usa la API REST de SUNAT si la empresa cargó sus credenciales API SUNAT (funciona también en beta), o `billValidService` con la clave SOL, que solo existe en producción (`422 NO_DISPONIBLE_EN_BETA`). La de un RUC lee el padrón reducido que la plataforma carga cada día y funciona también en beta.")
@RequiredArgsConstructor
public class ConsultaController {
    private final ConsultarValidezUseCase validez;
    private final ConsultarPadronRucUseCase padron;

    @GetMapping("/consultas/ruc/{ruc}")
    @Operation(operationId = "consultarRuc", summary = "Consultar un RUC en el padrón de SUNAT", description = """
            Razón social, estado, condición de domicilio y domicilio fiscal de un contribuyente, del padrón reducido que SUNAT publica cada día (no consulta a SUNAT en cada llamada).
            `actualizado_al` dice de qué carga viene la respuesta: un RUC creado después no aparece, así que `404 NO_ENCONTRADO` no prueba que no exista. `400 PARAMETRO_INVALIDO` si
            no es un RUC (11 dígitos, prefijo y dígito verificador), `503 PADRON_NO_CARGADO` si el padrón todavía no se cargó y `429 DEMASIADAS_CONSULTAS` al pasar el cupo
            (60 por minuto y 5 000 por día, por empresa). No requiere credenciales SOL.""")
    public ApiResponse<PadronRucResponse> ruc(HttpServletRequest req, @Parameter(example = "20100066603") @PathVariable String ruc) {
        return ApiResponse.ok(PadronRucResponse.de(padron.consultar(TenantActual.id(req), ruc)));
    }

    @GetMapping("/consultas/validez")
    @Operation(summary = "Consultar la validez de un comprobante", description = """
            Estado de un comprobante de cualquier emisor —el propio o un proveedor— por sus datos. Con credenciales API SUNAT
            (`PUT /v1/empresa/credenciales-api-sunat`) se consulta la API REST de SUNAT («consulta integrada», `fuente: API_REST`):
            `estado` `NO_EXISTE` (0), `ACEPTADO` (1), `DE_BAJA` (2, anulado), `AUTORIZADO` (3) o `NO_AUTORIZADO` (4, comprobantes
            físicos), más `estado_ruc`, `condicion_domicilio` y `observaciones` (p. ej. si la fecha o el monto no coinciden); ahí
            `fecha` es obligatoria y `monto` lo es para comprobantes electrónicos. Sin ellas, o si la API no responde y hay clave SOL,
            se usa `billValidService` (`fuente: SOAP`, solo producción): `ACEPTADO` (0001), `RECHAZADO` (0002), `DE_BAJA` (0003),
            `NO_EXISTE` (0011), `AJENO` (0012) o `ERROR_CONSULTA`; `aviso` dice por qué. Errores: `422 CREDENCIALES_SUNAT_NO_CARGADAS`,
            `422 CREDENCIALES_API_SUNAT_RECHAZADAS`, `422 CREDENCIALES_SOL_RECHAZADAS`, `422 CONSULTA_RECHAZADA` (SUNAT rechazó los
            datos de la consulta), `503 SUNAT_NO_DISPONIBLE`.""")
    public ApiResponse<ValidezResponse> validez(HttpServletRequest req,
                                                @Parameter(example = "20100066603", description = "RUC del emisor del comprobante") @RequestParam String ruc,
                                                @Parameter(example = "01", description = "01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito") @RequestParam String tipo,
                                                @Parameter(example = "F001") @RequestParam String serie,
                                                @Parameter(example = "125") @RequestParam long numero,
                                                @Parameter(example = "6", description = "Tipo de documento del receptor (catálogo 06); opcional") @RequestParam(name = "tipo_doc_receptor", required = false) String tipoDocReceptor,
                                                @Parameter(example = "20601234565", description = "Número de documento del receptor; opcional") @RequestParam(name = "num_doc_receptor", required = false) String numDocReceptor,
                                                @Parameter(example = "2026-09-14", description = "Fecha de emisión (`YYYY-MM-DD`); obligatoria por la API REST, opcional por SOAP") @RequestParam(required = false) LocalDate fecha,
                                                @Parameter(example = "1180.00", description = "Importe total; por la API REST, obligatorio para comprobantes electrónicos") @RequestParam(required = false) BigDecimal monto) {
        ConsultarValidezUseCase.Validez v = validez.consultar(TenantActual.id(req),
                new ConsultarValidezUseCase.Criterios(ruc, tipo, serie, numero, tipoDocReceptor, numDocReceptor, fecha, monto));
        return ApiResponse.ok(ValidezResponse.de(v));
    }
}
