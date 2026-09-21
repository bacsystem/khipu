package pe.factura.application.service;

import pe.factura.application.port.out.EmisorDeSerieRepository;
import pe.factura.application.port.out.EmisorDeSerieRepository.Asignacion;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

/**
 * Resuelve con qué domicilio sale un comprobante: el del establecimiento anexo asignado a su serie, o el fiscal del tenant
 * cuando la serie está en {@code 0000} (o no se conoce, como en los comprobantes anteriores a #80).
 */
final class EmisorDeSerie {
    private EmisorDeSerie() {}

    /** Para emitir: el anexo debe existir y estar activo; si no, se rechaza antes de consumir número. */
    static Tenant paraEmitir(EmisorDeSerieRepository emisorDeSerie, Tenant t, Comprobante c) {
        return emisorDeSerie.buscarAsignacionDeSerie(t.id(), c.tipo(), c.serie()).map(a -> {
            if (a.establecimiento() == null) throw new DomainException("ESTABLECIMIENTO_INVALIDO",
                    "La serie " + c.serie() + " está asignada al establecimiento " + a.codigo() + ", que no existe en la empresa");
            if (!a.establecimiento().activo()) throw new DomainException("ESTABLECIMIENTO_INVALIDO",
                    "La serie " + c.serie() + " está asignada al establecimiento " + a.codigo() + " (" + a.establecimiento().nombre() + "), que está dado de baja");
            return t.conDomicilio(a.establecimiento().domicilio());
        }).orElse(t);
    }

    /** Para la representación impresa de un comprobante ya emitido: usa el anexo aunque esté dado de baja. */
    static Tenant paraImprimir(EmisorDeSerieRepository emisorDeSerie, Tenant t, Comprobante c) {
        return emisorDeSerie.buscarAsignacionDeSerie(t.id(), c.tipo(), c.serie())
                .map(Asignacion::establecimiento)
                .map(e -> e == null ? t : t.conDomicilio(e.domicilio()))
                .orElse(t);
    }
}
