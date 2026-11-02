package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(
        @Schema(example = "exito", description = "\"exito\" o \"error\"") String estado,
        T datos,
        @Schema(example = "null", description = "Mensaje legible, presente solo en error") String mensaje,
        @Schema(example = "null", description = "Código de error, presente solo en error") String codigo,
        @Schema(example = "null", description = "Errores de validación por campo, presente solo en VALIDACION") Map<String, List<String>> errores,
        @Schema(description = "Lo que SUNAT observará aunque acepte el comprobante (por ahora 4013 y 4014, sobre el RUC del comprador); ausente si no hay nada que avisar") List<Aviso> avisos) {
    /** Un aviso que no impide la operación. {@code codigo}: el de SUNAT cuando la observación es suya. */
    public record Aviso(@Schema(example = "4013") String codigo, @Schema(example = "El RUC 20100070970 del comprador no está activo en SUNAT (BAJA DEFINITIVA): SUNAT aceptará el comprobante con una observación") String mensaje) {
        public static List<Aviso> de(List<pe.factura.application.port.in.ValidarCompradorUseCase.Aviso> avisos) { return avisos.stream().map(a -> new Aviso(a.codigo(), a.mensaje())).toList(); }
    }

    public static <T> ApiResponse<T> ok(T datos) { return new ApiResponse<>("exito", datos, null, null, null, null); }
    /** Con avisos; una lista vacía no se serializa (el contrato dice «ausente si no hay nada que avisar»). */
    public static <T> ApiResponse<T> ok(T datos, List<Aviso> avisos) { return new ApiResponse<>("exito", datos, null, null, null, avisos == null || avisos.isEmpty() ? null : avisos); }
    public static ApiResponse<Void> error(String codigo, String mensaje) { return new ApiResponse<>("error", null, mensaje, codigo, null, null); }
    public static ApiResponse<Void> validacion(Map<String, List<String>> errores) { return new ApiResponse<>("error", null, "Validación fallida", "VALIDACION", errores, null); }
}
