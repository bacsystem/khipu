package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final ObjectMapper json;

    public AdminAltaAsistidaController(AltaAsistidaUseCase alta, @Value("${app.portal-url}") String portalUrl, ObjectMapper json) {
        this.alta = alta;
        this.portalUrl = portalUrl;
        this.json = json;
    }

    @PostMapping("/cuentas")
    @Operation(summary = "Dar de alta a un cliente: cuenta, empresa y primera serie", description = """
            Crea en una sola transacción la cuenta con su primer usuario, la primera empresa atada a ella, su primera serie y una
            API key inicial, y manda al correo del cliente una invitación para que cree su contraseña (el administrador nunca la
            elige). Todo o nada: si algo falla, no queda nada. La API key se devuelve solo en esta respuesta. Si el correo no
            salió —el SMTP lo rechazó, o no hay SMTP configurado y solo quedó en el log—, `invitacion_enviada` es `false` y el
            alta queda hecha: el cliente puede pedir un enlace con «olvidé mi contraseña». `409 DUPLICADO` si el correo o el
            RUC ya existen.

            **Reintentos** (`Idempotency-Key`, #219): con una clave por alta (un UUID), repetir el mismo pedido devuelve `200` con
            **la misma respuesta, incluida la API key**, sin crear nada. La respuesta se guarda cifrada y solo una hora; después el
            reintento responde `409 IDEMPOTENCIA_VENCIDA` (el alta ya está hecha; el cliente crea otra API key desde su portal). La
            misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`.""")
    public ResponseEntity<ApiResponse<AltaAsistidaResponse>> crear(@Valid @RequestBody AltaAsistidaRequest body, HttpServletRequest req,
            @Parameter(description = "Clave única por alta (un UUID), para repetir el pedido sin perder la API key", example = "a1b2c3d4-0000-4000-8000-000000000001")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        var idempotencia = ClaveDeIdempotencia.de(clave, body, json);
        var solicitud = new Solicitud(body.nombre(), body.email(), body.telefono(), body.empresa().ruc(), body.empresa().razonSocial(),
                body.empresa().entorno(), TipoDocumento.porCodigo(body.serie().tipo()), body.serie().serie());
        if (idempotencia == null) {
            var r = alta.alta(AdministradorActual.actor(req), solicitud, portalUrl);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AltaAsistidaResponse.de(r)));
        }
        var resultado = alta.alta(AdministradorActual.actor(req), solicitud, portalUrl, idempotencia);
        return ResponseEntity.status(resultado.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(AltaAsistidaResponse.de(resultado.alta())));
    }
}
