package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Si SUNAT rechazó las credenciales SOL de una empresa (#107). Mientras estén rechazadas, el outbox no toma los envíos de esa empresa: no tiene sentido
 * golpear a SUNAT documento por documento con credenciales que no acepta. Guardar credenciales nuevas lo levanta y los envíos se reanudan solos.
 */
public interface RechazoDeSolRepository {
    record Rechazo(Instant en, String motivo) {}

    /** Marca las credenciales de la empresa como rechazadas, con el motivo de SUNAT. Si ya lo estaban, conserva la fecha del primer rechazo. */
    void marcar(UUID tenantId, String motivo, Instant en);

    Optional<Rechazo> buscar(UUID tenantId);

    /** Quita la marca y adelanta a {@code ahora} los envíos pendientes de la empresa, que estaban esperando. {@code false} si no estaba marcada. */
    boolean levantar(UUID tenantId, Instant ahora);

    /** Para quien no lleva la cuenta (tests y usos que no envían a SUNAT): no marca nada. */
    RechazoDeSolRepository NINGUNO = new RechazoDeSolRepository() {
        public void marcar(UUID t, String m, Instant e) {}
        public Optional<Rechazo> buscar(UUID t) { return Optional.empty(); }
        public boolean levantar(UUID t, Instant a) { return false; }
    };
}
