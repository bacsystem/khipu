package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.ClienteDtos;
import pe.factura.application.port.in.GestionarClientesUseCase;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clientes")
@Tag(name = "Clientes", description = """
        Los compradores a los que la empresa factura seguido, guardados para no teclearlos en cada emisión: el portal los ofrece en el autocompletado de «Nuevo comprobante». El documento
        (`tipo_doc` + `num_doc`) es único por empresa y se valida como el comprador de una boleta (DNI de 8 dígitos, RUC con dígito verificador…); «sin documento» no se guarda.
        Funciona con API key o con la sesión del portal. Para completar razón social y dirección de un RUC, `GET /v1/consultas/ruc/{ruc}` consulta el padrón de SUNAT; no hay una consulta
        pública y gratuita de DNI, así que el nombre de un DNI se teclea.""")
@RequiredArgsConstructor
public class ClientesController {
    private final GestionarClientesUseCase clientes;

    @GetMapping
    @Operation(summary = "Buscar clientes", description = """
            Los clientes de la empresa por nombre. `q` (opcional) se compara con el documento (empieza por) y con el nombre (contiene, sin distinguir mayúsculas ni tildes). El total va en
            `X-Total-Count`.""")
    public ResponseEntity<ApiResponse<List<ClienteDtos.Cliente>>> listar(HttpServletRequest req,
                                                                         @Parameter(description = "Texto a buscar en el documento o el nombre") @RequestParam(required = false) String q,
                                                                         @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
                                                                         @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        GestionarClientesUseCase.Pagina p = clientes.listar(TenantActual.id(req), q, pagina, porPagina);
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.clientes().stream().map(ClienteDtos.Cliente::de).toList()));
    }

    @PostMapping
    @Operation(summary = "Guardar un cliente", description = "Crea el cliente. `409 DUPLICADO` si la empresa ya tiene uno con ese documento; `422 RECEPTOR_INVALIDO` si el documento o el nombre no cumplen las reglas del comprador.")
    public ResponseEntity<ApiResponse<ClienteDtos.Cliente>> crear(HttpServletRequest req, @Valid @RequestBody ClienteDtos.Crear body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ClienteDtos.Cliente.de(clientes.crear(TenantActual.id(req), body.datos()))));
    }

    @PutMapping("/documento/{tipoDoc}/{numDoc}")
    @Operation(summary = "Guardar o actualizar un cliente por su documento", description = "Crea el cliente (201) o, si ya existe, actualiza sus datos (200). Es idempotente: repetirlo no duplica nada. Es lo que usa el guardado automático al emitir.")
    public ResponseEntity<ApiResponse<ClienteDtos.Cliente>> guardarPorDocumento(HttpServletRequest req, @PathVariable String tipoDoc, @PathVariable String numDoc,
                                                                                @Valid @RequestBody ClienteDtos.Guardar body) {
        GestionarClientesUseCase.Guardado g = clientes.guardarPorDocumento(TenantActual.id(req), body.datos(tipoDoc, numDoc));
        return ResponseEntity.status(g.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(ApiResponse.ok(ClienteDtos.Cliente.de(g.cliente())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un cliente", description = "`404 NO_ENCONTRADO` si no es de la empresa.")
    public ApiResponse<ClienteDtos.Cliente> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ClienteDtos.Cliente.de(clientes.obtener(TenantActual.id(req), id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambiar un cliente", description = "Reemplaza sus datos (también puede cambiar el documento). `409 DUPLICADO` si el nuevo documento ya es de otro cliente.")
    public ApiResponse<ClienteDtos.Cliente> actualizar(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody ClienteDtos.Crear body) {
        return ApiResponse.ok(ClienteDtos.Cliente.de(clientes.actualizar(TenantActual.id(req), id, body.datos())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un cliente", description = "Solo lo quita de la lista guardada: los comprobantes ya emitidos conservan sus datos.")
    public ResponseEntity<Void> eliminar(HttpServletRequest req, @PathVariable UUID id) {
        clientes.eliminar(TenantActual.id(req), id);
        return ResponseEntity.noContent().build();
    }
}
