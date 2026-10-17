package pe.factura.application.service;

import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.ValidarCompradorUseCase;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Receptor;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Las reglas de SUNAT sobre el RUC del comprador (1083, 4013, 4014), contra el padrón reducido de #361. El padrón se renueva cada día y un RUC recién inscrito puede no estar
 * todavía; por eso solo se confía en él mientras sea reciente: si nunca se cargó o su última carga pasó de {@code maximoDeAtraso}, no se rechaza nada y queda un aviso en el log.
 */
@Slf4j
public class ValidarCompradorEnPadron implements ValidarCompradorUseCase {
    private final PadronRucRepository padron;
    private final Clock clock;
    private final Duration maximoDeAtraso;

    public ValidarCompradorEnPadron(PadronRucRepository padron, Clock clock, Duration maximoDeAtraso) {
        this.padron = padron;
        this.clock = clock;
        this.maximoDeAtraso = maximoDeAtraso;
    }

    @Override public void exigirQueExista(Receptor receptor) {
        if (!conRuc(receptor) || !padronConfiable(true)) return;
        if (padron.buscar(receptor.numDoc()).isEmpty())
            throw new DomainException("RECEPTOR_INVALIDO", "1083 - El RUC " + receptor.numDoc() + " del comprador no figura en el padrón de SUNAT y SUNAT rechazaría el comprobante. "
                    + "Si es un RUC recién inscrito, el padrón se actualiza cada día: reintente más tarde");
    }

    @Override public List<Aviso> avisosDe(Receptor receptor) {
        if (!conRuc(receptor) || !padronConfiable(false)) return List.of();
        Optional<Contribuyente> contribuyente = padron.buscar(receptor.numDoc());
        if (contribuyente.isEmpty()) return List.of();
        Contribuyente c = contribuyente.get();
        return Stream.of(
                c.activo() ? null : new Aviso("4013", "El RUC " + c.ruc() + " del comprador no está activo en SUNAT (" + c.estado() + "): SUNAT aceptará el comprobante con una observación"),
                c.noHabido() ? new Aviso("4014", "El RUC " + c.ruc() + " del comprador figura como no habido en SUNAT: SUNAT aceptará el comprobante con una observación") : null)
                .filter(a -> a != null).toList();
    }

    private static boolean conRuc(Receptor receptor) { return receptor != null && receptor.esRuc(); }

    /** {@code registrar}: dejar constancia en el log; la consulta de avisos corre después de la validación y no repite el mensaje. */
    private boolean padronConfiable(boolean registrar) {
        var carga = padron.ultimaCargaExitosa();
        if (carga.isEmpty()) {
            if (registrar) log.warn("El padrón de RUC no está cargado: no se valida el RUC del comprador (1083). Ver app.padron-ruc.habilitado");
            return false;
        }
        Duration atraso = Duration.between(carga.get().terminada(), clock.instant());
        if (atraso.compareTo(maximoDeAtraso) > 0) {
            if (registrar) log.warn("El padrón de RUC se cargó por última vez hace {} días, más de los {} que se admiten: no se valida el RUC del comprador (1083)", atraso.toDays(), maximoDeAtraso.toDays());
            return false;
        }
        return true;
    }
}
