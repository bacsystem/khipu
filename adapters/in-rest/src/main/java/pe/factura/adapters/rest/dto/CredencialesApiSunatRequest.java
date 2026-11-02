package pe.factura.adapters.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
public record CredencialesApiSunatRequest(
        @NotBlank @Schema(example = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17", description = "«Id» de la aplicación registrada en SOL, opción «Credenciales de API SUNAT» (client_id)") String clientId,
        @NotBlank @Schema(example = "HhWwZVo8ZaWm4BZ7n/xEzw==", description = "«Clave» de esa aplicación (client_secret). Se guarda cifrada y nunca vuelve por la API") String clientSecret) {
    /** Sin el secreto: un record lo imprimiría entero en cualquier log que muestre el pedido. */
    @Override public String toString() { return "CredencialesApiSunatRequest[clientId=" + clientId + ", clientSecret=***]"; }
}
