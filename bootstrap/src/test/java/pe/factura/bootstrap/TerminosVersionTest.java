package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #342: la versión de los términos vive en dos lugares que se despliegan por separado: el backend decide quién tiene que volver a aceptar
 * ({@code app.terminos-version}) y el portal muestra el texto ({@code VERSION_TEXTOS_LEGALES}). Si el texto cambia sin subir la versión del
 * backend, nadie vuelve a aceptar; si sube solo la del backend, se pide aceptar un texto que no cambió. Sin contexto de Spring: solo lee
 * los dos archivos.
 */
class TerminosVersionTest {
    @Test void laVersionDelBackendEsLaDelTextoQueMuestraElPortal() throws Exception {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        String backend = yaml.getObject().getProperty("app.terminos-version");

        Path archivo = Path.of("..", "portal", "src", "content", "legal", "version.ts");
        assertThat(archivo).as("el portal publica la versión de sus textos legales").exists();
        Matcher m = Pattern.compile("export const VERSION_TEXTOS_LEGALES = \"([^\"]+)\";").matcher(Files.readString(archivo));
        assertThat(m.find()).as("VERSION_TEXTOS_LEGALES en %s", archivo).isTrue();

        assertThat(backend).as("app.terminos-version (application.yml) y VERSION_TEXTOS_LEGALES (portal/src/content/legal/version.ts) cambian juntas: ver deploy/README.md")
                .isEqualTo(m.group(1));
    }
}
