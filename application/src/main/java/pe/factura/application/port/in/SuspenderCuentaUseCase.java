package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.UUID;

/**
 * Suspender y reactivar una cuenta de cliente desde el backoffice (#182). Suspender corta el portal y la emisión por API de todas sus
 * empresas; no borra nada, y reactivar lo devuelve todo a como estaba. Los documentos que ya se emitieron siguen enviándose a SUNAT: cortarlos
 * los dejaría fuera de plazo. Ambas acciones quedan en la bitácora a nombre de {@code actor}, en la misma transacción que el cambio.
 */
public interface SuspenderCuentaUseCase {
    /** Lo más que cabe en el motivo: es contexto para la bitácora, no un texto largo. */
    int MOTIVO_MAX = 200;

    /**
     * {@code NO_ENCONTRADO} si la cuenta no existe; {@code CUENTA_YA_SUSPENDIDA} si ya lo estaba; {@code MOTIVO_INVALIDO} si el motivo pasa de
     * {@link #MOTIVO_MAX}. El motivo es opcional y va a la bitácora.
     */
    EstadoDeCuenta suspender(ActorAdmin actor, UUID cuentaId, String motivo);

    /** {@code NO_ENCONTRADO} si la cuenta no existe; {@code CUENTA_NO_SUSPENDIDA} si ya estaba activa. */
    EstadoDeCuenta reactivar(ActorAdmin actor, UUID cuentaId);

    /**
     * El estado en que quedó la cuenta; {@code suspendidaEn} es nulo si no está suspendida. {@code bajaEn} (#201) es nulo si está en servicio: la
     * suspensión no la toca, pero hace falta para decir el estado que dice el resto de la API (la baja manda).
     */
    record EstadoDeCuenta(UUID cuentaId, Instant suspendidaEn, Instant bajaEn) {
        public EstadoDeCuenta(UUID cuentaId, Instant suspendidaEn) { this(cuentaId, suspendidaEn, null); }
        public boolean suspendida() { return suspendidaEn != null; }
    }
}
