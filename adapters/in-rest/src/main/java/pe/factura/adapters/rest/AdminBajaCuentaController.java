package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.BajaCuentaResponse;
import pe.factura.adapters.rest.dto.DarDeBajaCuentaRequest;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase;

import java.util.UUID;

/**
 * Baja lógica de una cuenta de cliente desde el backoffice (#201). Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un
 * administrador. Distinto de suspender (#182, {@link AdminCuentaController}): la suspensión corta el servicio de quien no paga y se revierte a
 * diario; la baja es para el cliente que se fue.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminBajaCuentaController {
    private final DarDeBajaCuentaUseCase baja;

    @PostMapping("/cuentas/{id}/baja")
    @Operation(summary = "Dar de baja una cuenta", description = """
            Para el cliente que se fue: la cuenta y sus empresas salen de los listados operativos (cuentas y empresas) y del cálculo de consumo y
            cobro. **No se borra nada**: los comprobantes, XML y CDR se conservan (la retención es una obligación legal del emisor) y siguen
            siendo consultables, y el RUC de sus empresas **sigue ocupado**, así que nadie más puede registrarlo. La cuenta de baja se puede
            consultar con `bajas=INCLUIDAS` o `bajas=SOLO` en los listados, y se revierte con `reponer`. **No corta por sí sola el acceso del
            cliente al portal ni a la API**: para cortar el servicio está la suspensión. Queda en la bitácora de auditoría, con el motivo si se
            dio, en la misma transacción. `409 CUENTA_YA_DE_BAJA` si ya lo estaba; `404 NO_ENCONTRADO` si no existe; `422 MOTIVO_INVALIDO` si
            el motivo pasa de 200 caracteres.""")
    public ApiResponse<BajaCuentaResponse> darDeBaja(@Parameter(description = "Id de la cuenta") @PathVariable UUID id,
                                                     @RequestBody(required = false) DarDeBajaCuentaRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(BajaCuentaResponse.de(baja.darDeBaja(actor, id, body == null ? null : body.motivo())));
    }

    @PostMapping("/cuentas/{id}/reponer")
    @Operation(summary = "Reponer una cuenta dada de baja", description = """
            Revierte la baja: la cuenta y sus empresas vuelven a los listados operativos. No toca la suspensión, que es independiente. Queda en
            la bitácora de auditoría. `409 CUENTA_NO_DE_BAJA` si no estaba de baja; `404 NO_ENCONTRADO` si no existe.""")
    public ApiResponse<BajaCuentaResponse> reponer(@Parameter(description = "Id de la cuenta") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(BajaCuentaResponse.de(baja.reponer(actor, id)));
    }
}
