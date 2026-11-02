package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ConfiguracionRequest;
import pe.factura.adapters.rest.dto.ConfiguracionResponse;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.PlantillaDeCorreo;

import java.util.List;

/**
 * La configuración de la plataforma del backoffice (#199): el remitente de los correos, el texto de cada correo y el aviso de mantenimiento. Autenticado por
 * {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador. Todo cambio queda en la bitácora.
 */
@RestController
@RequestMapping("/v1/admin/configuracion")
@Tag(name = "Administración de la plataforma")
public class AdminConfiguracionController {
    private final ConfigurarPlataformaUseCase configuracion;

    public AdminConfiguracionController(ConfigurarPlataformaUseCase configuracion) { this.configuracion = configuracion; }

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @GetMapping("/correo")
    @Operation(summary = "Remitente de los correos", description = """
            De quién salen los correos de la plataforma: el remitente vigente, si lo fijó un administrador (`personalizado`) o es el de la configuración del servidor, y ese predeterminado,
            al que se vuelve al restablecer. Solo lectura.""")
    public ApiResponse<ConfiguracionResponse.Remitente> remitente() { return ApiResponse.ok(ConfiguracionResponse.Remitente.de(configuracion.remitente())); }

    @PutMapping("/correo")
    @Operation(summary = "Cambiar el remitente de los correos", description = """
            Fija el correo remitente, el nombre con que se muestra y, si se quiere, adónde llegan las respuestas. Vale desde el siguiente correo, sin reiniciar. `422 REMITENTE_INVALIDO` si
            el correo no es una sola dirección válida en ASCII o el nombre lleva saltos de línea, comillas o < >. **El servidor SMTP tiene que aceptar mandar desde esa dirección**
            (remitente verificado, SPF/DKIM): eso no se puede comprobar acá. Queda en la bitácora con el antes y el después.""")
    public ApiResponse<ConfiguracionResponse.Remitente> cambiarRemitente(@RequestBody(required = false) ConfiguracionRequest.Remitente body, HttpServletRequest req) {
        var b = body == null ? new ConfiguracionRequest.Remitente(null, null, null) : body;
        return ApiResponse.ok(ConfiguracionResponse.Remitente.de(configuracion.cambiarRemitente(AdministradorActual.actor(req), b.nombre(), b.email(), b.responderA())));
    }

    @DeleteMapping("/correo")
    @Operation(summary = "Volver al remitente del servidor", description = "Quita el remitente fijado y vuelve al de la configuración del servidor. Sin remitente propio no hace nada. Queda en la bitácora.")
    public ApiResponse<ConfiguracionResponse.Remitente> restablecerRemitente(HttpServletRequest req) {
        return ApiResponse.ok(ConfiguracionResponse.Remitente.de(configuracion.restablecerRemitente(AdministradorActual.actor(req))));
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    @GetMapping("/plantillas")
    @Operation(summary = "Plantillas de los correos", description = """
            Todos los correos que la plataforma manda, con el texto con que salen ahora, el de fábrica, si alguien lo cambió y las variables que admite cada uno (`{nombre}`). Solo lectura.""")
    public ApiResponse<List<ConfiguracionResponse.Plantilla>> plantillas() { return ApiResponse.ok(configuracion.plantillas().stream().map(ConfiguracionResponse.Plantilla::de).toList()); }

    @PutMapping("/plantillas/{tipo}")
    @Operation(summary = "Editar el texto de un correo", description = """
            Reemplaza el asunto y el cuerpo de un correo; vale desde el siguiente. `422 PLANTILLA_INVALIDA` si el asunto o el cuerpo están vacíos o pasan de 150 / 5000 caracteres, el asunto
            tiene más de una línea, usa una variable que ese correo no tiene, o el cuerpo no incluye una variable indispensable (el `{enlace}` de los correos de acceso: sin él nadie
            podría entrar). `404 NO_ENCONTRADO` si el correo no existe. Queda en la bitácora (sin el texto).""")
    public ApiResponse<ConfiguracionResponse.Plantilla> guardarPlantilla(@Parameter(description = "El correo", example = "RECUPERACION_CLAVE") @PathVariable String tipo,
                                                                        @RequestBody(required = false) ConfiguracionRequest.Plantilla body, HttpServletRequest req) {
        var b = body == null ? new ConfiguracionRequest.Plantilla(null, null) : body;
        return ApiResponse.ok(ConfiguracionResponse.Plantilla.de(configuracion.guardarPlantilla(AdministradorActual.actor(req), tipo(tipo), b.asunto(), b.cuerpo())));
    }

    @DeleteMapping("/plantillas/{tipo}")
    @Operation(summary = "Restaurar el texto de fábrica de un correo", description = "Quita el texto propio y el correo vuelve al de fábrica. Sin texto propio no hace nada. `404 NO_ENCONTRADO` si el correo no existe. Queda en la bitácora.")
    public ApiResponse<ConfiguracionResponse.Plantilla> restaurarPlantilla(@Parameter(description = "El correo", example = "RECUPERACION_CLAVE") @PathVariable String tipo, HttpServletRequest req) {
        return ApiResponse.ok(ConfiguracionResponse.Plantilla.de(configuracion.restaurarPlantilla(AdministradorActual.actor(req), tipo(tipo))));
    }

    @PostMapping("/plantillas/{tipo}/vista-previa")
    @Operation(summary = "Vista previa de un correo", description = """
            Cómo se vería ese asunto y ese cuerpo con valores de ejemplo para cada variable, validados como al guardar (`422 PLANTILLA_INVALIDA`). No guarda nada ni deja registro.""")
    public ApiResponse<ConfiguracionResponse.TextoDeCorreo> vistaPrevia(@Parameter(description = "El correo", example = "RECUPERACION_CLAVE") @PathVariable String tipo,
                                                                        @RequestBody(required = false) ConfiguracionRequest.Plantilla body) {
        var b = body == null ? new ConfiguracionRequest.Plantilla(null, null) : body;
        return ApiResponse.ok(ConfiguracionResponse.TextoDeCorreo.de(configuracion.vistaPrevia(tipo(tipo), b.asunto(), b.cuerpo())));
    }

    private static PlantillaDeCorreo tipo(String nombre) {
        return PlantillaDeCorreo.deNombre(nombre).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Ese correo no existe"));
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    @GetMapping("/banner")
    @Operation(summary = "Aviso de mantenimiento publicado", description = """
            El aviso que ven todos los clientes en su portal: su texto, su vigencia y si se está mostrando ahora (`vigente_ahora`; puede estar programado para después o haber vencido).
            `datos` es nulo si no hay ninguno. Solo lectura.""")
    public ApiResponse<ConfiguracionResponse.Banner> banner() { return ApiResponse.ok(configuracion.banner().map(ConfiguracionResponse.Banner::de).orElse(null)); }

    @PutMapping("/banner")
    @Operation(summary = "Publicar el aviso de mantenimiento", description = """
            Publica el aviso, o reemplaza el anterior (hay uno solo). Siempre tiene fin: `hasta` es obligatorio, posterior a `desde` y a ahora, y a lo sumo 90 días después de empezar.
            `422 BANNER_INVALIDO` si el texto está vacío, pasa de 300 caracteres o tiene más de una línea, o la vigencia no cumple eso. Los clientes lo ven mientras `desde ≤ ahora < hasta`.
            Queda en la bitácora con su texto y vigencia.""")
    public ApiResponse<ConfiguracionResponse.Banner> publicarBanner(@RequestBody(required = false) ConfiguracionRequest.Banner body, HttpServletRequest req) {
        var b = body == null ? new ConfiguracionRequest.Banner(null, null, null) : body;
        return ApiResponse.ok(ConfiguracionResponse.Banner.de(configuracion.publicarBanner(AdministradorActual.actor(req), b.texto(), b.desde(), b.hasta())));
    }

    @DeleteMapping("/banner")
    @Operation(summary = "Retirar el aviso de mantenimiento", description = "Lo retira antes de que venza. `404 NO_ENCONTRADO` si no hay ninguno. Queda en la bitácora.")
    public ApiResponse<Void> retirarBanner(HttpServletRequest req) {
        configuracion.retirarBanner(AdministradorActual.actor(req));
        return ApiResponse.ok(null);
    }
}
