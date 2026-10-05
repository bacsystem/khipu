package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Lo que se envía para cambiar la configuración de la plataforma (#199). La validación es del dominio: lo que no sirve vuelve con un mensaje que dice qué corregir. */
public final class ConfiguracionRequest {
    private ConfiguracionRequest() {}

    public record Remitente(
            @Schema(example = "khipu", description = "Opcional; hasta 100 caracteres, sin saltos de línea, comillas ni < >") String nombre,
            @Schema(example = "no-responder@khipu.pe", description = "Obligatorio; una sola dirección, en ASCII") String email,
            @Schema(example = "soporte@khipu.pe", description = "Opcional; adónde llegan las respuestas") String responderA) {}

    public record Plantilla(
            @Schema(example = "Restablecer contraseña", description = "Una sola línea, hasta 150 caracteres; admite las variables del correo") String asunto,
            @Schema(example = "Para restablecer tu contraseña abre este enlace (válido {validez}):\n{enlace}", description = "Hasta 5000 caracteres; admite las variables del correo y tiene que incluir las indispensables") String cuerpo) {}

    public record Banner(
            @Schema(example = "Mantenimiento programado esta noche de 22:00 a 23:00", description = "Una sola línea, hasta 300 caracteres") String texto,
            @Schema(example = "2026-10-15T20:00:00Z", description = "Desde cuándo se muestra") Instant desde,
            @Schema(example = "2026-10-16T01:00:00Z", description = "Hasta cuándo: obligatorio, posterior al inicio y a ahora, y a lo sumo 90 días después de empezar") Instant hasta) {}
}
