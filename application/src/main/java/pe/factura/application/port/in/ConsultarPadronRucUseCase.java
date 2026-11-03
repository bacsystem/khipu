package pe.factura.application.port.in;

import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Consulta de un RUC en el padrón reducido de SUNAT (#361). */
public interface ConsultarPadronRucUseCase {
    /**
     * @param actualizadoAl cuándo terminó la carga del padrón que respondió: un RUC creado después no aparece, y quien consulta debe poder saberlo.
     */
    record Consulta(Contribuyente contribuyente, Instant actualizadoAl) { }

    /**
     * A quién se le cuenta el cupo (#575). Una empresa (API key o portal con empresa elegida), una cuenta del portal que todavía no tiene empresa (el onboarding) o quien
     * usa el backoffice. Cada uno tiene su propio cupo: una cuenta nueva no gasta el de nadie, y nadie esquiva el suyo cambiando de credencial dentro del mismo tipo.
     */
    record Solicitante(String clave) {
        public Solicitante {
            Objects.requireNonNull(clave, "clave");
        }

        /** La clave de una empresa es su id, sin prefijo: la que se usaba antes de #575, para que los contadores en curso sigan valiendo. */
        public static Solicitante empresa(UUID tenantId) { return new Solicitante(Objects.requireNonNull(tenantId, "tenantId").toString()); }

        public static Solicitante cuenta(UUID cuentaId) { return new Solicitante("cuenta:" + Objects.requireNonNull(cuentaId, "cuentaId")); }

        /** La clave de plataforma no identifica a una persona: comparte un solo cupo. */
        public static Solicitante backoffice(ActorAdmin actor) {
            return new Solicitante(actor.tipo() == ActorAdmin.Tipo.ADMINISTRADOR ? "admin:" + actor.administradorId() : "admin:plataforma");
        }
    }

    /**
     * {@code PARAMETRO_INVALIDO} si no es un RUC (11 dígitos, prefijo y dígito verificador); {@code PADRON_NO_CARGADO} si el padrón nunca se cargó (no se puede afirmar que el
     * RUC no existe); {@code DEMASIADAS_CONSULTAS} si quien consulta pasó su cupo; {@code NO_ENCONTRADO} si el RUC no está en el padrón.
     */
    Consulta consultar(Solicitante solicitante, String ruc);
}
