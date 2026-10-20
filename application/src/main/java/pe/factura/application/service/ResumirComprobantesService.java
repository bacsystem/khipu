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
import java.util.stream.Stream;

/**
 * El resumen de comprobantes de una empresa (#15) y su emisión día por día (#24). Solo lectura. El repositorio trae cuántos hay por estado; acá se decide qué estados son
 * «emitidos», «aceptados», «facturados» y cada clase de «atención requerida» con las reglas de {@link EstadoDocumento}, una sola vez.
 */
@RequiredArgsConstructor
public class ResumirComprobantesService implements ResumirComprobantesUseCase {
    private final ResumenDeComprobantesRepository resumenes;
    private final Clock clock;

    @Override public ResumirComprobantesUseCase.Resumen resumir(UUID tenantId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
        Agregados r = resumenes.resumir(tenantId, desde, hasta, LocalDate.now(clock));
        return new ResumirComprobantesUseCase.Resumen(desde, hasta, cuantos(r.porEstado(), EstadoDocumento::fueEmitido), cuantos(r.porEstado(), EstadoDocumento::esFinalAceptado),
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
     * Los tipos que son ventas y por eso cuentan en la emisión por día: factura, boleta y sus notas. Es un solo predicado a propósito. Otras ramas agregan tipos que no son
     * ventas (04 liquidación de compra, 20 retención, 40 percepción) y el resumen (#15) los excluye de «emitidos» y «facturado»: al ensamblarlas, esta regla y la del resumen
     * tienen que ser la misma. Se nombran las constantes una por una en vez de tomar {@code TipoDocumento.values()}, para que un tipo nuevo no entre solo.
     */
    static final Set<String> TIPOS_DE_VENTA = Stream.of(TipoDocumento.FACTURA, TipoDocumento.BOLETA, TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO)
            .map(TipoDocumento::codigo).collect(Collectors.toUnmodifiableSet());

    /** Un conjunto inmutable lanza con {@code contains(null)}: un tipo nulo no es una venta, no un error. */
    static boolean esVenta(String tipo) { return tipo != null && TIPOS_DE_VENTA.contains(tipo); }

    /** Lo de un día mientras se recorren sus celdas; {@code porMoneda} ordenado por moneda, como el resumen. */
    private static final class Acumulado {
        long emitidos;
        final Map<String, BigDecimal> porMoneda = new TreeMap<>();
    }

    private static long cuantos(Map<EstadoDocumento, Long> porEstado, Predicate<EstadoDocumento> cuenta) {
        return porEstado.entrySet().stream().filter(e -> cuenta.test(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }
}
