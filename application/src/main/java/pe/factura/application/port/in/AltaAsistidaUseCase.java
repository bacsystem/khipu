package pe.factura.application.port.in;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

/** Alta asistida de un cliente desde el backoffice (#188): cuenta, primera empresa, primera serie e invitación, de una sola vez. */
public interface AltaAsistidaUseCase {
    /** {@code entorno} nulo: BETA, como en el onboarding del portal. No lleva contraseña: la elige el cliente al aceptar la invitación. */
    record Solicitud(String nombre, String email, String telefono, String ruc, String razonSocial, Entorno entorno, TipoDocumento tipoSerie, String serie) {}

    /**
     * {@code apiKeyEnClaro} se entrega esta única vez. {@code invitacionEnviada} es {@code false} si el correo no salió —el envío
     * falló, o no hay SMTP y el correo solo quedó en el log ({@code CorreoSender#entregaDeVerdad})—: el alta queda hecha igual y el
     * cliente puede pedir un enlace desde «olvidé mi contraseña».
     */
    record AltaCreada(UUID cuentaId, Tenant tenant, String apiKeyEnClaro, TipoDocumento tipoSerie, String serie, boolean invitacionEnviada) {}

    /**
     * Todo o nada: la cuenta, su usuario, la empresa, la API key, la serie, la invitación y el registro de bitácora se escriben en
     * una sola transacción. El correo sale después, fuera de ella. {@code urlPortal} es la base de los enlaces del correo.
     */
    AltaCreada alta(ActorAdmin actor, Solicitud solicitud, String urlPortal);
}
