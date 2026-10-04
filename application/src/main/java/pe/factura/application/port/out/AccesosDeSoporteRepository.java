package pe.factura.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lo que la bitácora (#178) tiene de las sesiones de soporte (#184) de una cuenta. */
public interface AccesosDeSoporteRepository {
    /** Las impersonaciones de esa cuenta, de la más reciente a la más antigua, hasta {@code limite}. Solo esa cuenta: nunca las de otra. */
    List<Registro> deLaCuenta(UUID cuentaId, int limite);

    /** Cuándo ocurrió y el detalle tal como lo guardó la bitácora (ver {@code DetalleDeSoporte}). */
    record Registro(Instant ocurridoEn, String detalle) {}
}
