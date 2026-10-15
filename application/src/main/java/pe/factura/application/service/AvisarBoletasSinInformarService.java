package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AvisarBoletasSinInformarUseCase;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.FilaBoletas;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.documento.PlazoEnvio;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El aviso automático de boletas sin informar a SUNAT (#356). Va a la cuenta dueña de la empresa y dice cuántas son y hasta cuándo se pueden informar. Sale una vez por motivo y por
 * empresa cada {@link MotivoDeAviso#ENFRIAMIENTO}: «desde el 5.º día» y «el plazo de la más antigua vence» son motivos distintos, así que el segundo sale aunque el primero se haya
 * mandado hace dos días. Mismo orden que el aviso de un administrador (AvisarAlClienteService): primero se **reserva** el aviso en una transacción, después sale el correo —fuera
 * de ella, es lento y externo— y si falla, la reserva se anula y la próxima corrida lo reintenta. Sin envío de correos habilitado no se reserva nada: reservarlo lo daría por
 * mandado una semana y el cliente no se enteraría.
 */
@RequiredArgsConstructor
public class AvisarBoletasSinInformarService implements AvisarBoletasSinInformarUseCase {
    private final AvisosRepository avisos;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final Clock clock;
    private final PlantillasDeCorreo plantillas;

    @Override public Resultado avisar(String urlPortal) {
        if (!correo.entregaDeVerdad()) return new Resultado(0, List.of());
        LocalDate hoy = LocalDate.now(clock);
        Instant ahora = clock.instant();
        int enviados = 0;
        List<String> fallos = new ArrayList<>();
        for (FilaBoletas f : avisos.boletasSinInformar(hoy)) {
            if (f.cuenta().email() == null) continue;
            MotivoDeAviso motivo = f.urgentes() > 0 ? MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO : MotivoDeAviso.BOLETAS_POR_INFORMAR;
            AvisoRegistrado aviso = new AvisoRegistrado(UUID.randomUUID(), f.empresaId(), f.cuenta().cuentaId(), motivo, f.cuenta().email(), ahora, null);
            if (uow.ejecutar(() -> avisos.reservar(aviso, ahora.minus(MotivoDeAviso.ENFRIAMIENTO))).isPresent()) continue;

            LocalDate venceEl = f.masAntiguaEmitidaEl().plusDays(PlazoEnvio.DIAS_RESUMEN_DIARIO);
            Texto mensaje = MensajeDeAviso.deBoletas(plantillas, motivo, f.razonSocial(), f.ruc(), f.pendientes(), venceEl, (int) ChronoUnit.DAYS.between(hoy, venceEl), urlPortal);
            try {
                correo.enviar(f.cuenta().email(), mensaje.asunto(), mensaje.cuerpo());
                enviados++;
            } catch (RuntimeException e) {
                uow.ejecutar(() -> avisos.anular(aviso.id()));
                fallos.add(f.ruc() + " (" + f.cuenta().email() + "): " + e.getMessage());
            }
        }
        return new Resultado(enviados, List.copyOf(fallos));
    }
}
