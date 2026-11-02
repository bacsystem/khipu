package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarPadronRucUseCase;
import pe.factura.application.port.out.IntentosDeAccesoRepository;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Consulta del padrón reducido (#361). El padrón son los datos de 18 millones de contribuyentes, la mayoría personas naturales: la consulta responde de a un RUC y cada
 * empresa tiene un cupo, para que quien tenga una cuenta no pueda recorrerlo entero. El cupo se cuenta con los mismos contadores del login ({@link IntentosDeAccesoRepository}).
 */
@RequiredArgsConstructor
public class ConsultarPadronRucService implements ConsultarPadronRucUseCase {
    /** Un formulario que consulta al terminar de digitar nunca se acerca a esto; recorrer el padrón sí. */
    static final int MAX_POR_MINUTO = 60;
    static final Duration VENTANA_MINUTO = Duration.ofMinutes(1);
    /** Una empresa con mucho movimiento emite cientos de comprobantes al día, cada uno con un RUC; recorrer los 2,4 millones de RUC 20 llevaría casi dos años. */
    static final int MAX_POR_DIA = 5000;
    static final Duration VENTANA_DIA = Duration.ofDays(1);

    private final PadronRucRepository padron;
    private final IntentosDeAccesoRepository cupo;
    private final Clock clock;

    @Override public Consulta consultar(UUID tenantId, String ruc) {
        Ruc.exigirValido(ruc, "PARAMETRO_INVALIDO", "Consulta de RUC");
        var carga = padron.ultimaCargaExitosa()
                .orElseThrow(() -> new DomainException("PADRON_NO_CARGADO", "El padrón de SUNAT todavía no está cargado: no se puede confirmar si el RUC existe"));
        var ahora = clock.instant();
        // Fuera de cualquier transacción y antes de buscar, como el login: la consulta que el cupo frena no toca el padrón.
        if (!cupo.reservar("padron:min:" + tenantId, MAX_POR_MINUTO, ahora, VENTANA_MINUTO, VENTANA_MINUTO)
                || !cupo.reservar("padron:dia:" + tenantId, MAX_POR_DIA, ahora, VENTANA_DIA, VENTANA_DIA)) {
            throw new DomainException("DEMASIADAS_CONSULTAS", "Se superó el máximo de consultas de RUC. Espera un momento antes de volver a consultar");
        }
        return padron.buscar(ruc)
                .map(c -> new Consulta(c, carga.terminada()))
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "El RUC no figura en el padrón de SUNAT. Puede ser reciente: el padrón se actualiza cada día"));
    }
}
