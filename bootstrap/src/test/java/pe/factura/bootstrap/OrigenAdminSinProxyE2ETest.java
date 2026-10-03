package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sin configurar nada (#208): no se confía en {@code X-Forwarded-For}. Cualquiera que llame directo puede ponerle lo que quiera a esa
 * cabecera, y si el backend la creyera, falsificaría la IP de su propio registro de auditoría: peor que registrar la del proxy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrigenAdminSinProxyE2ETest extends OrigenAdminE2EBase {

    @Test void unaCabeceraFalsificadaSeIgnoraYSeRegistraLaIpDeLaConexion() {
        String ip = ipRegistradaTras("6.6.6.6");

        assertThat(ip).isNotEqualTo("6.6.6.6").isIn(LOOPBACK);
    }

    @Test void unaCadenaFalsificadaTampocoCambiaLaIp() {
        assertThat(ipRegistradaTras("6.6.6.6, 203.0.113.7")).isIn(LOOPBACK);
    }

    @Test void elEndpointDeCalibracionTambienIgnoraLaCabecera() {
        assertThat(ipSegunElEndpointDeCalibracion("6.6.6.6")).isIn(LOOPBACK);
    }
}
