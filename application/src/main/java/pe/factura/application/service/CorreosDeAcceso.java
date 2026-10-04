package pe.factura.application.service;

/**
 * Los correos de acceso de un usuario (recuperar la contraseña y verificar el correo): su asunto y su texto, en un solo lugar para que el flujo
 * del propio usuario ({@link AutenticarUsuarioService}) y el que dispara el administrador desde el backoffice ({@link SoporteDeAccesoService},
 * #183) manden exactamente lo mismo. El enlace lleva un token de un solo uso; el texto nunca lleva una contraseña.
 */
final class CorreosDeAcceso {
    private CorreosDeAcceso() {}

    static final String ASUNTO_RECUPERACION = "Restablecer contraseña";
    static final String ASUNTO_VERIFICACION = "Verifica tu correo en khipu";

    static String cuerpoRecuperacion(String urlBase, String token) {
        return "Para restablecer tu contraseña abre este enlace (válido 1 hora):\n" + urlBase + "/restablecer/" + token;
    }

    static String cuerpoVerificacion(String urlBase, String token) {
        return "Para terminar de crear tu cuenta, verifica tu correo abriendo este enlace (válido 24 horas, de un solo uso):\n" + urlBase + "/verificar/" + token;
    }
}
