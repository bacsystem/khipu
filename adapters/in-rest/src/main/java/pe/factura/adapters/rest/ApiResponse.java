package pe.factura.adapters.rest;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(String estado, T datos, String mensaje, String codigo, Map<String, List<String>> errores) {
    public static <T> ApiResponse<T> ok(T datos) { return new ApiResponse<>("exito", datos, null, null, null); }
    public static ApiResponse<Void> error(String codigo, String mensaje) { return new ApiResponse<>("error", null, mensaje, codigo, null); }
    public static ApiResponse<Void> validacion(Map<String, List<String>> errores) { return new ApiResponse<>("error", null, "Validación fallida", "VALIDACION", errores); }
}
