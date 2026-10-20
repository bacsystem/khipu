package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ResumirComprobantesUseCase;
import pe.factura.application.port.out.ResumenDeComprobantesRepository;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Agregados;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Celda;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * El resumen de comprobantes de una empresa (#15) y su emisión día por día (#24). Solo lectura. El repositorio trae cuántos hay por estado; acá se decide qué estados son
 * «emitidos», «aceptados», «facturados» y cada clase de «atención requerida» con las reglas de {@link EstadoDocumento}, una sola vez. «Emitidos», «aceptados» y «facturado»
 * cuentan solo ventas ({@link TipoDocumento#esVenta()}); «en camino» y la atención requerida cuentan todos los tipos, como el listado filtrado por estado.
 */
@RequiredArgsConstructor
public class ResumirComprobantesService implements ResumirComprobantesUseCase {
    private final ResumenDeComprobantesRepository resumenes;
    private final Clock clock;

    @Override public ResumirComprobantesUseCase.Resumen resumir(UUID tenantId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
        Agregados r = resumenes.resumir(tenantId, desde, hasta, LocalDate.now(clock));
        // «Emitidos» y «aceptados» son ventas; lo que está en camino o pide atención cuenta todos los tipos (un 04 rechazado o un 20 con error de envío también tienen plazo).
        return new ResumirComprobantesUseCase.Resumen(desde, hasta, cuantos(r.ventasPorEstado(), EstadoDocumento::fueEmitido), cuantos(r.ventasPorEstado(), EstadoDocumento::esFinalAceptado),
                cuantos(r.porEstado(), EstadoDocumento::estaEnCamino), cuantos(r.porEstado(), e -> e == EstadoDocumento.RECHAZADO), cuantos(r.porEstado(), e -> e == EstadoDocumento.ERROR_ENVIO),
                cuantos(r.porEstado(), e -> e == EstadoDocumento.FUERA_DE_PLAZO), r.boletasSinEnviar(), r.facturado().stream().map(f -> new Total(f.moneda(), f.total())).toList());
    }

    @Override public PorDia porDia(UUID tenantId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new DomainException("PARAMETRO_INVALIDO", "La emisión por día necesita desde y hasta");
        if (desde.isAfter(hasta)) throw new DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > MAXIMO_DIAS_POR_DIA)
            throw new DomainException("RANGO_INVALIDO", "La emisión por día admite hasta " + MAXIMO_DIAS_POR_DIA + " días y se pidieron " + dias + ": acote desde y hasta");

        Map<LocalDate, Acumulado> porFecha = new TreeMap<>();
        for (Celda c : resumenes.porDia(tenantId, desde, hasta)) {
            if (!esVenta(c.tipo())) continue;
            boolean emitido = c.estado().fueEmitido();
            boolean facturado = c.estado().cuentaComoFacturado() && c.moneda() != null && c.neto() != null;
            if (!emitido && !facturado) continue;
            Acumulado a = porFecha.computeIfAbsent(c.fecha(), f -> new Acumulado());
            if (emitido) a.emitidos += c.cuantos();
            if (facturado) a.porMoneda.merge(c.moneda(), c.neto(), BigDecimal::add);
        }
        List<Dia> resultado = new ArrayList<>(porFecha.size());
        porFecha.forEach((fecha, a) -> resultado.add(new Dia(fecha, a.emitidos,
                a.porMoneda.entrySet().stream().map(e -> new Total(e.getKey(), e.getValue())).toList())));
        return new PorDia(desde, hasta, resultado);
    }

    /**
     * Los códigos que son ventas y por eso cuentan en la emisión por día: los de {@link TipoDocumento#esVenta()}, la misma definición con que el resumen (#15) cuenta
     * «emitidos» y «facturado» ({@code TiposDeVenta} en la persistencia), así que 04, 20, 40, 09 y 31 no entran en ninguno de los dos.
     */
    static final Set<String> TIPOS_DE_VENTA = TipoDocumento.ventas().stream().map(TipoDocumento::codigo).collect(Collectors.toUnmodifiableSet());

    /** Un tipo nulo o desconocido no es una venta, no un error. */
    static boolean esVenta(String tipo) { return TipoDocumento.esVenta(tipo); }

    /** Lo de un día mientras se recorren sus celdas; {@code porMoneda} ordenado por moneda, como el resumen. */
    private static final class Acumulado {
        long emitidos;
        final Map<String, BigDecimal> porMoneda = new TreeMap<>();
    }

    private static long cuantos(Map<EstadoDocumento, Long> porEstado, Predicate<EstadoDocumento> cuenta) {
        return porEstado.entrySet().stream().filter(e -> cuenta.test(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }
}
