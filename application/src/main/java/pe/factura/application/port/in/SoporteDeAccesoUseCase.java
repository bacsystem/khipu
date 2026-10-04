package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;

import java.util.UUID;

/**
 * Lo más frecuente del soporte, sin pedirle nada al cliente (#183): mandarle el correo para restablecer su contraseña o reenviarle el de
 * verificación. Nunca se muestra ni se fija una contraseña desde el backoffice: el administrador solo dispara el correo, y es el usuario quien
 * elige la suya con el enlace. Ni el enlace ni su token salen en la respuesta. Las dos acciones quedan en la bitácora a nombre de {@code actor},
 * en la misma transacción que la creación del enlace.
 *
 * <p>El usuario se busca dentro de la cuenta que dice la ruta: un id de usuario de otra cuenta es {@code NO_ENCONTRADO}.
 */
public interface SoporteDeAccesoUseCase {
    /**
     * {@code NO_ENCONTRADO} si el usuario no existe o no es de esa cuenta; {@code USUARIO_INACTIVO} si está desactivado;
     * {@code CORREO_NO_CONFIGURADO} si el servidor no entrega correos (no se crea nada); {@code CORREO_NO_ENVIADO} si el servidor de correo lo
     * rechaza (el enlace ya se creó y la acción quedó en la bitácora: es el intento del administrador).
     */
    Destinatario enviarRestablecimiento(ActorAdmin actor, UUID cuentaId, UUID usuarioId, String urlBase);

    /** Lo mismo, y {@code CORREO_YA_VERIFICADO} si el usuario ya verificó su correo: no hay nada que reenviar. */
    Destinatario reenviarVerificacion(ActorAdmin actor, UUID cuentaId, UUID usuarioId, String urlBase);

    /** A quién se le mandó el correo. Sin el enlace, sin el token. */
    record Destinatario(UUID usuarioId, String correo) {}
}
