package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de `GET /v1/catalogos`: el resumen (número, nombre, cantidad) o, con `completo=true`, el catálogo entero. */
@Schema(oneOf = {CatalogoResponse.Resumen.class, CatalogoResponse.class},
        description = "Resumen del catálogo (por defecto) o el catálogo completo con sus entradas (`completo=true`)")
public sealed interface CatalogoIndice permits CatalogoResponse, CatalogoResponse.Resumen {
    String id();
    String nombre();
}
