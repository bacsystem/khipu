package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.UUID;

/**
 * Dar de baja y reponer una cuenta de cliente desde el backoffice (#201). La baja es para el cliente que se fue (suspender, #182, es para quien
 * no paga): lo saca de los listados operativos y del cobro, **sin borrar nada**: los comprobantes, XML y CDR se conservan, y su RUC sigue ocupado.
 * No corta por sí sola el acceso del cliente: para eso está la suspensión. Ambas acciones quedan en la bitácora a nombre de {@code actor}, en la
 * misma transacción que el cambio.
 */
public interface DarDeBajaCuentaUseCase {
    /** Lo más que cabe en el motivo: es contexto para la bitácora, no un texto largo. */
    int MOTIVO_MAX = 200;

    /**
     * {@code NO_ENCONTRADO} si la cuenta no existe; {@code CUENTA_YA_DE_BAJA} si ya lo estaba; {@code MOTIVO_INVALIDO} si el motivo pasa de
     * {@link #MOTIVO_MAX}. El motivo es opcional y va a la bitácora.
     */
    EstadoDeBaja darDeBaja(ActorAdmin actor, UUID cuentaId, String motivo);

    /** {@code NO_ENCONTRADO} si la cuenta no existe; {@code CUENTA_NO_DE_BAJA} si no estaba de baja. */
    EstadoDeBaja reponer(ActorAdmin actor, UUID cuentaId);

    /** El estado en que quedó la cuenta; {@code bajaEn} es nulo si está repuesta. */
    record EstadoDeBaja(UUID cuentaId, Instant bajaEn) {
        public boolean deBaja() { return bajaEn != null; }
    }
}
