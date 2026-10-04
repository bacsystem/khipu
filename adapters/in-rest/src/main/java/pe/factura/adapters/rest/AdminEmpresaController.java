package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.EmpresaAdminResponse;
import pe.factura.adapters.rest.dto.EmpresaDetalleResponse;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.application.port.in.VisibilidadDeBajas;
import pe.factura.domain.tenant.Entorno;

import java.util.List;
import java.util.UUID;

/**
 * Listado y detalle de las empresas de toda la plataforma del backoffice (#185, #186). Solo lectura. Autenticado por {@link AdminAuthFilter}: la clave de
 * plataforma o el JWT de un administrador; un JWT de cliente o una API key de tenant no pasan.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminEmpresaController {
    private final ListarEmpresasAdminUseCase empresas;
    private final DetalleEmpresaAdminUseCase detalle;

    @GetMapping("/empresas")
    @Operation(summary = "Listar las empresas de la plataforma", description = """
            Todas las empresas, de la más reciente a la más antigua, paginadas; incluye las dadas de alta por una integración, que no tienen
            cuenta. El total de resultados va en la cabecera `X-Total-Count` y refleja los filtros, que se combinan con «y». Por cada
            empresa: su cuenta, su entorno, el estado del certificado y los días que le quedan (hoy es la fecha de Lima), si tiene
            credenciales SOL cargadas, sus series activas, los documentos emitidos en el mes y la fecha de la última emisión. Del certificado
            y de las credenciales solo se informa su estado, **nunca su contenido**. `entorno`, `certificado` o `bajas` con un valor que no existe
            responden `400`. **Las empresas de cuentas dadas de baja (#201) no salen** salvo que se pida con `bajas`: `INCLUIDAS` las mezcla y
            `SOLO` devuelve únicamente esas; el total las trata igual. Las empresas sin cuenta nunca están de baja.""")
    public ResponseEntity<ApiResponse<List<EmpresaAdminResponse>>> listar(
            @Parameter(description = "Solo las empresas de ese entorno") @RequestParam(required = false) Entorno entorno,
            @Parameter(description = "Solo las empresas con el certificado en ese estado") @RequestParam(required = false) EstadoCertificado certificado,
            @Parameter(description = "Qué hacer con las empresas de cuentas dadas de baja: `OCULTAS` (por defecto), `INCLUIDAS` o `SOLO`") @RequestParam(required = false) VisibilidadDeBajas bajas,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var filtro = new Filtro(entorno, certificado, bajas);
        List<EmpresaAdminResponse> datos = empresas.listar(filtro, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(EmpresaAdminResponse::de).toList();
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(empresas.contar(filtro))).body(ApiResponse.ok(datos));
    }

    @GetMapping("/empresas/{id}")
    @Operation(summary = "Abrir una empresa", description = """
            Lo mismo que ve su dueño, en solo lectura: datos fiscales y domicilio, estado del certificado y de las credenciales SOL (**sin
            exponer su contenido**), series, establecimientos, API keys (**solo el prefijo**, nunca el secreto ni el hash), personalización del
            PDF (si hay logo, sin dónde está guardado), los 10 comprobantes más recientes con el detalle del CDR de SUNAT, los últimos 20
            cambios de estado de esos comprobantes y el outbox pendiente de la empresa. No se audita. `404 NO_ENCONTRADO` si la empresa no
            existe; un id que no es un UUID responde `400`.""")
    public ApiResponse<EmpresaDetalleResponse> abrir(@Parameter(description = "Id de la empresa") @PathVariable UUID id) {
        return ApiResponse.ok(EmpresaDetalleResponse.de(detalle.detalle(id)));
    }
}
