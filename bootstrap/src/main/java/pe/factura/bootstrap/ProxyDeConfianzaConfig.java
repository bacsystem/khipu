package pe.factura.bootstrap;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.PatternSyntaxException;

/**
 * De quién se acepta la IP del cliente en {@code X-Forwarded-For} (#208). Detrás del BFF del portal, {@code getRemoteAddr()}
 * es la IP del servidor de Next, la misma para todos los administradores, y la bitácora de auditoría (#178) pierde su valor
 * como evidencia.
 *
 * <p><b>Sin configurar no se confía en nadie</b> ({@code TRUSTED_PROXIES} vacío): no se instala nada y una cabecera
 * falsificada no cambia lo que se registra. Con la expresión configurada, Tomcat solo reescribe la IP de la conexión cuando el
 * par que la envía casa con ella, y recorre la cadena desde la derecha saltando proxies de confianza: lo que un cliente
 * antepone a la izquierda nunca gana.
 *
 * <p>Por qué una válvula explícita y no {@code server.forward-headers-strategy=native}: en ese modo, si no se fija
 * {@code internal-proxies}, Tomcat da por buenos todos los rangos privados (10/8, 172.16/12, 192.168/16…), que es justo lo que
 * una red de plataforma (Railway, Docker) comparte con cualquier otro servicio.
 */
@Configuration
public class ProxyDeConfianzaConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> proxiesDeConfianza(@Value("${app.trusted-proxies:}") String proxies) {
        return fabrica -> {
            if (proxies == null || proxies.isBlank()) return;
            var valvula = new RemoteIpValve();
            valvula.setRemoteIpHeader("X-Forwarded-For");
            // La válvula trae `X-Forwarded-Proto` activo por defecto: reescribiría también esquema y `isSecure()`. Aquí solo se quiere la IP.
            valvula.setProtocolHeader(null);
            try {
                valvula.setInternalProxies(proxies.strip());
            } catch (PatternSyntaxException e) {
                // Arrancar sin proxies de confianza sería seguro, pero silencioso; arrancar confiando en todo, no. Se aborta y se dice por qué.
                throw new IllegalArgumentException("TRUSTED_PROXIES no es una expresión regular válida: " + e.getDescription(), e);
            }
            fabrica.addEngineValves(valvula);
        };
    }
}
