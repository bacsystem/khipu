package pe.factura.application.port.out;

import pe.factura.domain.documento.ClienteGuardado;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los clientes guardados de cada empresa (#319). Todo se acota por empresa: un id de otra empresa no existe. */
public interface ClienteRepository {
    /** Guarda el cliente nuevo; {@code false} si la empresa ya tiene uno con ese documento (nada se escribe). */
    boolean crear(ClienteGuardado cliente);

    /** Reemplaza los datos del cliente; {@code false} si el nuevo documento ya es de otro cliente de la empresa (nada se escribe). */
    boolean actualizar(ClienteGuardado cliente);

    Optional<ClienteGuardado> buscar(UUID tenantId, UUID id);

    Optional<ClienteGuardado> buscarPorDocumento(UUID tenantId, String tipoDoc, String numDoc);

    /**
     * Los clientes de la empresa por nombre. {@code texto} (nulo o vacío: todos) se compara con el documento (empieza por) y con el nombre (contiene, sin distinguir mayúsculas ni
     * tildes). Páginas desde 1.
     */
    List<ClienteGuardado> listar(UUID tenantId, String texto, int pagina, int porPagina);

    long contar(UUID tenantId, String texto);

    boolean eliminar(UUID tenantId, UUID id);
}
