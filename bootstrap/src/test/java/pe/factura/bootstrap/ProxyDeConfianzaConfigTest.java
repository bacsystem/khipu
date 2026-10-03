package pe.factura.bootstrap;

import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qué proxies son de confianza para leer la IP del cliente (#208). Sin configurar no se instala nada: Tomcat deja la IP
 * de la conexión y una cabecera {@code X-Forwarded-For} falsificada no cambia lo que se registra en la bitácora.
 */
class ProxyDeConfianzaConfigTest {
    private static List<Valve> valvulas(String proxies) {
        var fabrica = new TomcatServletWebServerFactory();
        new ProxyDeConfianzaConfig().proxiesDeConfianza(proxies).customize(fabrica);
        return List.copyOf(fabrica.getEngineValves());
    }

    @Test void sinProxiesConfiguradosNoSeInstalaNingunaValvula() {
        assertThat(valvulas("")).isEmpty();
        assertThat(valvulas("   ")).isEmpty();
        assertThat(valvulas(null)).isEmpty();
    }

    @Test void conProxiesConfiguradosSoloEsosSonDeConfianza() {
        List<Valve> v = valvulas("100\\.64\\.\\d+\\.\\d+");

        assertThat(v).hasSize(1).first().isInstanceOf(RemoteIpValve.class);
        RemoteIpValve valvula = (RemoteIpValve) v.get(0);
        // Se fija explícitamente: el valor por defecto de Tomcat da por buenos todos los rangos privados (10/8, 172.16/12, 192.168/16…).
        assertThat(valvula.getInternalProxies()).isEqualTo("100\\.64\\.\\d+\\.\\d+");
        assertThat(valvula.getTrustedProxies()).isNull();
    }

    @Test void soloLeeLaIpDelClienteNoElEsquemaNiElHost() {
        RemoteIpValve valvula = (RemoteIpValve) valvulas("127\\.0\\.0\\.1").get(0);

        assertThat(valvula.getRemoteIpHeader()).isEqualToIgnoringCase("X-Forwarded-For");
        assertThat(valvula.getProtocolHeader()).isNull();
        assertThat(valvula.getHostHeader()).isNull();
        assertThat(valvula.getPortHeader()).isNull();
    }

    @Test void unaExpresionInvalidaImpideArrancarEnVezDeConfiarEnTodo() {
        assertThatThrownBy(() -> valvulas("(sin cerrar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRUSTED_PROXIES");
    }
}
