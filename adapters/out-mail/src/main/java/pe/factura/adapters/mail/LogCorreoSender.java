package pe.factura.adapters.mail;

import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.util.List;

/** Sin SMTP configurado (desarrollo/self-hosted sin correo): escribe el correo en el log en vez de enviarlo. */
@Slf4j
public class LogCorreoSender implements CorreoSender {
    @Override public void enviar(String para, String asunto, String cuerpoTexto) {
        log.warn("Correo NO enviado (SMTP no configurado) — para={} asunto={}\n{}", para, asunto, cuerpoTexto);
    }
    @Override public void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos) {
        log.warn("Correo NO enviado (SMTP no configurado) — para={} asunto={} adjuntos={}\n{}", para, asunto,
                adjuntos.stream().map(a -> a.nombre() + " (" + a.contenido().length + " bytes)").toList(), cuerpoTexto);
    }
}
