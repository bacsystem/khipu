package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Establecimiento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstablecimientoRepository {
    void guardar(Establecimiento e); // insert o update por (tenant, código)
    Optional<Establecimiento> buscar(UUID tenantId, String codigo);
    /** Como {@link #buscar}, pero bloquea la fila (SELECT ... FOR UPDATE): serializa crear una serie contra ese anexo con darlo de baja. */
    Optional<Establecimiento> buscarConBloqueo(UUID tenantId, String codigo);
    /** Anexos de la empresa (activos e inactivos), por código; el 0000 no está aquí: es el domicilio del tenant. */
    List<Establecimiento> listar(UUID tenantId);

    /**
     * Resuelve en una sola consulta (JOIN con {@code serie}) el establecimiento asignado a una serie de emisión —evita el
     * SELECT a {@code serie} más el SELECT a {@code establecimiento} por separado en el camino caliente de emisión—.
     * Vacío si la serie no existe o está en el domicilio fiscal ({@code 0000}); si está asignada a un código sin
     * establecimiento registrado, {@link Asignacion#establecimiento()} viene nulo para que el caller decida
     * (emitir rechaza, imprimir cae al domicilio fiscal).
     */
    Optional<Asignacion> buscarAsignacionDeSerie(UUID tenantId, TipoDocumento tipo, String serie);

    /** {@code codigo}: establecimiento asignado a la serie (nunca {@code 0000}); {@code establecimiento}: nulo si ese código no existe. */
    record Asignacion(String codigo, Establecimiento establecimiento) {}
}
