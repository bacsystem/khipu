package pe.factura.application.service;

import pe.factura.application.port.in.AceptarTerminosUseCase;
import pe.factura.application.port.out.AceptacionDeTerminosRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.time.Clock;
import java.util.UUID;

public class AceptarTerminosService implements AceptarTerminosUseCase {
    private final AceptacionDeTerminosRepository aceptaciones;
    private final UnitOfWork uow;
    private final Clock clock;
    private final String vigente;

    /** {@code vigente}: la de la configuración ({@code app.terminos-version}); una inválida frena el arranque. */
    public AceptarTerminosService(AceptacionDeTerminosRepository aceptaciones, UnitOfWork uow, Clock clock, String vigente) {
        this.aceptaciones = aceptaciones;
        this.uow = uow;
        this.clock = clock;
        this.vigente = AceptacionDeTerminos.validarVersion(vigente);
    }

    @Override public String versionVigente() { return vigente; }

    @Override public boolean pendiente(UUID usuarioId) {
        return aceptaciones.buscar(usuarioId, vigente).isEmpty();
    }

    @Override public AceptacionDeTerminos aceptar(UUID usuarioId) {
        return uow.ejecutar(() -> {
            aceptaciones.registrar(new AceptacionDeTerminos(usuarioId, vigente, clock.instant()));
            // Lo guardado, no lo que se intentó guardar: si ya la había aceptado, la fecha es la de entonces.
            return aceptaciones.buscar(usuarioId, vigente).orElseThrow();
        });
    }
}
