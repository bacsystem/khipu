package pe.factura.application.port.in;

import java.util.List;

/**
 * El vigilante de la operación (#341): revisa lo que hoy falla en silencio —envíos a SUNAT atascados, comprobantes fuera de plazo, resúmenes sin respuesta, el respaldo diario,
 * el correo, los certificados— y avisa al operador de la plataforma, una vez por problema y no en cada pasada.
 */
public interface VigilarOperacionUseCase {
    /** {@code avisadas}: alertas nuevas o repetidas que salieron; {@code resueltas}: problemas que dejaron de existir y se avisó; {@code fallos}: qué no se pudo avisar (se reintenta). */
    record Resultado(int avisadas, int resueltas, List<String> fallos) {}

    Resultado vigilar();
}
