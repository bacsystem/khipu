package pe.factura.application.port.out;

import pe.factura.domain.documento.ProductoGuardado;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los productos guardados de cada empresa (#320). Todo se acota por empresa: un id de otra empresa no existe. */
public interface ProductoRepository {
    /** Guarda el producto nuevo; {@code false} si la empresa ya tiene uno con ese código interno (nada se escribe). */
    boolean crear(ProductoGuardado producto);

    /** Reemplaza los datos del producto; {@code false} si no es de la empresa o si el nuevo código interno ya es de otro producto (nada se escribe). */
    boolean actualizar(ProductoGuardado producto);

    Optional<ProductoGuardado> buscar(UUID tenantId, UUID id);

    /**
     * Los productos de la empresa por descripción. {@code texto} (nulo o vacío: todos) se compara con el código interno (empieza por) y con la descripción (contiene, sin distinguir mayúsculas ni
     * tildes). Páginas desde 1.
     */
    List<ProductoGuardado> listar(UUID tenantId, String texto, int pagina, int porPagina);

    long contar(UUID tenantId, String texto);

    boolean eliminar(UUID tenantId, UUID id);
}
