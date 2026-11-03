package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.CanalDeAlertas;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** #581: el aviso suelto al operador sale por los canales configurados, y uno caído no corta a los demás ni al que avisa. */
class AvisoAlOperadorPorCanalesTest {
    static class Canal implements CanalDeAlertas {
        final String nombre;
        final boolean configurado;
        final boolean falla;
        final List<String> enviados = new ArrayList<>();
        Canal(String nombre, boolean configurado, boolean falla) { this.nombre = nombre; this.configurado = configurado; this.falla = falla; }
        public String nombre() { return nombre; }
        public boolean configurado() { return configurado; }
        public void enviar(String asunto, String cuerpo) {
            if (falla) throw new IllegalStateException("caído");
            enviados.add(asunto + "|" + cuerpo);
        }
    }

    @Test void saleSoloPorLosCanalesConfigurados() {
        Canal correo = new Canal("correo", true, false);
        Canal webhook = new Canal("webhook", false, false);

        new AvisoAlOperadorPorCanales(List.of(correo, webhook)).avisar("Cuenta nueva pide el plan Negocio", "detalle");

        assertThat(correo.enviados).containsExactly("Cuenta nueva pide el plan Negocio|detalle");
        assertThat(webhook.enviados).isEmpty();
    }

    @Test void unCanalCaidoNoCortaALosDemasNiAlQueAvisa() {
        Canal caido = new Canal("correo", true, true);
        Canal webhook = new Canal("webhook", true, false);

        assertThatCode(() -> new AvisoAlOperadorPorCanales(List.of(caido, webhook)).avisar("asunto", "cuerpo")).doesNotThrowAnyException();
        assertThat(webhook.enviados).containsExactly("asunto|cuerpo");
    }

    @Test void sinCanalesNoFalla() {
        assertThatCode(() -> new AvisoAlOperadorPorCanales(List.of()).avisar("asunto", "cuerpo")).doesNotThrowAnyException();
    }
}
