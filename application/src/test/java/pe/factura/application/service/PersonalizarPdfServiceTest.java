package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.PlantillaPdf;
import pe.factura.domain.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalizarPdfServiceTest {
    record Render(Comprobante c, Tenant t, String qr, byte[] logo) {}

    final UUID tenantId = UUID.randomUUID();
    final Fakes.Tenants tenants = new Fakes.Tenants();
    final Fakes.Storage storage = new Fakes.Storage();
    final List<Render> renders = new ArrayList<>();
    /** Como el generador real: falla si el logo no decodifica (aquí, si termina en 0xFF). */
    final PersonalizarPdfService service = new PersonalizarPdfService(tenants, storage, (c, t, qr, logo) -> {
        if (logo != null && logo[logo.length - 1] == (byte) 0xFF) throw new IllegalStateException("No se pudo decodificar el logo");
        renders.add(new Render(c, t, qr, logo)); return "%PDF".getBytes();
    }, Fakes.CLOCK);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    static final byte[] PNG_2 = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 1};
    static final byte[] PNG_ROTO = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, (byte) 0xFF};

    @BeforeEach void setUp() { tenants.guardar(Fakes.tenantListo(tenantId)); }

    @Test void actualizarConservaElLogoYElLogoSeGestionaAparte() {
        PersonalizacionPdf conLogo = service.cargarLogo(tenantId, PNG);
        assertThat(conLogo.logoKey()).startsWith(tenantId + "/logo-").endsWith(".png");
        assertThat(service.logo(tenantId)).isEqualTo(PNG);

        PersonalizacionPdf p = service.actualizar(tenantId, new PersonalizacionPdf(PlantillaPdf.MODERNO, "#C8552B", "ignorado", "Gracias", "Obs"));
        assertThat(p.plantilla()).isEqualTo(PlantillaPdf.MODERNO);
        assertThat(p.logoKey()).isEqualTo(conLogo.logoKey());
        assertThat(tenants.buscar(tenantId).orElseThrow().personalizacionPdf()).isEqualTo(p);

        assertThat(service.borrarLogo(tenantId).tieneLogo()).isFalse();
        assertThat(storage.datos).doesNotContainKey(conLogo.logoKey());
        assertThatThrownBy(() -> service.logo(tenantId)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.cargarLogo(tenantId, "<svg/>".getBytes())).extracting("codigo").isEqualTo("LOGO_INVALIDO");
    }

    @Test void reemplazarElLogoCambiaLaClaveYLaHuellaYBorraElAnterior() {
        String primera = service.cargarLogo(tenantId, PNG).logoKey();
        String huella1 = tenants.buscar(tenantId).orElseThrow().personalizacionPdf().huella();
        String segunda = service.cargarLogo(tenantId, PNG_2).logoKey();
        assertThat(segunda).isNotEqualTo(primera);
        assertThat(tenants.buscar(tenantId).orElseThrow().personalizacionPdf().huella()).isNotEqualTo(huella1);
        assertThat(storage.datos).containsKey(segunda).doesNotContainKey(primera);
        // El mismo contenido dos veces: misma clave, nada que borrar.
        assertThat(service.cargarLogo(tenantId, PNG_2).logoKey()).isEqualTo(segunda);
        assertThat(storage.datos).containsKey(segunda);
    }

    @Test void unLogoQueNoDecodificaSeRechazaAlSubirYNoTocaNada() {
        service.cargarLogo(tenantId, PNG);
        assertThatThrownBy(() -> service.cargarLogo(tenantId, PNG_ROTO)).isInstanceOf(DomainException.class)
                .hasMessageContaining("no se pudo decodificar").extracting("codigo").isEqualTo("LOGO_INVALIDO");
        assertThat(service.logo(tenantId)).isEqualTo(PNG);
        assertThat(storage.datos.keySet().stream().filter(k -> k.contains("/logo-"))).hasSize(1);
    }

    @Test void laVistaPreviaUsaElDiseñoIndicadoSinGuardarloYConElLogoActual() {
        service.cargarLogo(tenantId, PNG);
        byte[] pdf = service.vistaPrevia(tenantId, new PersonalizacionPdf(PlantillaPdf.GRIS, "#333333", null, "Pie de prueba", null));

        assertThat(pdf).isEqualTo("%PDF".getBytes());
        Render r = renders.get(renders.size() - 1);   // el primer render es la validación del logo al subirlo
        assertThat(r.t().personalizacionPdf().plantilla()).isEqualTo(PlantillaPdf.GRIS);
        assertThat(r.t().personalizacionPdf().pieDePagina()).isEqualTo("Pie de prueba");
        assertThat(r.logo()).isEqualTo(PNG);
        assertThat(r.c().numero()).isEqualTo(123L);
        assertThat(r.c().hash()).isNotBlank();
        assertThat(r.c().detraccion()).isNotNull();
        assertThat(r.c().formaPago().cuotas()).hasSize(2);
        assertThat(r.c().observaciones()).isNotBlank();
        assertThat(r.qr()).startsWith("20100066603|01|F001|123|");
        // No se persiste: la empresa sigue con su diseño (clásico).
        assertThat(tenants.buscar(tenantId).orElseThrow().personalizacionPdf().plantilla()).isEqualTo(PlantillaPdf.CLASICO);
    }
}
