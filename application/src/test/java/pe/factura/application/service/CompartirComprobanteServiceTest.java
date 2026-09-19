package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompartirComprobanteServiceTest {
    record Correo(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) {}

    private final Fakes.Comprobantes repo = new Fakes.Comprobantes();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Tenants tenants = new Fakes.Tenants();
    private final List<Correo> enviados = new ArrayList<>();
    private final CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) { enviar(para, asunto, cuerpo, List.of()); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviados.add(new Correo(para, asunto, cuerpo, adjuntos)); }
    };
    private final ConsultarComprobanteService consultar = new ConsultarComprobanteService(repo, tenants, storage, (c, t, qr) -> "%PDF".getBytes());
    private final CompartirComprobanteService service = new CompartirComprobanteService(consultar, tenants, correo);
    private final UUID tenant = UUID.randomUUID();

    private Comprobante aceptada() {
        tenants.guardar(Fakes.tenantListo(tenant));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        c.marcarEnviado();
        storage.guardar("cdr/" + c.id(), new byte[]{0x50, 0x4b});
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + c.id());
        repo.guardar(c);
        return c;
    }

    @Test void adjuntaPdfXmlYCdrConElMensajeDelEmisor() {
        Comprobante c = aceptada();

        service.enviarPorCorreo(tenant, c.id(), "cliente@example.com", "Gracias por su compra.");

        assertThat(enviados).hasSize(1);
        Correo e = enviados.get(0);
        assertThat(e.para()).isEqualTo("cliente@example.com");
        assertThat(e.asunto()).isEqualTo("Factura F001-" + c.numero() + " - EMPRESA SAC");
        assertThat(e.cuerpo()).startsWith("Gracias por su compra.\n\n").contains("RUC 20100066603").contains("PEN 118.00");
        assertThat(e.adjuntos()).extracting(Adjunto::nombre).containsExactly(c.nombreArchivo() + ".pdf", c.nombreArchivo() + ".xml", "R-" + c.nombreArchivo() + ".zip");
        assertThat(e.adjuntos()).extracting(Adjunto::tipoContenido).containsExactly("application/pdf", "application/xml", "application/zip");
        assertThat(new String(e.adjuntos().get(0).contenido())).isEqualTo("%PDF");
    }

    @Test void unFalloDelCorreoSeReportaComoEntregaFallida() {
        Comprobante c = aceptada();
        CorreoSender roto = new CorreoSender() {
            public void enviar(String para, String asunto, String cuerpo) { throw new IllegalStateException("SMTP caído"); }
            public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new IllegalStateException("SMTP caído"); }
        };
        assertThatThrownBy(() -> new CompartirComprobanteService(consultar, tenants, roto).enviarPorCorreo(tenant, c.id(), "cliente@example.com", null))
                .isInstanceOf(DomainException.class).hasMessageContaining("cliente@example.com").hasMessageContaining("SMTP caído")
                .extracting("codigo").isEqualTo("CORREO_NO_ENVIADO");
    }

    @Test void soloSeEnvianComprobantesAceptados() {
        tenants.guardar(Fakes.tenantListo(tenant));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);
        assertThatThrownBy(() -> service.enviarPorCorreo(tenant, c.id(), "cliente@example.com", null))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ACEPTADO");
        assertThat(enviados).isEmpty();
    }
}
