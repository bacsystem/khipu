package pe.factura.adapters.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.UncheckedIOException;

/**
 * Los datos sectoriales de la detracción (#69) se guardan como JSON en {@code comprobante_item}: son records anidados
 * ({@link pe.factura.domain.documento.TransporteCarga} con tramos y vehículos) que rara vez se consultan por columna.
 * Se serializan con los nombres de los records (camelCase), independientes de la API, y las fechas como ISO-8601.
 */
final class DatosSectorialesJson {
    private DatosSectorialesJson() {}

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    static String aJson(Object o) {
        try {
            return o == null ? null : MAPPER.writeValueAsString(o);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static <T> T deJson(String json, Class<T> tipo) {
        try {
            return json == null ? null : MAPPER.readValue(json, tipo);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
