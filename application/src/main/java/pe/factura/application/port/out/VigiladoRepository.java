package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que lee y escribe el vigilante de la operación (#341) además de lo que ya da {@link MonitorDeEmisionRepository} y {@link AvisosRepository}: los comprobantes con el envío
 * fallando o fuera de plazo, los latidos de procesos externos y el registro de qué alerta está activa para no repetirla en cada pasada.
 */
public interface VigiladoRepository {
    /** Una alerta registrada: {@code desde} cuándo está activa y {@code ultimoEnvio} cuándo se avisó por última vez. */
    record EstadoAlerta(boolean activa, Instant desde, Instant ultimoEnvio) {}

    /** Comprobantes en {@code ERROR_ENVIO} cuyo último cambio fue antes de {@code antesDe}: llevan ese tiempo fallando. */
    long enErrorDeEnvioDesde(Instant antesDe);

    /** Comprobantes que pasaron a {@code FUERA_DE_PLAZO} desde {@code desde} (inclusive): el cliente tiene que volver a emitir. */
    long fueraDePlazoDesde(Instant desde);

    /** Cuándo terminó bien por última vez el proceso externo con esa clave (por ejemplo {@code respaldo}); vacío si nunca avisó. */
    Optional<Instant> ultimoLatido(String clave);

    Map<String, EstadoAlerta> alertas();

    void guardarAlerta(String clave, EstadoAlerta estado);
}
