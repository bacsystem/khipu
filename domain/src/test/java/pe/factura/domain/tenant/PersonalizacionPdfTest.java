package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalizacionPdfTest {
    @Test void porDefectoEsLaClasicaSinLogoNiTextos() {
        PersonalizacionPdf p = PersonalizacionPdf.porDefecto();
        assertThat(p.plantilla()).isEqualTo(PlantillaPdf.CLASICO);
        assertThat(p.colorPrimario()).isEqualTo(PersonalizacionPdf.COLOR_POR_DEFECTO);
        assertThat(p.tieneLogo()).isFalse();
        assertThat(p.pieDePagina()).isNull();
        assertThat(p.observacionesPorDefecto()).isNull();
        assertThat(new Tenant(java.util.UUID.randomUUID(), "20100066603", "X", Entorno.BETA, null, null).personalizacionPdf()).isEqualTo(p);
    }

    @Test void normalizaColorYTextos() {
        PersonalizacionPdf p = new PersonalizacionPdf(PlantillaPdf.MODERNO, " #1f5f4a ", null, "  Gracias por su preferencia  ", "Línea 1\nLínea 2");
        assertThat(p.colorPrimario()).isEqualTo("#1F5F4A");
        assertThat(p.pieDePagina()).isEqualTo("Gracias por su preferencia");
        assertThat(p.observacionesPorDefecto()).isEqualTo("Línea 1\nLínea 2");
        assertThat(PlantillaPdf.porNombre("corporativo")).isEqualTo(PlantillaPdf.CORPORATIVO);
        assertThat(PlantillaPdf.porNombre(null)).isEqualTo(PlantillaPdf.CLASICO);
    }

    @Test void rechazaColorTextosYPlantillaInvalidos() {
        assertThatThrownBy(() -> new PersonalizacionPdf(null, "azul", null, null, null)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PERSONALIZACION_INVALIDA");
        assertThatThrownBy(() -> new PersonalizacionPdf(null, null, null, "a".repeat(301), null)).hasMessageContaining("300");
        assertThatThrownBy(() -> new PersonalizacionPdf(null, null, null, "con\nsalto", null)).hasMessageContaining("una sola línea");
        assertThatThrownBy(() -> new PersonalizacionPdf(null, null, null, null, "tab\tno")).hasMessageContaining("1000");
        assertThatThrownBy(() -> PlantillaPdf.porNombre("neon")).hasMessageContaining("neon");
    }

    @Test void elLogoSeGestionaAparteYLaHuellaCambiaConElDiseño() {
        PersonalizacionPdf conLogo = PersonalizacionPdf.porDefecto().conLogo("t/logo.png");
        PersonalizacionPdf nueva = conLogo.conDiseñoDe(new PersonalizacionPdf(PlantillaPdf.GRIS, null, "otro.png", "Pie", null));
        assertThat(nueva.logoKey()).isEqualTo("t/logo.png");
        assertThat(nueva.plantilla()).isEqualTo(PlantillaPdf.GRIS);
        assertThat(nueva.huella()).isNotEqualTo(conLogo.huella()).hasSize(8);
        assertThat(nueva.huella()).isEqualTo(nueva.conDiseñoDe(nueva).huella());
        assertThat(conLogo.sinLogo().tieneLogo()).isFalse();
        // Estable entre ejecuciones: no depende de Enum.hashCode (identidad), sino del nombre.
        assertThat(new PersonalizacionPdf(PlantillaPdf.GRIS, "#333333", null, null, null).huella()).isEqualTo("c95e0191");
    }

    @Test void laClaveDelLogoLlevaElHashDelContenido() {
        java.util.UUID t = java.util.UUID.randomUUID();
        byte[] png1 = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 1};
        byte[] png2 = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 2};
        assertThat(LogoPdf.clave(t, png1)).startsWith(t + "/logo-").endsWith(".png").isEqualTo(LogoPdf.clave(t, png1));
        assertThat(LogoPdf.clave(t, png2)).isNotEqualTo(LogoPdf.clave(t, png1));
        assertThat(PersonalizacionPdf.porDefecto().conLogo(LogoPdf.clave(t, png1)).huella()).isNotEqualTo(PersonalizacionPdf.porDefecto().conLogo(LogoPdf.clave(t, png2)).huella());
    }

    @Test void elLogoSeReconocePorSusBytesYTieneTope() {
        assertThat(LogoPdf.extension(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0})).isEqualTo("png");
        assertThat(LogoPdf.extension(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0})).isEqualTo("jpg");
        assertThat(LogoPdf.tipoContenido("png")).isEqualTo("image/png");
        assertThatThrownBy(() -> LogoPdf.extension("<svg/>".getBytes())).isInstanceOf(DomainException.class).hasMessageContaining("PNG o JPEG");
        byte[] grande = new byte[LogoPdf.MAX_BYTES + 1]; grande[0] = (byte) 0x89; grande[1] = 'P'; grande[2] = 'N'; grande[3] = 'G';
        assertThatThrownBy(() -> LogoPdf.extension(grande)).hasMessageContaining("200 KB");
        assertThatThrownBy(() -> LogoPdf.extension(new byte[0])).hasMessageContaining("vacío");
    }
}
