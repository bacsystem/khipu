package pe.factura.application.port.in;

import pe.factura.domain.plan.Pago;

import java.util.List;
import java.util.UUID;

/** El historial de pagos de una cuenta (#194), del más reciente al más antiguo. Solo lectura: no se audita. */
public interface ConsultarPagosUseCase {
    record Pagina(List<Pago> pagos, long total) {}

    /** Una página, desde 1. {@code NO_ENCONTRADO} si la cuenta no existe. */
    Pagina deLaCuenta(UUID cuentaId, int pagina, int porPagina);
}
