package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.LimitesDto;
import pe.factura.application.port.in.ConsultarPlanesPublicadosUseCase;
import pe.factura.domain.plan.Plan;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Los planes de la página de precios (H20). **Público**, sin credenciales: la portada los lee para no tener los precios escritos a mano. Solo los activos marcados
 * como visibles; un plan a medida para un cliente no sale. Sin ids ni cuántas cuentas tiene cada uno: solo lo que se publica.
 */
@RestController
@RequestMapping("/v1/planes")
@Tag(name = "Catálogos y estado")
public class PlanesPublicadosController {
    private final ConsultarPlanesPublicadosUseCase planes;

    public PlanesPublicadosController(ConsultarPlanesPublicadosUseCase planes) { this.planes = planes; }

    /** Un plan tal como se publica: nombre, precio y límites de hoy. */
    public record PlanPublicado(@Schema(example = "Emprende") String nombre, @Schema(example = "29.00") BigDecimal precioMensual, LimitesDto limites) {
        static PlanPublicado de(Plan p) { return new PlanPublicado(p.nombre(), p.precioMensual(), LimitesDto.de(p.limites())); }
    }

    @GetMapping
    @Operation(summary = "Planes publicados", description = """
            Los planes que se venden en la página de precios, del más barato al más caro, con los límites que mandan hoy. Un plan a medida (activo pero no visible en
            la publicidad) o fuera de la oferta no sale. Público, sin autenticación; se puede guardar en caché unos minutos.""")
    public ResponseEntity<ApiResponse<List<PlanPublicado>>> publicados() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.ok(planes.publicados().stream().map(PlanPublicado::de).toList()));
    }
}
