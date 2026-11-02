package pe.factura.application.port.out;

import pe.factura.domain.plan.Pago;

import java.util.List;
import java.util.UUID;

/**
 * Los pagos que los administradores registran a mano (#194). Solo se agregan: un pago anotado no se edita ni se borra, porque es el rastro de plata que entró (una
 * equivocación se aclara con otro apunte, no reescribiendo el anterior).
 */
public interface PagoRepository {
    /**
     * Guarda el pago. {@code false} si la cuenta ya tenía un pago por ese medio con esa referencia (sin distinguir mayúsculas): es el mismo apunte repetido, y no se guarda
     * dos veces aunque el administrador haga doble clic o reintente. Un pago sin referencia nunca se considera repetido.
     */
    boolean registrar(Pago pago);

    /** Los pagos de la cuenta, el más reciente primero: por fecha de pago, luego por cuándo se registró y, a igualdad, por id (para que dos páginas no se pisen). Página desde 1. */
    List<Pago> deLaCuenta(UUID cuentaId, int pagina, int porPagina);

    long contarDeLaCuenta(UUID cuentaId);
}
