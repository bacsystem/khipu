package pe.factura.application.port.in;

import pe.factura.domain.documento.ClienteGuardado;

import java.util.List;
import java.util.UUID;

/** Clientes guardados de una empresa (#319): alta, búsqueda para el autocompletado de «Nuevo comprobante», cambio y baja. */
public interface GestionarClientesUseCase {
    /** {@code creado}: el cliente no existía y se creó; si existía se actualizó. */
    record Guardado(ClienteGuardado cliente, boolean creado) {}

    record Pagina(List<ClienteGuardado> clientes, long total) {}

    /** {@code 409 DUPLICADO} si la empresa ya tiene un cliente con ese documento. */
    ClienteGuardado crear(UUID tenantId, ClienteGuardado.Datos datos);

    /**
     * Crea o actualiza el cliente de ese documento: es lo que usa el guardado automático al emitir, y repetirlo no duplica nada. Al actualizar, la dirección, el país y el correo que no
     * vienen se conservan (el comprobante no siempre los trae y no debe borrar lo que la persona ya guardó); el nombre sí se actualiza.
     */
    Guardado guardarPorDocumento(UUID tenantId, ClienteGuardado.Datos datos);

    ClienteGuardado obtener(UUID tenantId, UUID id);

    Pagina listar(UUID tenantId, String texto, int pagina, int porPagina);

    /** {@code 409 DUPLICADO} si el nuevo documento ya es de otro cliente. */
    ClienteGuardado actualizar(UUID tenantId, UUID id, ClienteGuardado.Datos datos);

    void eliminar(UUID tenantId, UUID id);
}
