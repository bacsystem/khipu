package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.EnvioAlClienteDtos.PreferenciaRequest;
import pe.factura.adapters.rest.dto.EnvioAlClienteDtos.PreferenciaResponse;
import pe.factura.application.port.in.EnviarAlClienteUseCase;

@RestController
@RequestMapping("/v1/empresa")
@RequiredArgsConstructor
@Tag(name = "Envío al cliente", description = """
        Envío automático del comprobante a su cliente: con la preferencia activada, cuando SUNAT acepta un comprobante emitido con
        `cliente.email` (una nota hereda el de su comprobante), khipu le manda a ese correo el PDF, el XML firmado y el CDR. Sale poco después
        de la aceptación, una sola vez por comprobante, y si el servidor de correo falla se reintenta hasta 5 veces en unas 2 horas y media.
        Una boleta informada en un resumen diario sale cuando SUNAT acepta el resumen. El resultado se ve en `envio_al_cliente` al consultar
        el comprobante (`GET /v1/facturas/{id}`). Apagada por defecto.""")
public class EnvioAlClienteController {
    private final EnviarAlClienteUseCase envios;

    @GetMapping("/envio-al-cliente")
    @Operation(summary = "Ver la preferencia de envío al cliente", description = "Si la empresa envía automáticamente el comprobante aceptado a su cliente, y si el servidor tiene correo configurado para hacerlo.")
    public ApiResponse<PreferenciaResponse> ver(HttpServletRequest req) {
        return ApiResponse.ok(PreferenciaResponse.de(envios.preferencia(TenantActual.id(req))));
    }

    @PutMapping("/envio-al-cliente")
    @Operation(summary = "Activar o desactivar el envío al cliente", description = """
            Vale para los comprobantes que SUNAT acepte desde ahora; lo ya aceptado no se envía. Activarlo sin correo configurado en el servidor responde
            `503 CORREO_NO_CONFIGURADO` (los clientes no recibirían nada); desactivarlo siempre se puede.""")
    public ApiResponse<PreferenciaResponse> fijar(HttpServletRequest req, @Valid @RequestBody PreferenciaRequest body) {
        return ApiResponse.ok(PreferenciaResponse.de(envios.fijarPreferencia(TenantActual.id(req), body.enviarAutomaticamente())));
    }
}
