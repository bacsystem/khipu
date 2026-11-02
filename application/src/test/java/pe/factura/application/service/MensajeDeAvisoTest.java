package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PlantillasRepository.Guardada;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El texto de los avisos a los clientes (#197): dice qué empresa, qué pasa y qué hacer, sin datos que no aplican. El texto sale de las plantillas (#199): de fábrica, o el que un
 * administrador editó.
 */
class MensajeDeAvisoTest {
    static final LocalDate VENCE = LocalDate.of(2026, 10, 15);

    ConfiguracionFake.Plantillas guardadas = new ConfiguracionFake.Plantillas();
    PlantillasDeCorreo plantillas = new PlantillasDeCorreo(guardadas);

    Texto certificado(MotivoDeAviso m, Integer dias) {
        return MensajeDeAviso.de(plantillas, m, "COMERCIAL ANDINA SAC", "20100066603", VENCE, dias, "https://app.khipu.pe");
    }

    @Test void unCertificadoPorVencerDiceCuandoYQueHacer() {
        Texto m = certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 12);

        assertThat(m.asunto()).isEqualTo("Tu certificado digital de COMERCIAL ANDINA SAC vence en 12 días");
        assertThat(m.cuerpo()).contains("COMERCIAL ANDINA SAC (RUC 20100066603)").contains("vence el 15/10/2026 (en 12 días)").contains("ya no podrá firmar")
                .contains("https://app.khipu.pe").contains("Si ya lo renovaste, ignora este mensaje");
    }

    @Test void elUltimoDiaMananaYHoyTienenSusPalabras() {
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 1).asunto()).endsWith("vence mañana");
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 1).cuerpo()).contains("(mañana)");
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 0).asunto()).endsWith("vence hoy");
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 0).cuerpo()).contains("(hoy)");
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 2).asunto()).endsWith("vence en 2 días");
    }

    @Test void unCertificadoVencidoDiceCuandoVencioYQueNoSePuedeEmitir() {
        Texto m = certificado(MotivoDeAviso.CERTIFICADO_VENCIDO, -3);

        assertThat(m.asunto()).isEqualTo("El certificado digital de COMERCIAL ANDINA SAC venció");
        assertThat(m.cuerpo()).contains("venció el 15/10/2026").contains("no podrás emitir").contains("https://app.khipu.pe");
    }

    @Test void losDiasDeUnVencidoNoSeMencionanEnElTextoDeFabrica() {
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_VENCIDO, -3).cuerpo()).doesNotContain("-3").doesNotContain("en -3 días").doesNotContain("hace 3 días");
    }

    @Test void lasCredencialesSolDicenQueSunatNoLasAcepta() {
        Texto m = MensajeDeAviso.de(plantillas, MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, "COMERCIAL ANDINA SAC", "20100066603", null, null, "https://app.khipu.pe");

        assertThat(m.asunto()).isEqualTo("SUNAT no acepta las credenciales SOL de COMERCIAL ANDINA SAC");
        assertThat(m.cuerpo()).contains("COMERCIAL ANDINA SAC (RUC 20100066603)").contains("usuario o la clave SOL").contains("quedan pendientes").contains("https://app.khipu.pe")
                .contains("Si ya las corregiste, ignora este mensaje");
    }

    @Test void ningunMensajeTraeUnNuloNiUnaPlantillaSinLlenar() {
        for (MotivoDeAviso motivo : MotivoDeAviso.values()) {
            Texto m = motivo.automatico() ? MensajeDeAviso.deBoletas(plantillas, motivo, "X SAC", "20100066603", 3, VENCE, 2, "https://app.khipu.pe")
                    : MensajeDeAviso.de(plantillas, motivo, "X SAC", "20100066603", VENCE, 5, "https://app.khipu.pe");
            assertThat(m.asunto() + m.cuerpo()).as(motivo.name()).doesNotContain("null").doesNotContain("{").doesNotContain("%s");
        }
    }

    // --- las boletas sin informar (#356) --------------------------------------------------------------------------------------------------

    Texto boletas(MotivoDeAviso m, long cuantas, int dias) {
        return MensajeDeAviso.deBoletas(plantillas, m, "BODEGA SAN JUAN SAC", "20100066603", cuantas, VENCE, dias, "https://app.khipu.pe");
    }

    @Test void elAvisoDelQuintoDiaDiceCuantasBoletasHastaCuandoYQueHacer() {
        Texto m = boletas(MotivoDeAviso.BOLETAS_POR_INFORMAR, 3, 2);

        assertThat(m.asunto()).isEqualTo("3 boletas de BODEGA SAN JUAN SAC sin informar a SUNAT");
        assertThat(m.cuerpo()).contains("BODEGA SAN JUAN SAC (RUC 20100066603)").contains("3 boletas sin informar").contains("resumen diario").contains("el 15/10/2026 (en 2 días)")
                .contains("https://app.khipu.pe").contains("Si ya lo enviaste, ignora este mensaje");
    }

    @Test void unaSolaBoletaVaEnSingular() {
        assertThat(boletas(MotivoDeAviso.BOLETAS_POR_INFORMAR, 1, 2).asunto()).startsWith("1 boleta de ").doesNotContain("boletas");
    }

    @Test void elAvisoDelSeptimoDiaDiceQueElPlazoEsHoyYQueSeInformaFueraDePlazo() {
        Texto m = boletas(MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO, 2, 0);

        assertThat(m.asunto()).isEqualTo("2 boletas de BODEGA SAN JUAN SAC siguen sin informarse a SUNAT");
        assertThat(m.cuerpo()).contains("es el 15/10/2026 (hoy)").contains("fuera de plazo").contains("SUNAT puede observarlas");
    }

    @Test void siElPlazoYaPasoDiceHaceCuanto() {
        assertThat(boletas(MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO, 2, -3).cuerpo()).contains("(hace 3 días)");
    }

    @Test void elTextoDeUnaBoletaQueUnAdministradorEditoEsElQueSale() {
        guardadas.filas.put(PlantillaDeCorreo.AVISO_BOLETAS_POR_INFORMAR, new Guardada(new Texto("Faltan {boletas}", "{razon_social}: {boletas} hasta el {fecha} en {enlace}"), Instant.EPOCH));

        Texto m = boletas(MotivoDeAviso.BOLETAS_POR_INFORMAR, 4, 2);

        assertThat(m.asunto()).isEqualTo("Faltan 4 boletas");
        assertThat(m.cuerpo()).isEqualTo("BODEGA SAN JUAN SAC: 4 boletas hasta el 15/10/2026 en https://app.khipu.pe");
    }

    // --- cada motivo, su plantilla; y lo que el administrador editó ---------------------------------------------------------------------

    @Test void cadaMotivoUsaSuPropiaPlantilla() {
        assertThat(MensajeDeAviso.plantilla(MotivoDeAviso.CERTIFICADO_POR_VENCER)).isEqualTo(PlantillaDeCorreo.AVISO_CERTIFICADO_POR_VENCER);
        assertThat(MensajeDeAviso.plantilla(MotivoDeAviso.CERTIFICADO_VENCIDO)).isEqualTo(PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO);
        assertThat(MensajeDeAviso.plantilla(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS)).isEqualTo(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL);
        assertThat(MensajeDeAviso.plantilla(MotivoDeAviso.BOLETAS_POR_INFORMAR)).isEqualTo(PlantillaDeCorreo.AVISO_BOLETAS_POR_INFORMAR);
        assertThat(MensajeDeAviso.plantilla(MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO)).isEqualTo(PlantillaDeCorreo.AVISO_BOLETAS_PLAZO);
    }

    @Test void elTextoQueUnAdministradorEditoEsElQueSale() {
        guardadas.filas.put(PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO,
                new Guardada(new Texto("Urgente: {razon_social}", "{razon_social} ({ruc}) venció el {fecha}, {cuando}. Entra a {enlace}"), Instant.EPOCH));

        Texto m = certificado(MotivoDeAviso.CERTIFICADO_VENCIDO, -3);

        assertThat(m.asunto()).isEqualTo("Urgente: COMERCIAL ANDINA SAC");
        assertThat(m.cuerpo()).isEqualTo("COMERCIAL ANDINA SAC (20100066603) venció el 15/10/2026, hace 3 días. Entra a https://app.khipu.pe");
    }

    @Test void cuandoDiceLoQueLeQuedaOLoQueLlevaVencido() {
        assertThat(MensajeDeAviso.cuando(0)).isEqualTo("hoy");
        assertThat(MensajeDeAviso.cuando(1)).isEqualTo("mañana");
        assertThat(MensajeDeAviso.cuando(2)).isEqualTo("en 2 días");
        assertThat(MensajeDeAviso.cuando(29)).isEqualTo("en 29 días");
        assertThat(MensajeDeAviso.cuando(-1)).isEqualTo("ayer");
        assertThat(MensajeDeAviso.cuando(-2)).isEqualTo("hace 2 días");
        assertThat(MensajeDeAviso.cuando(-30)).isEqualTo("hace 30 días");
    }

    @Test void unAvisoDeSolNoTieneFechaNiCuandoPeroSuTextoEditadoNoNecesitaLosDos() {
        guardadas.filas.put(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, new Guardada(new Texto("SOL de {razon_social}", "Revisa {ruc} en {enlace}"), Instant.EPOCH));

        Texto m = MensajeDeAviso.de(plantillas, MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, "X SAC", "20100066603", null, null, "https://app.khipu.pe");

        assertThat(m.cuerpo()).isEqualTo("Revisa 20100066603 en https://app.khipu.pe");
    }
}
