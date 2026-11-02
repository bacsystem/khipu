package pe.factura.application.port.in;

import pe.factura.domain.plataforma.Administrador;

import java.util.List;
import java.util.UUID;

/**
 * Login del backoffice en dos pasos (#177): la contraseña da un desafío, y solo el segundo factor da la sesión. Sin segundo factor
 * configurado, el desafío lleva a configurarlo: no hay forma de operar el backoffice sin él.
 */
public interface AutenticarAdministradorUseCase {
    enum Paso { CONFIGURAR_SEGUNDO_FACTOR, VERIFICAR_SEGUNDO_FACTOR }

    record Desafio(String token, Paso paso) {}
    /** El secreto en texto, por si la app no puede leer el QR. */
    record Configuracion(String secreto, String uri, byte[] qrPng) {}
    record Sesion(String accessToken, long expiraEnSegundos, Administrador administrador) {}
    /** Los códigos de recuperación se muestran una sola vez: solo se guarda su hash. */
    record SesionNueva(Sesion sesion, List<String> codigosRecuperacion) {}

    /** {@code ip}: la del cliente ya resuelta, o {@code null}. Mismo límite de intentos que el portal, contado aparte (#261). */
    Desafio login(String email, String password, String ip);
    Configuracion configurarSegundoFactor(String desafio);
    SesionNueva confirmarSegundoFactor(String desafio, String codigo, String ip);
    /** {@code codigo}: el de la app de autenticación o uno de recuperación. */
    Sesion verificarSegundoFactor(String desafio, String codigo, String ip);
    Administrador me(UUID administradorId);
}
