package pe.factura.domain.tenant;

import java.util.List;

/**
 * Cuándo el último error de un envío dice que SUNAT no acepta las credenciales SOL de la empresa (#197). Un error de envío guarda el texto «código - mensaje»; sirven los
 * códigos de la familia de autenticación de SUNAT (usuario y clave: no existe, incorrecta, inactivo, sin perfil) y el 401 de la autenticación HTTP, que llega como
 * «SUNAT respondió HTTP 401». Un servicio caído, un timeout o un 5xx **no** son un problema de credenciales: el cliente no tiene nada que corregir.
 */
public final class FalloDeAutenticacionSol {
    /** 0102 usuario o clave incorrectos, 0103 el usuario no existe, 0104 clave incorrecta, 0105 usuario inactivo, 0106 usuario no válido, 0111 sin el perfil para enviar. */
    public static final List<String> CODIGOS = List.of("0102", "0103", "0104", "0105", "0106", "0111");

    /** Lo que dice el error cuando SUNAT contesta 401 a la autenticación HTTP. */
    public static final String MARCA_HTTP_401 = "HTTP 401";

    private FalloDeAutenticacionSol() {}

    public static boolean esUno(String ultimoError) {
        if (ultimoError == null) return false;
        if (ultimoError.contains(MARCA_HTTP_401)) return true;
        return CODIGOS.stream().anyMatch(c -> ultimoError.startsWith(c + " - "));
    }
}
