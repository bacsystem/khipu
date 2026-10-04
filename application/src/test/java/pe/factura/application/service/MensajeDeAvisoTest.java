package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** El texto de los avisos a los clientes (#197): dice qué empresa, qué pasa y qué hacer, sin datos que no aplican. Cuando #199 haga editables las plantillas, cambia de dónde sale. */
class MensajeDeAvisoTest {
    static final LocalDate VENCE = LocalDate.of(2026, 10, 15);

    MensajeDeAviso certificado(MotivoDeAviso m, Integer dias) {
        return MensajeDeAviso.de(m, "COMERCIAL ANDINA SAC", "20100066603", VENCE, dias, "https://app.khipu.pe");
    }

    @Test void unCertificadoPorVencerDiceCuandoYQueHacer() {
        MensajeDeAviso m = certificado(MotivoDeAviso.CERTIFICADO_POR_VENCER, 12);

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
        MensajeDeAviso m = certificado(MotivoDeAviso.CERTIFICADO_VENCIDO, -3);

        assertThat(m.asunto()).isEqualTo("El certificado digital de COMERCIAL ANDINA SAC venció");
        assertThat(m.cuerpo()).contains("venció el 15/10/2026").contains("no podrás emitir").contains("https://app.khipu.pe");
    }

    @Test void losDiasDeUnVencidoNoSeMencionan() {
        assertThat(certificado(MotivoDeAviso.CERTIFICADO_VENCIDO, -3).cuerpo()).doesNotContain("-3").doesNotContain("en -3 días");
    }

    @Test void lasCredencialesSolDicenQueSunatNoLasAcepta() {
        MensajeDeAviso m = MensajeDeAviso.de(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, "COMERCIAL ANDINA SAC", "20100066603", null, null, "https://app.khipu.pe");

        assertThat(m.asunto()).isEqualTo("SUNAT no acepta las credenciales SOL de COMERCIAL ANDINA SAC");
        assertThat(m.cuerpo()).contains("COMERCIAL ANDINA SAC (RUC 20100066603)").contains("usuario o la clave SOL").contains("quedan pendientes").contains("https://app.khipu.pe")
                .contains("Si ya las corregiste, ignora este mensaje");
    }

    @Test void ningunMensajeTraeUnNuloNiUnaPlantillaSinLlenar() {
        for (MotivoDeAviso motivo : MotivoDeAviso.values()) {
            MensajeDeAviso m = MensajeDeAviso.de(motivo, "X SAC", "20100066603", VENCE, 5, "https://app.khipu.pe");
            assertThat(m.asunto() + m.cuerpo()).as(motivo.name()).doesNotContain("null").doesNotContain("{").doesNotContain("%s");
        }
    }
}
