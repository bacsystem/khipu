package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.GuiaPdfGenerator;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.GuiaRemision;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.factura.application.service.FakesDeGuia.*;

/** #39: la consulta de la guía y de sus archivos; el PDF se guarda y se regenera cuando llega el QR del CDR. */
class ConsultarGuiaRemisionServiceTest {
    UUID tenant = UUID.randomUUID();
    Guias guias = new Guias();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    final List<String> qrs = new ArrayList<>();
    GuiaPdfGenerator pdf = (g, t, qr, logo) -> { qrs.add(qr); return ("PDF " + g.nombreArchivo() + " " + qr).getBytes(); };
    ConsultarGuiaRemisionService service = new ConsultarGuiaRemisionService(guias, tenants, storage, pdf);

    @Test void elPdfSeGuardaYSeRegeneraConElQrDelCdr() {
        tenants.guardar(tenantGre(tenant));
        GuiaRemision g = firmada(tenant, storage, guias);
        assertThat(new String(service.pdf(tenant, g.id()))).isEqualTo("PDF 20100066603-09-T001-1 null");
        service.pdf(tenant, g.id());
        assertThat(qrs).as("la segunda vez sale del storage").hasSize(1);

        GuiaRemision enviada = guias.buscar(tenant, g.id()).orElseThrow();
        enviada.marcarEnviada("t", null);
        enviada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "r.zip", "https://e-factura.sunat.gob.pe/qr");
        guias.guardar(enviada);
        assertThat(new String(service.pdf(tenant, g.id()))).endsWith("https://e-factura.sunat.gob.pe/qr");
        assertThat(qrs).containsExactly(null, "https://e-factura.sunat.gob.pe/qr");
    }

    @Test void sinCdrNoHayCdrYDeOtraEmpresaNoExiste() {
        tenants.guardar(tenantGre(tenant));
        GuiaRemision g = firmada(tenant, storage, guias);
        assertThatThrownBy(() -> service.cdr(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "SIN_CDR");
        assertThatThrownBy(() -> service.obtener(UUID.randomUUID(), g.id())).hasFieldOrPropertyWithValue("codigo", "NO_ENCONTRADO");
        assertThat(service.xml(tenant, g.id())).isEqualTo("<DespatchAdvice/>".getBytes());
        assertThat(service.contar(tenant, null)).isEqualTo(1);
    }
}
