package pe.factura.adapters.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EventoDocumento;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El cuerpo de un webhook de cambio de estado (#19), con los nombres en snake_case de la API. Es una foto de lo necesario para reaccionar sin llamar de vuelta (qué
 * comprobante, en qué quedó y por qué); el detalle completo sigue en {@code GET /v1/facturas/{id}}. Se arma una vez al encolar y es el mismo texto que se firma en cada
 * reintento.
 */
final class PayloadDeWebhook {
    private PayloadDeWebhook() {}

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    static String de(String evento, Comprobante c, EventoDocumento cambio, Instant ahora) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", c.id());
        datos.put("tipo", c.tipo().codigo());
        datos.put("serie", c.serie());
        datos.put("numero", c.numero());
        datos.put("fecha_emision", c.fechaEmision());
        datos.put("estado", cambio.estadoNuevo().name());
        datos.put("estado_anterior", cambio.estadoAnterior() == null ? null : cambio.estadoAnterior().name());
        datos.put("detalle", cambio.detalle());
        if (c.cdr() != null) {
            Map<String, Object> cdr = new LinkedHashMap<>();
            cdr.put("codigo", c.cdr().codigo());
            cdr.put("descripcion", c.cdr().descripcion());
            cdr.put("observaciones", c.cdr().observaciones() == null ? java.util.List.of() : c.cdr().observaciones());
            datos.put("cdr", cdr);
        }
        datos.put("ultimo_error", c.ultimoError());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("evento", evento);
        cuerpo.put("creado_en", ahora);
        cuerpo.put("datos", datos);
        try {
            return MAPPER.writeValueAsString(cuerpo);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
