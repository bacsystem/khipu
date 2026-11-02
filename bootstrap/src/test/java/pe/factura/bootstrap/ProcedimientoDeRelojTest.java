package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el procedimiento B de {@code deploy/README.md} §10 da por hecho del binding de Spring: la variable de entorno {@code APP_SUNAT_BETAURL} (sin guion bajo entre BETA y
 * URL) sobrescribe {@code app.sunat.beta-url} del application.yml, y el valor por defecto que lleva el archivo de override es exactamente el de application.yml.
 */
class ProcedimientoDeRelojTest {
    static final String BETA_DE_APPLICATION_YML = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";

    private static AppProperties.Sunat sunat(Map<String, Object> entorno) throws Exception {
        MockEnvironment env = new MockEnvironment();
        for (PropertySource<?> yml : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) env.getPropertySources().addLast(yml);
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("systemEnvironment", entorno));
        ConfigurationPropertySources.attach(env);
        return Binder.get(env).bind("app.sunat", AppProperties.Sunat.class).get();
    }

    @Test void laVariableDelProcedimientoSobrescribeLaUrlDeBeta() throws Exception {
        assertThat(sunat(Map.of("APP_SUNAT_BETAURL", "http://127.0.0.1:9/billService")).betaUrl()).isEqualTo("http://127.0.0.1:9/billService");
    }

    @Test void sinLaVariableLaUrlDeBetaEsLaDelYmlQueElOverrideRepiteComoPorDefecto() throws Exception {
        assertThat(sunat(Map.of()).betaUrl()).isEqualTo(BETA_DE_APPLICATION_YML);
    }
}
