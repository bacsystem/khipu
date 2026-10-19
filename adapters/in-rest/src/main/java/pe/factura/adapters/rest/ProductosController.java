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
import pe.factura.adapters.rest.dto.ProductoDtos;
import pe.factura.application.port.in.GestionarProductosUseCase;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/productos")
@Tag(name = "Productos", description = """
        Los productos y servicios que la empresa factura seguido, guardados para no teclearlos en cada emisión: el portal los ofrece en el autocompletado de los ítems de «Nuevo comprobante».
        Cada uno lleva lo que repite una línea: descripción, unidad (catálogo 03), afectación del IGV (catálogo 07), precio unitario, código interno y código de producto SUNAT (catálogo 25).
        Se validan como la línea de un comprobante, así que lo guardado siempre se puede emitir. Funciona con API key o con la sesión del portal.""")
@RequiredArgsConstructor
public class ProductosController {
    private final GestionarProductosUseCase productos;

    @GetMapping
    @Operation(summary = "Buscar productos", description = """
            Los productos de la empresa por descripción. `q` (opcional) se compara con el código interno (empieza por) y con la descripción (contiene, sin distinguir mayúsculas ni tildes).
            El total va en `X-Total-Count`.""")
    public ResponseEntity<ApiResponse<List<ProductoDtos.Producto>>> listar(HttpServletRequest req,
                                                                           @Parameter(description = "Texto a buscar en el código o la descripción") @RequestParam(required = false) String q,
                                                                           @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
                                                                           @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        GestionarProductosUseCase.Pagina p = productos.listar(TenantActual.id(req), q, pagina, porPagina);
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.productos().stream().map(ProductoDtos.Producto::de).toList()));
    }

    @PostMapping
    @Operation(summary = "Guardar un producto", description = "Crea el producto. `409 DUPLICADO` si la empresa ya tiene uno con ese código interno; `422 ITEM_INVALIDO` si los datos no cumplen las reglas de una línea (unidad fuera del catálogo 03, precio en cero…).")
    public ResponseEntity<ApiResponse<ProductoDtos.Producto>> crear(HttpServletRequest req, @Valid @RequestBody ProductoDtos.Guardar body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ProductoDtos.Producto.de(productos.crear(TenantActual.id(req), body.datos()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un producto", description = "`404 NO_ENCONTRADO` si no es de la empresa.")
    public ApiResponse<ProductoDtos.Producto> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(ProductoDtos.Producto.de(productos.obtener(TenantActual.id(req), id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambiar un producto", description = "Reemplaza sus datos. `409 DUPLICADO` si el nuevo código interno ya es de otro producto.")
    public ApiResponse<ProductoDtos.Producto> actualizar(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody ProductoDtos.Guardar body) {
        return ApiResponse.ok(ProductoDtos.Producto.de(productos.actualizar(TenantActual.id(req), id, body.datos())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un producto", description = "Solo lo quita de la lista guardada: los comprobantes ya emitidos conservan sus datos.")
    public ResponseEntity<Void> eliminar(HttpServletRequest req, @PathVariable UUID id) {
        productos.eliminar(TenantActual.id(req), id);
        return ResponseEntity.noContent().build();
    }
}
