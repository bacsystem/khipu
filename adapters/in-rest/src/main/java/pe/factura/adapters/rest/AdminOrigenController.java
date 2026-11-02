package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.OrigenResponse;
import pe.factura.domain.plataforma.ActorAdmin;

/**
 * Herramienta de calibración de la IP del administrador (#208): devuelve la IP que el backend resolvió para esta petición, que
 * es la misma que {@link AdministradorActual#actor} escribiría en la bitácora. Detrás de un proxy hay que fijar cuántos saltos
 * son de confianza, y eso no se puede deducir desde fuera: se llama desde el portal y se compara con la IP pública.
 * Solo lectura, autenticado por {@link AdminAuthFilter}. No refleja la cabecera cruda, que es lo que el cliente puso.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
public class AdminOrigenController {
    @GetMapping("/origen")
    @Operation(summary = "IP de origen resuelta", description = "La IP que el backend usa como origen de esta petición (y que registra en la bitácora de auditoría). Sirve para comprobar la cadena de proxies de confianza (`TRUSTED_PROXIES`).")
    public ResponseEntity<ApiResponse<OrigenResponse>> origen(HttpServletRequest req) {
        // La misma normalización que `ActorAdmin` aplica a lo que va a la bitácora: se calibra contra lo que se guardará.
        return ResponseEntity.ok(ApiResponse.ok(new OrigenResponse(ActorAdmin.normalizarIp(req.getRemoteAddr()))));
    }
}
