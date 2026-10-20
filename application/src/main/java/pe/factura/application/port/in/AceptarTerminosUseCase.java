package pe.factura.application.port.in;

import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.util.UUID;

/**
 * Los términos del servicio y la política de privacidad (#342). Se aceptan al registrarse y, si suben de versión, otra vez al entrar: hasta
 * entonces el portal no deja usar el panel.
 */
public interface AceptarTerminosUseCase {
    /** La versión que hay que aceptar hoy. */
    String versionVigente();

    /** {@code true} si el usuario no aceptó la versión vigente: un usuario anterior a #342 o uno que aceptó una versión vieja. */
    boolean pendiente(UUID usuarioId);

    /** Registra que el usuario aceptó la versión vigente; aceptarla otra vez no mueve la fecha. */
    AceptacionDeTerminos aceptar(UUID usuarioId);
}
