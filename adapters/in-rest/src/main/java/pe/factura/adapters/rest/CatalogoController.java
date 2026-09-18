package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.CatalogoIndice;
import pe.factura.adapters.rest.dto.CatalogoResponse;
import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.util.List;

/** Referencia pública (sin credenciales) de los códigos que la API espera en cada campo. */
@RestController
@RequestMapping("/v1/catalogos")
@Tag(name = "Catálogos SUNAT", description = """
        Códigos oficiales que usan los campos de la API (tipo de documento, afectación del IGV, unidades de medida, tipo de
        operación, motivos de nota, cargos y descuentos, detracciones, medios de pago…). Son públicos: no requieren API key.
        La fuente es el Anexo 8 de las reglas de validación de SUNAT (versión 2026-08-26). La API valida contra ellos los códigos
        que decide el emisor (tipo de operación, afectación del IGV, tipo de documento, moneda) y responde `422` antes de consumir
        numeración; la unidad de medida no se valida (la lista UN/ECE completa excede el catálogo) y la rechaza SUNAT.""")
public class CatalogoController {

    @GetMapping
    @Operation(operationId = "listarCatalogos", summary = "Listar catálogos", description = "Índice de los catálogos disponibles con su número, nombre y cantidad de entradas. Con `completo=true` devuelve además las entradas de cada uno (una sola llamada para cachear toda la referencia).")
    public ApiResponse<List<CatalogoIndice>> listar(
            @Parameter(description = "`true` para incluir las entradas de cada catálogo") @RequestParam(defaultValue = "false") boolean completo) {
        return ApiResponse.ok(CatalogoSunat.todos().stream().<CatalogoIndice>map(completo ? CatalogoResponse::de : CatalogoResponse::resumen).toList());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "obtenerCatalogo", summary = "Consultar un catálogo", description = """
            Devuelve todas las entradas del catálogo con su código, descripción y columnas adicionales (por ejemplo, el
            catálogo 07 indica el código de tributo de cada afectación y el 53 el nivel —línea o global— de cada
            cargo/descuento). Responde `404 NO_ENCONTRADO` si el número no existe.""")
    public ApiResponse<CatalogoResponse> obtener(@Parameter(description = "Número del catálogo SUNAT, p. ej. `07`", example = "07") @PathVariable String id) {
        return ApiResponse.ok(CatalogoResponse.de(CatalogoSunat.porId(id)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "No existe el catálogo SUNAT " + id))));
    }
}
