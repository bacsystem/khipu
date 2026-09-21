package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.UnitOfWork;
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
        for (Comprobante c : comprobantes.pendientesDeEnvioEmitidosHasta(corte)) {
            if (!c.fueraDePlazo(hoy)) continue;
            c.marcarFueraDePlazo(hoy);
            uow.ejecutar(() -> comprobantes.guardar(c));
            vencidos.add(c);
        }
        return vencidos;
    }
}
