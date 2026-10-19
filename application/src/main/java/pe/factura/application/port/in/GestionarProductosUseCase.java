package pe.factura.application.port.in;

import pe.factura.domain.documento.ProductoGuardado;

import java.util.List;
import java.util.UUID;

/** Productos y servicios guardados de una empresa (#320): alta, búsqueda para el autocompletado de los ítems de «Nuevo comprobante», cambio y baja. */
public interface GestionarProductosUseCase {
    record Pagina(List<ProductoGuardado> productos, long total) {}

    /** {@code 409 DUPLICADO} si la empresa ya tiene un producto con ese código interno; {@code 422 ITEM_INVALIDO} si los datos no cumplen las reglas de una línea. */
    ProductoGuardado crear(UUID tenantId, ProductoGuardado.Datos datos);

    ProductoGuardado obtener(UUID tenantId, UUID id);

    Pagina listar(UUID tenantId, String texto, int pagina, int porPagina);

    /** {@code 409 DUPLICADO} si el nuevo código interno ya es de otro producto. */
    ProductoGuardado actualizar(UUID tenantId, UUID id, ProductoGuardado.Datos datos);

    void eliminar(UUID tenantId, UUID id);
}
