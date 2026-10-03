package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.AltaAsistidaRequest;
import pe.factura.adapters.rest.dto.AltaAsistidaResponse;
import pe.factura.application.port.in.AltaAsistidaUseCase;
import pe.factura.application.port.in.AltaAsistidaUseCase.Solicitud;
import pe.factura.domain.documento.TipoDocumento;

/**
 * Alta asistida de un cliente desde el backoffice (#188). Con el registro público cerrado (#174) es el único camino para
 * incorporar a un cliente. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
public class AdminAltaAsistidaController {
    private final AltaAsistidaUseCase alta;
    private final String portalUrl;

    public AdminAltaAsistidaController(AltaAsistidaUseCase alta, @Value("${app.portal-url}") String portalUrl) {
        this.alta = alta;
        this.portalUrl = portalUrl;
    }

    @PostMapping("/cuentas")
    @Operation(summary = "Dar de alta a un cliente: cuenta, empresa y primera serie", description = """
            Crea en una sola transacción la cuenta con su primer usuario, la primera empresa atada a ella, su primera serie y una
            API key inicial, y manda al correo del cliente una invitación para que cree su contraseña (el administrador nunca la
            elige). Todo o nada: si algo falla, no queda nada. La API key se devuelve solo en esta respuesta. Si el correo no
            salió, `invitacion_enviada` es `false` y el alta queda hecha: el cliente puede pedir un enlace con «olvidé mi
            contraseña». `409 DUPLICADO` si el correo o el RUC ya existen.""")
    public ResponseEntity<ApiResponse<AltaAsistidaResponse>> crear(@Valid @RequestBody AltaAsistidaRequest body, HttpServletRequest req) {
        var solicitud = new Solicitud(body.nombre(), body.email(), body.telefono(), body.empresa().ruc(), body.empresa().razonSocial(),
                body.empresa().entorno(), TipoDocumento.porCodigo(body.serie().tipo()), body.serie().serie());
        var r = alta.alta(AdministradorActual.actor(req), solicitud, portalUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AltaAsistidaResponse.de(r)));
    }
}
