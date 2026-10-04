package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BannerDeMantenimientoTest {
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final Instant DESDE = AHORA.plus(Duration.ofHours(1));
    static final Instant HASTA = AHORA.plus(Duration.ofHours(5));

    private static void rechaza(String texto, Instant desde, Instant hasta, String mensaje) {
        assertThatThrownBy(() -> BannerDeMantenimiento.de(texto, desde, hasta, AHORA)).isInstanceOf(DomainException.class).hasMessageContaining(mensaje).extracting("codigo").isEqualTo("BANNER_INVALIDO");
    }

    @Test void unBannerValidoSeGuardaConSuTextoRecortado() {
        BannerDeMantenimiento b = BannerDeMantenimiento.de("  Mantenimiento esta noche  ", DESDE, HASTA, AHORA);

        assertThat(b).isEqualTo(new BannerDeMantenimiento("Mantenimiento esta noche", DESDE, HASTA));
    }

    @Test void puedeEmpezarAhoraMismoOYaHaberEmpezado() {
        assertThat(BannerDeMantenimiento.de("x", AHORA, HASTA, AHORA)).isNotNull();
        assertThat(BannerDeMantenimiento.de("x", AHORA.minus(Duration.ofHours(2)), HASTA, AHORA)).isNotNull();
    }

    @Test void elTextoEsObligatorio() {
        rechaza(null, DESDE, HASTA, "texto del aviso no puede estar vacío");
        rechaza("   ", DESDE, HASTA, "texto del aviso no puede estar vacío");
    }

    @Test void elTextoTieneUnTopeContadoDespuesDeRecortar() {
        assertThat(BannerDeMantenimiento.de(" " + "a".repeat(BannerDeMantenimiento.MAX_TEXTO) + " ", DESDE, HASTA, AHORA).texto()).hasSize(BannerDeMantenimiento.MAX_TEXTO);
        rechaza("a".repeat(BannerDeMantenimiento.MAX_TEXTO + 1), DESDE, HASTA, "hasta 300 caracteres");
    }

    @Test void elTextoVaEnUnaSolaLinea() {
        rechaza("Primera\nSegunda", DESDE, HASTA, "una sola línea");
        rechaza("Con\ttab", DESDE, HASTA, "una sola línea");
    }

    @Test void lasDosFechasSonObligatorias() {
        rechaza("x", null, HASTA, "desde cuándo y hasta cuándo");
        rechaza("x", DESDE, null, "desde cuándo y hasta cuándo");
    }

    @Test void tieneQueTerminarDespuesDeEmpezar() {
        rechaza("x", HASTA, DESDE, "terminar después de empezar");
        rechaza("x", DESDE, DESDE, "terminar después de empezar");
    }

    @Test void unBannerQueYaVencioNoSePublica() {
        rechaza("x", AHORA.minus(Duration.ofHours(3)), AHORA.minus(Duration.ofHours(1)), "ya venció");
        rechaza("x", AHORA.minus(Duration.ofHours(3)), AHORA, "ya venció");
        assertThat(BannerDeMantenimiento.de("x", AHORA.minus(Duration.ofHours(3)), AHORA.plusSeconds(1), AHORA)).isNotNull();
    }

    @Test void nuncaDuraMasDeNoventaDias() {
        assertThat(BannerDeMantenimiento.de("x", AHORA, AHORA.plus(Duration.ofDays(90)), AHORA)).isNotNull();
        rechaza("x", AHORA, AHORA.plus(Duration.ofDays(90)).plusSeconds(1), "hasta 90 días");
    }

    @Test void seMuestraDesdeElInicioInclusiveHastaElFinExclusive() {
        BannerDeMantenimiento b = new BannerDeMantenimiento("x", DESDE, HASTA);

        assertThat(b.vigenteEn(DESDE.minusSeconds(1))).isFalse();
        assertThat(b.vigenteEn(DESDE)).isTrue();
        assertThat(b.vigenteEn(HASTA.minusSeconds(1))).isTrue();
        assertThat(b.vigenteEn(HASTA)).isFalse();
        assertThat(b.vigenteEn(HASTA.plusSeconds(1))).isFalse();
    }
}
