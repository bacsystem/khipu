package pe.factura.adapters.rest.dto;

/** Fila de `GET /v1/catalogos`: el resumen (número, nombre, cantidad) o, con `completo=true`, el catálogo entero. */
public sealed interface CatalogoIndice permits CatalogoResponse, CatalogoResponse.Resumen {
    String id();
    String nombre();
}
