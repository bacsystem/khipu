package pe.factura.application.service;

import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.Duration;
import java.util.Map;

/**
 * Los correos de acceso de un usuario (recuperar la contraseña, verificar el correo y dar la bienvenida a un cliente dado de alta): qué enlace lleva cada uno y cuánto dura, en un solo
 * lugar para que el flujo del propio usuario ({@link AutenticarUsuarioService}) y el que dispara el administrador desde el backoffice ({@link SoporteDeAccesoService}, #183)
 * manden exactamente lo mismo. El texto sale de {@link PlantillasDeCorreo} (editable, #199). El enlace lleva un token de un solo uso; el texto nunca lleva una contraseña.
 */
final class CorreosDeAcceso {
    private CorreosDeAcceso() {}

    static Texto recuperacion(PlantillasDeCorreo plantillas, String urlBase, String token) {
        return plantillas.de(PlantillaDeCorreo.RECUPERACION_CLAVE, Map.of("enlace", urlBase + "/restablecer/" + token, "validez", enPalabras(AutenticarUsuarioService.VIDA_RECUPERACION)));
    }

    static Texto verificacion(PlantillasDeCorreo plantillas, String urlBase, String token) {
        return plantillas.de(PlantillaDeCorreo.VERIFICACION_CORREO, Map.of("enlace", urlBase + "/verificar/" + token, "validez", enPalabras(AutenticarUsuarioService.VIDA_VERIFICACION)));
    }

    static Texto bienvenida(PlantillasDeCorreo plantillas, String razonSocial, String ruc, String urlPortal, String token) {
        return plantillas.de(PlantillaDeCorreo.BIENVENIDA, Map.of("enlace", urlPortal + "/restablecer/" + token + "?invitacion=1", "razon_social", razonSocial, "ruc", ruc,
                "validez", enPalabras(AltaAsistidaService.VIDA_INVITACION)));
    }

    /** «1 hora», «24 horas», «7 días»: en días cuando son dos o más días enteros, y en horas si no (un día se dice «24 horas», como siempre). */
    static String enPalabras(Duration d) {
        if (d.toDays() >= 2 && d.toHours() % 24 == 0) return d.toDays() + " días";
        return d.toHours() == 1 ? "1 hora" : d.toHours() + " horas";
    }
}
