package pe.factura.domain.webhook;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los eventos, la URL y la firma de los webhooks (#19). */
class WebhookDominioTest {
    @Test void soloLosEstadosQueCierranOdetienenElTramiteSePublican() {
        assertThat(EventoWebhook.deEstado(EstadoDocumento.ACEPTADO)).contains("comprobante.aceptado");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.ACEPTADO_CON_OBS)).contains("comprobante.aceptado_con_obs");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.RECHAZADO)).contains("comprobante.rechazado");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.ERROR_ENVIO)).contains("comprobante.error_envio");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.FUERA_DE_PLAZO)).contains("comprobante.fuera_de_plazo");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.ANULADO)).contains("comprobante.anulado");
        assertThat(EventoWebhook.deEstado(EstadoDocumento.DESCARTADO)).contains("comprobante.descartado");
        for (EstadoDocumento paso : List.of(EstadoDocumento.RECIBIDO, EstadoDocumento.FIRMADO, EstadoDocumento.ENVIADO, EstadoDocumento.INVALIDO, EstadoDocumento.PENDIENTE_AGRUPACION))
            assertThat(EventoWebhook.deEstado(paso)).as(paso.name()).isEmpty();
    }

    @Test void normalizarQuitaRepetidosYEspaciosYAceptaAsteriscoSoloComoTodos() {
        assertThat(EventoWebhook.normalizar(List.of(" comprobante.aceptado ", "comprobante.rechazado", "comprobante.aceptado"))).containsExactly("comprobante.aceptado", "comprobante.rechazado");
        assertThat(EventoWebhook.normalizar(List.of("*"))).isEqualTo(EventoWebhook.todos()).hasSize(7);
    }

    @Test void normalizarRechazaListaVaciaEventosInventadosYLaPruebaComoSuscripcion() {
        assertThatThrownBy(() -> EventoWebhook.normalizar(List.of())).isInstanceOf(DomainException.class).hasMessageContaining("al menos un evento");
        assertThatThrownBy(() -> EventoWebhook.normalizar(null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> EventoWebhook.normalizar(List.of("comprobante.inventado"))).hasMessageContaining("comprobante.inventado");
        assertThatThrownBy(() -> EventoWebhook.normalizar(List.of(EventoWebhook.PRUEBA))).hasMessageContaining("no existe");
        assertThatThrownBy(() -> EventoWebhook.normalizar(List.of("*", "comprobante.aceptado"))).hasMessageContaining("no existe");
    }

    @Test void laUrlDebeSerHttpsConServidorYSinCredencialesNiFragmento() {
        assertThat(UrlDeWebhook.validar("  https://mi-sistema.pe/khipu?x=1  ", false)).isEqualTo("https://mi-sistema.pe/khipu?x=1");
        assertThat(UrlDeWebhook.validar("HTTPS://mi-sistema.pe:8443/h", false)).isEqualTo("HTTPS://mi-sistema.pe:8443/h");
        for (String mala : new String[]{null, "", "   ", "http://mi-sistema.pe/h", "ftp://mi-sistema.pe/h", "mi-sistema.pe/h", "https:///h", "https://user:clave@mi-sistema.pe/h", "https://mi-sistema.pe/h#frag", "https://mi sistema.pe/h"})
            assertThatThrownBy(() -> UrlDeWebhook.validar(mala, false)).as("%s", mala).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("WEBHOOK_INVALIDO");
    }

    @Test void httpSoloSePermiteCuandoSeHabilitaParaDesarrollo() {
        assertThat(UrlDeWebhook.validar("http://localhost:9000/h", true)).isEqualTo("http://localhost:9000/h");
        assertThatThrownBy(() -> UrlDeWebhook.validar("http://localhost:9000/h", false)).hasMessageContaining("https://");
    }

    @Test void laUrlTieneUnTopeDeLargo() {
        String larga = "https://mi-sistema.pe/" + "a".repeat(UrlDeWebhook.MAXIMO);
        assertThatThrownBy(() -> UrlDeWebhook.validar(larga, false)).hasMessageContaining("500");
        String justa = "https://mi-sistema.pe/" + "a".repeat(UrlDeWebhook.MAXIMO - "https://mi-sistema.pe/".length());
        assertThat(UrlDeWebhook.validar(justa, false)).hasSize(UrlDeWebhook.MAXIMO);
    }

    /** El vector se puede reproducir con `printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac whsec_prueba`. */
    @Test void laFirmaEsHmacSha256DelInstanteYElCuerpo() {
        String hex = FirmaWebhook.hmacHex("whsec_prueba", 1700000000L, "{\"a\":1}");

        assertThat(hex).isEqualTo("0ca0bb9c916989fb6c1d1eb729a180da30cb078b7cbe551c8f8da0ec452ac9db");
        assertThat(FirmaWebhook.cabecera("whsec_prueba", 1700000000L, "{\"a\":1}")).isEqualTo("t=1700000000,v1=" + hex);
        assertThat(FirmaWebhook.hmacHex("whsec_prueba", 1700000001L, "{\"a\":1}")).as("otro instante").isNotEqualTo(hex);
        assertThat(FirmaWebhook.hmacHex("whsec_prueba", 1700000000L, "{\"a\":2}")).as("otro cuerpo").isNotEqualTo(hex);
        assertThat(FirmaWebhook.hmacHex("otro", 1700000000L, "{\"a\":1}")).as("otro secreto").isNotEqualTo(hex);
    }
}
