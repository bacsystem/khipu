package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.LimpiarIdempotenciaUseCase;
import pe.factura.application.port.out.IdempotenciaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class LimpiarIdempotenciaService implements LimpiarIdempotenciaUseCase {
    /** Lo bastante para cualquier reintento tras un corte de red; después, la misma clave vuelve a ser una operación nueva. */
    static final Duration VIGENCIA = Duration.ofHours(24);
    /**
     * Una respuesta guardada puede llevar un secreto (la API key inicial del alta asistida, #219): vive menos que la clave. Pasada la
     * hora, el reintento se reconoce pero ya no recibe el secreto.
     */
    static final Duration VIGENCIA_RESPUESTA = Duration.ofHours(1);

    private final IdempotenciaRepository idempotencia;
    private final Clock clock;

    /** Cuántas claves borró más cuántas respuestas olvidó. */
    @Override public int limpiar() {
        Instant ahora = Instant.now(clock);
        return idempotencia.borrarAnterioresA(ahora.minus(VIGENCIA)) + idempotencia.olvidarRespuestasAnterioresA(ahora.minus(VIGENCIA_RESPUESTA));
    }
}
