package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.tenant.Entorno;

import java.time.Instant;
import java.util.UUID;

/**
 * Acciones del administrador sobre una empresa (#187): cambiar su entorno, revocar una de sus API keys y probar su conexión con SUNAT. Las tres quedan
 * en la bitácora a nombre de {@code actor} (también en la de su cuenta, si la tiene), sin secretos: ni claves, ni credenciales SOL.
 */
public interface AccionesDeEmpresaUseCase {
    /**
     * Cambia el entorno (BETA ↔ PRODUCCION), que decide contra qué URLs de SUNAT se emite; no toca ningún comprobante ya emitido. {@code NO_ENCONTRADO}
     * si la empresa no existe; {@code ENTORNO_INVALIDO} si {@code hacia} es nulo; {@code ENTORNO_SIN_CAMBIOS} si ya estaba en ese entorno;
     * {@code EMPRESA_CON_ENVIOS_PENDIENTES} si tiene tareas en el outbox: lo emitido en un entorno no debe enviarse al otro.
     */
    CambioDeEntorno cambiarEntorno(ActorAdmin actor, UUID empresaId, Entorno hacia);

    /** {@code NO_ENCONTRADO} si la empresa o la key no existen (o la key es de otra empresa); {@code API_KEY_YA_REVOCADA} si ya lo estaba. */
    ApiKeyRevocada revocarApiKey(ActorAdmin actor, UUID empresaId, UUID apiKeyId);

    /**
     * Consulta a SUNAT, con las credenciales SOL y el entorno de la empresa, el estado de un ticket que no existe: es de solo lectura y no envía ni
     * cambia nada. {@code NO_ENCONTRADO} si la empresa no existe; {@code SOL_NO_CARGADAS} si no tiene credenciales SOL.
     */
    ResultadoDeConexion probarConexion(ActorAdmin actor, UUID empresaId);

    record CambioDeEntorno(UUID empresaId, Entorno desde, Entorno hacia) {}

    record ApiKeyRevocada(UUID apiKeyId, Instant revocadaEn) {}

    /**
     * Cómo contestó SUNAT. {@code CONECTADO}: contestó con normalidad. {@code RECHAZADO}: contestó con un error definitivo (el código y el mensaje de
     * SUNAT, tal cual: con un ticket que no existe, un error de ticket es esperable). {@code SIN_RESPUESTA}: no hubo una respuesta útil (red, tiempo
     * de espera, error del servicio o de autenticación HTTP).
     */
    enum Resultado { CONECTADO, RECHAZADO, SIN_RESPUESTA }

    record ResultadoDeConexion(Resultado resultado, Entorno entorno, String codigo, String mensaje) {}
}
