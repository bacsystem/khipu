package pe.factura.application.port.out;

import java.util.List;

/**
 * Sondea si los servicios de SUNAT que usa la plataforma responden (#195), para el monitor del backoffice. Es una pregunta barata («¿contesta?»), no una prueba de que
 * emitir funciona: no envía ningún comprobante ni usa credenciales de ninguna empresa.
 */
public interface SondeoDeSunat {
    /** Los servicios que se sondean. */
    enum Servicio { ENVIO_PRODUCCION, ENVIO_BETA, CONSULTA_DE_CDR, CONSULTA_DE_VALIDEZ }

    /**
     * {@code disponible}: contestó con un éxito dentro del plazo. {@code milisegundos}: lo que tardó en contestar; nulo si no contestó. {@code detalle}: por qué no
     * (tiempo agotado, sin conexión, el código HTTP); nulo si contestó bien.
     */
    record Resultado(Servicio servicio, boolean disponible, Long milisegundos, String detalle) {}

    /** El estado de cada servicio configurado, en el orden de {@link Servicio}. Un servicio sin URL configurada no aparece. Nunca lanza: un fallo es un resultado. */
    List<Resultado> sondear();
}
