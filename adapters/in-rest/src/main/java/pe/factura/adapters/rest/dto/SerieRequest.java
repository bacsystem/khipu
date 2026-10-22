package pe.factura.adapters.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
public record SerieRequest(
        @NotBlank @Pattern(regexp = "01|03|04|07|08|20|40") @Schema(example = "01", description = "01=factura, 03=boleta, 04=liquidación de compra, 07=nota de crédito, 08=nota de débito, 20=retención, 40=percepción (estos dos, solo si la empresa es agente de ese tipo: `422 AGENTE_NO_DESIGNADO`)") String tipo,
        @NotBlank @Schema(example = "F001", description = "Serie del tipo: `F###` factura, `B###` boleta, `L###` liquidación de compra, `F###` o `B###` las notas, `R###` retención, `P###` percepción") String serie,
        @PositiveOrZero @Max(value = 99_999_999, message = "1001 - el correlativo admite hasta 8 dígitos") @Schema(example = "0", description = "Último número ya emitido; el siguiente comprobante lleva este valor + 1. Máximo 99999999 (regla 1001)") Long correlativoInicial,
        @Pattern(regexp = "\\d{4}", message = "código de establecimiento de 4 dígitos") @Schema(example = "0000", description = "Establecimiento desde el que emite la serie (`GET /v1/empresa/establecimientos`); por defecto `0000`, el domicilio fiscal. Un anexo debe existir y estar activo: `422 ESTABLECIMIENTO_INVALIDO`") String establecimiento) {}
