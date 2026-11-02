package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ConfiguracionResponse;
import pe.factura.application.port.in.ConsultarBannerUseCase;

/**
 * El aviso de mantenimiento que ven todos los clientes (#199). **Público**, sin credenciales: el portal lo muestra también en el inicio de sesión, y solo trae lo que se escribió
 * para que lo vean (texto y vigencia).
 */
@RestController
@RequestMapping("/v1/banner")
@Tag(name = "Catálogos y estado")
public class BannerController {
    private final ConsultarBannerUseCase banner;

    public BannerController(ConsultarBannerUseCase banner) { this.banner = banner; }

    @GetMapping
    @Operation(summary = "Aviso de mantenimiento vigente", description = """
            El aviso que se está mostrando ahora a los clientes: su texto y su vigencia. `datos` es nulo si no hay ninguno (uno programado para después o ya vencido no se muestra).
            Público, sin autenticación, y nunca se guarda en caché.""")
    public ResponseEntity<ApiResponse<ConfiguracionResponse.BannerPublico>> vigente() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(banner.vigente().map(ConfiguracionResponse.BannerPublico::de).orElse(null)));
    }
}
