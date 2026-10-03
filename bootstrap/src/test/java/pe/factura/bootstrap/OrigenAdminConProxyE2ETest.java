package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con un proxy de confianza configurado (#208): el cliente de prueba entra por loopback, que aquí se declara el proxy. Se usan clases
 * de caracteres y no {@code \.}: las {@code properties} de {@code @SpringBootTest} se leen como .properties, donde la barra invertida se pierde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.trusted-proxies=127[.]0[.]0[.]1|0:0:0:0:0:0:0:1|[:][:]1")
@ActiveProfiles("test")
class OrigenAdminConProxyE2ETest extends OrigenAdminE2EBase {

    @Test void laIpReenviadaPorUnProxyDeConfianzaEsLaQueSeRegistraEnLaBitacora() {
        assertThat(ipRegistradaTras("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test void unaIpFalsaALaIzquierdaDeLaCadenaNoGana() {
        // El cliente antepone una IP inventada; el proxy de confianza agrega la verdadera a la derecha. Se lee desde la derecha.
        assertThat(ipRegistradaTras("6.6.6.6, 203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test void elEndpointDeCalibracionDevuelveLaMismaIpQueLaBitacora() {
        assertThat(ipSegunElEndpointDeCalibracion("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test void unaIpv6ReenviadaQuedaEnLaMismaGrafiaQueUnaConexionDirecta() {
        // El portal reenvía la forma comprimida; la JVM da las conexiones directas sin comprimir. En la bitácora, una sola.
        assertThat(ipRegistradaTras("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ipSegunElEndpointDeCalibracion("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test void sinCabeceraSeRegistraLaIpDeLaConexion() {
        assertThat(ipRegistradaTras(null)).isIn(LOOPBACK);
    }
}
