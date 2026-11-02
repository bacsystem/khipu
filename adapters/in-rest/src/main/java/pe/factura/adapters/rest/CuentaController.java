package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.MiCuentaResponse;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase;

/** La cuenta del cliente vista desde el portal (C1, C7): su nombre, su plan y su consumo del mes. */
@RestController
@RequestMapping("/v1/cuenta")
@Tag(name = "Cuenta (portal)", description = "Lo que el cliente ve de su propia cuenta desde el portal.")
@RequiredArgsConstructor
public class CuentaController {
    private final ConsultarMiCuentaUseCase miCuenta;

    @GetMapping
    @Operation(summary = "Mi cuenta: plan y consumo del mes", description = """
            El nombre de la cuenta, el plan de hoy con su estado de pago (`VIGENTE`, `EN_GRACIA`, `VENCIDA`), hasta cuándo está pagado y una bajada programada si la
            hay, y los documentos consumidos este mes (America/Lima) contra el tope del plan. Solo lectura: el plan lo cambia el equipo de khipu. Solo con sesión de
            cuenta (portal), no con API key: `401 NO_AUTORIZADO`.""")
    public ApiResponse<MiCuentaResponse> ver(HttpServletRequest req) {
        return ApiResponse.ok(MiCuentaResponse.de(miCuenta.deLaCuenta(CuentaActual.id(req))));
    }
}
