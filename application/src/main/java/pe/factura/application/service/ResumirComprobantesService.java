package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ResumirComprobantesUseCase;
import pe.factura.application.port.out.ResumenDeComprobantesRepository;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Agregados;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * El resumen de comprobantes de una empresa (#15). Solo lectura. El repositorio trae cuántos hay por estado; acá se decide qué estados son «emitidos», «aceptados» y cada
 * clase de «atención requerida» con las reglas de {@link EstadoDocumento}, una sola vez.
 */
@RequiredArgsConstructor
public class ResumirComprobantesService implements ResumirComprobantesUseCase {
    private final ResumenDeComprobantesRepository resumenes;

    @Override public ResumirComprobantesUseCase.Resumen resumir(UUID tenantId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
        Agregados r = resumenes.resumir(tenantId, desde, hasta);
        return new ResumirComprobantesUseCase.Resumen(desde, hasta, cuantos(r.porEstado(), EstadoDocumento::fueEmitido), cuantos(r.porEstado(), EstadoDocumento::esFinalAceptado),
                cuantos(r.porEstado(), e -> e == EstadoDocumento.RECHAZADO), cuantos(r.porEstado(), e -> e == EstadoDocumento.ERROR_ENVIO),
                cuantos(r.porEstado(), e -> e == EstadoDocumento.FUERA_DE_PLAZO), r.facturado().stream().map(f -> new Total(f.moneda(), f.total())).toList());
    }

    private static long cuantos(Map<EstadoDocumento, Long> porEstado, Predicate<EstadoDocumento> cuenta) {
        return porEstado.entrySet().stream().filter(e -> cuenta.test(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }
}
