package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba unitaria pura (sin contenedor) del codificador/decodificador JSON mínimo de observaciones del CDR. */
class JsonObservacionesTest {

    @Test void roundTripEscapaCaracteresEspeciales() {
        List<String> original = List.of("a \"b\" \\ c", "línea1\nlínea2\ttab", "x\",\"y");
        String json = JdbcComprobanteRepository.aJson(original);
        List<String> decodificado = JdbcComprobanteRepository.deJson(json);
        assertThat(decodificado).containsExactlyElementsOf(original);
    }
}
