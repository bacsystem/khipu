package pe.factura.adapters.alertas;

import pe.factura.application.port.out.CanalDeAlertas;
import pe.factura.application.port.out.CorreoSender;

import java.util.Arrays;
import java.util.List;

/**
 * Alertas al operador por correo (#341): un mensaje a cada dirección de {@code ALERTAS_EMAIL} (separadas por coma) por el mismo {@link CorreoSender} del resto de la plataforma.
 * Si el correo no entrega de verdad (sin SMTP, {@code MAIL_HABILITADO=false}) el canal queda sin configurar: una alerta escrita en el log del correo no le llega a nadie.
 */
public class CorreoDeAlertas implements CanalDeAlertas {
    private final CorreoSender correo;
    private final List<String> destinatarios;

    public CorreoDeAlertas(CorreoSender correo, String direcciones) {
        this.correo = correo;
        this.destinatarios = direcciones == null ? List.of() : Arrays.stream(direcciones.split(",")).map(String::strip).filter(d -> !d.isEmpty()).toList();
    }

    @Override public String nombre() { return "correo"; }

    @Override public boolean configurado() { return !destinatarios.isEmpty() && correo.entregaDeVerdad(); }

    /** Intenta con todos; si alguno falla lanza al final, y el vigilante cuenta el aviso como entregado si al menos otro canal lo entregó. */
    @Override public void enviar(String asunto, String cuerpo) {
        RuntimeException fallo = null;
        int entregados = 0;
        for (String para : destinatarios) {
            try {
                correo.enviar(para, "[khipu] " + asunto, cuerpo);
                entregados++;
            } catch (RuntimeException e) {
                fallo = e;
            }
        }
        if (entregados == 0 && fallo != null) throw fallo;
    }
}
