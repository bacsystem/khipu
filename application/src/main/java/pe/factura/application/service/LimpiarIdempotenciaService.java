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

    private final IdempotenciaRepository idempotencia;
    private final Clock clock;

    @Override public int limpiar() { return idempotencia.borrarAnterioresA(Instant.now(clock).minus(VIGENCIA)); }
}
