package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.AccesoDeSoporteResponse;
import pe.factura.application.port.in.AccesosDeSoporteUseCase;

import java.util.List;

/** El historial de accesos de soporte a la cuenta del cliente (#184), para que sepa cuándo el equipo de khipu miró su portal. Solo del portal (sesión de cuenta). */
@RestController
@RequestMapping("/v1/cuenta")
@Tag(name = "Cuenta (portal)", description = "Lo que el cliente ve de su propia cuenta desde el portal.")
@RequiredArgsConstructor
public class CuentaAccesosDeSoporteController {
    private final AccesosDeSoporteUseCase accesos;

    @GetMapping("/accesos-de-soporte")
    @Operation(summary = "Accesos de soporte a mi cuenta", description = """
            Cada vez que alguien del equipo de khipu miró el portal como uno de los usuarios de la cuenta: cuándo, a qué usuario y por cuánto tiempo como máximo.
            Los más recientes primero, hasta 100. **No** dice qué administrador fue. Solo con sesión de cuenta (portal), no con API key.""")
    public ApiResponse<List<AccesoDeSoporteResponse>> accesosDeSoporte(HttpServletRequest req) {
        return ApiResponse.ok(accesos.deLaCuenta(CuentaActual.id(req)).stream().map(AccesoDeSoporteResponse::de).toList());
    }
}
