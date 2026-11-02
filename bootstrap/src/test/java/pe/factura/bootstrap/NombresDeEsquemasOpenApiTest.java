package pe.factura.bootstrap;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5: springdoc nombra cada esquema del OpenAPI por el nombre simple de la clase. Dos registros anidados con el mismo nombre (un {@code ItemDto} en la petición y otro en la respuesta,
 * un {@code Guardar} de clientes y otro de productos) se fusionan en <b>un</b> solo esquema, y la referencia {@code /developers} documenta una de las dos formas en las dos
 * partes: los campos de la respuesta ({@code valor_venta}, {@code igv}…) desaparecían, y {@code POST /v1/productos} mostraba el cuerpo de los clientes. Esta prueba falla con el nombre
 * repetido y las clases; se arregla con {@code @Schema(name = "...")} en una de ellas.
 */
class NombresDeEsquemasOpenApiTest {
    private static String nombreDelEsquema(JavaClass c) {
        return c.tryGetAnnotationOfType(Schema.class).map(Schema::name).filter(n -> !n.isBlank()).orElse(c.getSimpleName());
    }

    @Test void ningunNombreDeEsquemaSeRepiteEnLosRegistrosDeLaApi() {
        List<JavaClass> dtos = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("pe.factura.adapters.rest").stream()
                .filter(c -> !c.isAnonymousClass() && c.isRecord()).toList();
        assertThat(dtos).as("se importaron los registros de pe.factura.adapters.rest (los DTO y los que se declaran en controladores)").hasSizeGreaterThan(50);

        Map<String, List<String>> repetidos = new TreeMap<>(dtos.stream().collect(Collectors.groupingBy(NombresDeEsquemasOpenApiTest::nombreDelEsquema, Collectors.mapping(JavaClass::getName, Collectors.toList()))));
        repetidos.values().removeIf(clases -> clases.size() < 2);

        assertThat(repetidos).as("esquemas con el mismo nombre: uno tapa al otro en el OpenAPI; ponga @Schema(name = \"…\") a una de las clases").isEmpty();
    }
}
