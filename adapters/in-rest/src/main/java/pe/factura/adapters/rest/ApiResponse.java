package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(
        @Schema(example = "exito", description = "\"exito\" o \"error\"") String estado,
        T datos,
        @Schema(example = "null", description = "Mensaje legible, presente solo en error") String mensaje,
        @Schema(example = "null", description = "Código de error, presente solo en error") String codigo,
        @Schema(example = "null", description = "Errores de validación por campo, presente solo en VALIDACION") Map<String, List<String>> errores) {
    public static <T> ApiResponse<T> ok(T datos) { return new ApiResponse<>("exito", datos, null, null, null); }
    public static ApiResponse<Void> error(String codigo, String mensaje) { return new ApiResponse<>("error", null, mensaje, codigo, null); }
    public static ApiResponse<Void> validacion(Map<String, List<String>> errores) { return new ApiResponse<>("error", null, "Validación fallida", "VALIDACION", errores); }
}
