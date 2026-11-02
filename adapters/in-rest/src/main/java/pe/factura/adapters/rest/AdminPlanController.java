package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.PlanRequest;
import pe.factura.adapters.rest.dto.PlanResponse;
import pe.factura.application.port.in.GestionarPlanesUseCase;

import java.util.List;
import java.util.UUID;

/**
 * Los planes que se venden, gestionados desde el backoffice (#190). Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador. Todo
 * cambio queda en la bitácora de auditoría, a nombre de quien lo hizo y en la misma transacción.
 */
@RestController
@RequestMapping("/v1/admin/planes")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminPlanController {
    private final GestionarPlanesUseCase planes;

    @GetMapping
    @Operation(summary = "Listar los planes", description = """
            Todos los planes, activos o no, del más barato al más caro, con sus límites, su precio y **cuántas cuentas los tienen hoy** como su suscripción
            vigente. `limites` son los que mandan en el ciclo en curso; `limites_programados` es un cambio ya decidido que entra al inicio del ciclo
            siguiente (el mes calendario en America/Lima), si lo hay. Un límite sin tope viene con `ilimitado: true` y sin `maximo`.""")
    public ApiResponse<List<PlanResponse>> listar() {
        return ApiResponse.ok(planes.listar().stream().map(PlanResponse::de).toList());
    }

    @PostMapping
    @Operation(summary = "Crear un plan", description = """
            Nace activo. Validación: nombre obligatorio, hasta 40 caracteres y **único sin importar mayúsculas** (`409 NOMBRE_DUPLICADO`); precio mensual cero o más
            con hasta dos decimales (`422 PRECIO_INVALIDO`); límites mayores que cero, o `ilimitado: true` para documentos, usuarios y API keys (`422
            LIMITE_INVALIDO`); retención de al menos un año (`422 RETENCION_INVALIDA`). Un límite que se omite se rechaza: nunca se vuelve «ilimitado» en
            silencio. Queda en la bitácora.""")
    public ResponseEntity<ApiResponse<PlanResponse>> crear(@RequestBody PlanRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(PlanResponse.de(planes.crear(actor, body.aDominio()))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un plan", description = """
            Reemplaza nombre, precio y límites. **El nombre y el precio cambian al instante; los límites, no**: si difieren de los vigentes quedan programados para el
            inicio del ciclo siguiente (`limites_programados`), así que subir un tope a mitad de mes no regala documentos del mes corriente ni bajarlo le corta a
            nadie. Un segundo cambio antes de que llegue reemplaza al primero, y poner otra vez los límites vigentes cancela el programado. No toca a las cuentas
            que ya tienen el plan. Sin ningún cambio no escribe ni deja registro. `404 NO_ENCONTRADO`; `409 NOMBRE_DUPLICADO`. Queda en la bitácora, con qué
            cambió y de qué a qué.""")
    public ApiResponse<PlanResponse> editar(@Parameter(description = "Id del plan") @PathVariable UUID id, @RequestBody PlanRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(PlanResponse.de(planes.editar(actor, id, body.aDominio())));
    }

    @PostMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar un plan", description = """
            Lo saca de la oferta **sin tocar a las cuentas que ya lo tienen**: siguen con su suscripción. `409 PLAN_POR_DEFECTO` si es el plan con el que nacen las
            cuentas nuevas; `409 PLAN_YA_INACTIVO`; `404 NO_ENCONTRADO`. Queda en la bitácora.""")
    public ApiResponse<PlanResponse> desactivar(@Parameter(description = "Id del plan") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(PlanResponse.de(planes.desactivar(actor, id)));
    }

    @PostMapping("/{id}/activar")
    @Operation(summary = "Volver a ofrecer un plan", description = "Revierte la desactivación. `409 PLAN_YA_ACTIVO`; `404 NO_ENCONTRADO`. Queda en la bitácora.")
    public ApiResponse<PlanResponse> activar(@Parameter(description = "Id del plan") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(PlanResponse.de(planes.activar(actor, id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar un plan", description = """
            Solo un plan que nadie usó nunca. Con alguna cuenta que lo tiene —o que lo tuvo— responde `409 PLAN_EN_USO`: hay que **desactivarlo**. El plan con el
            que nacen las cuentas nuevas no se borra (`409 PLAN_POR_DEFECTO`). `404 NO_ENCONTRADO`. Queda en la bitácora.""")
    public ApiResponse<Void> eliminar(@Parameter(description = "Id del plan") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        planes.eliminar(actor, id);
        return ApiResponse.ok(null);
    }
}
