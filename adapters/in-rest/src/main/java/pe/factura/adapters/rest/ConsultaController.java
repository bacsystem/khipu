package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ValidezResponse;
import pe.factura.application.port.in.ConsultarValidezUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/v1")
@Tag(name = "Consultas", description = "Consultas a SUNAT con las credenciales SOL de la empresa. Solo en producción: e-beta no publica estos servicios (`422 NO_DISPONIBLE_EN_BETA`).")
@RequiredArgsConstructor
public class ConsultaController {
    private final ConsultarValidezUseCase validez;

    @GetMapping("/consultas/validez")
    @Operation(summary = "Consultar la validez de un comprobante", description = """
            `billValidService` (`validaCDPcriterios`) de SUNAT: estado de un comprobante electrónico de cualquier emisor —el propio
            o un proveedor— por sus datos. `estado`: `ACEPTADO` (0001), `RECHAZADO` (0002), `DE_BAJA` (0003), `NO_EXISTE` (0011),
            `AJENO` (0012) o `ERROR_CONSULTA` (otros códigos, con `mensaje`). Los criterios opcionales afinan la búsqueda: si se
            envían y no coinciden con lo que SUNAT registró, la respuesta lo indica. Requiere credenciales SOL cargadas.""")
    public ApiResponse<ValidezResponse> validez(HttpServletRequest req,
                                                @Parameter(example = "20100066603", description = "RUC del emisor del comprobante") @RequestParam String ruc,
                                                @Parameter(example = "01", description = "01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito") @RequestParam String tipo,
                                                @Parameter(example = "F001") @RequestParam String serie,
                                                @Parameter(example = "125") @RequestParam long numero,
                                                @Parameter(example = "6", description = "Tipo de documento del receptor (catálogo 06); opcional") @RequestParam(name = "tipo_doc_receptor", required = false) String tipoDocReceptor,
                                                @Parameter(example = "20601234565", description = "Número de documento del receptor; opcional") @RequestParam(name = "num_doc_receptor", required = false) String numDocReceptor,
                                                @Parameter(example = "2026-09-14", description = "Fecha de emisión (`YYYY-MM-DD`); opcional") @RequestParam(required = false) LocalDate fecha,
                                                @Parameter(example = "1180.00", description = "Importe total; opcional") @RequestParam(required = false) BigDecimal monto) {
        ConsultarValidezUseCase.Validez v = validez.consultar(TenantActual.id(req),
                new ConsultarValidezUseCase.Criterios(ruc, tipo, serie, numero, tipoDocReceptor, numDocReceptor, fecha, monto));
        return ApiResponse.ok(new ValidezResponse(v.estado(), v.codigo(), v.mensaje()));
    }
}
