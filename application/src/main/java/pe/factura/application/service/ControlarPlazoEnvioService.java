package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.PlazoEnvio;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Barrido periódico del plazo de envío (#37): lo que sigue FIRMADO (emitido con {@code enviar_automatico: false} y nunca
 * enviado) o en ERROR_ENVIO al vencer el plazo pasa a FUERA_DE_PLAZO. Los envíos individuales ya lo comprueban al
 * intentar; este barrido cubre lo que nadie intenta enviar y deja el estado listo para el portal y los webhooks.
 * <p>
 * Una boleta, o una nota B que va en el resumen diario, ya no se cierra (#331): pasado el 7.º día SUNAT la recibe igual en un resumen (la norma prevé que la observe
 * con la 4443; e-beta, con 8 y 30 días de atraso, la aceptó sin observaciones), así que sigue enviable. Las boletas que ya están FUERA_DE_PLAZO (cerradas antes de
 * #331) no se rescatan: el estado es terminal y reabrirlas no se ha decidido.
 */
@RequiredArgsConstructor
public class ControlarPlazoEnvioService implements ControlarPlazoEnvioUseCase {
    private final ComprobanteRepository comprobantes;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override
    public List<Comprobante> marcarVencidos() {
        LocalDate hoy = LocalDate.now(clock);
        // Corte grueso en SQL con el plazo más corto entre todos los tipos; la regla exacta por tipo la decide el dominio.
        LocalDate corte = hoy.minusDays(PlazoEnvio.diasMinimo() + 1);
        List<Comprobante> vencidos = new ArrayList<>();
        for (Comprobante c : comprobantes.pendientesQueVencenEmitidosHasta(corte)) {
            if (!c.fueraDePlazo(hoy)) continue;
            c.marcarFueraDePlazo(hoy);
            try {
                uow.ejecutar(() -> comprobantes.guardar(c));
            } catch (DomainException e) {
                // LOG-01: entre la lectura y el guardado otro proceso lo resolvió (un envío que lo reservó y SUNAT aceptó, un CDR recuperado): el guardado condicional no lo
                // pisa, y un comprobante así no corta el barrido de los demás.
                if (!"ESTADO_CONFLICTO".equals(e.codigo())) throw e;
                continue;
            }
            vencidos.add(c);
        }
        return vencidos;
    }
}
