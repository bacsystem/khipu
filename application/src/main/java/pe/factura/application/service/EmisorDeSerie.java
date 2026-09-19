package pe.factura.application.service;

import pe.factura.application.port.out.EstablecimientoRepository;
import pe.factura.application.port.out.SerieRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

/**
 * Resuelve con qué domicilio sale un comprobante: el del establecimiento anexo asignado a su serie, o el fiscal del tenant
 * cuando la serie está en {@code 0000} (o no se conoce, como en los comprobantes anteriores a #80).
 */
final class EmisorDeSerie {
    private EmisorDeSerie() {}

    /** Para emitir: el anexo debe existir y estar activo; si no, se rechaza antes de consumir número. */
    static Tenant paraEmitir(SerieRepository series, EstablecimientoRepository establecimientos, Tenant t, Comprobante c) {
        String codigo = codigoDeSerie(series, t, c);
        if (codigo == null) return t;
        Establecimiento e = establecimientos.buscar(t.id(), codigo).orElseThrow(() -> new DomainException("ESTABLECIMIENTO_INVALIDO",
                "La serie " + c.serie() + " está asignada al establecimiento " + codigo + ", que no existe en la empresa"));
        if (!e.activo()) throw new DomainException("ESTABLECIMIENTO_INVALIDO",
                "La serie " + c.serie() + " está asignada al establecimiento " + codigo + " (" + e.nombre() + "), que está dado de baja");
        return t.conDomicilio(e.domicilio());
    }

    /** Para la representación impresa de un comprobante ya emitido: usa el anexo aunque esté dado de baja. */
    static Tenant paraImprimir(SerieRepository series, EstablecimientoRepository establecimientos, Tenant t, Comprobante c) {
        String codigo = codigoDeSerie(series, t, c);
        if (codigo == null) return t;
        return establecimientos.buscar(t.id(), codigo).map(e -> t.conDomicilio(e.domicilio())).orElse(t);
    }

    private static String codigoDeSerie(SerieRepository series, Tenant t, Comprobante c) {
        return series.buscar(t.id(), c.tipo(), c.serie()).filter(s -> !s.enDomicilioFiscal()).map(Serie::establecimiento).orElse(null);
    }
}
