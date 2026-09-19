package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta o edición de un establecimiento anexo: el código es el declarado en la ficha RUC; el 0000 es el domicilio fiscal y no se registra aquí. */
public record EstablecimientoRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "código de establecimiento de 4 dígitos (regla 3030)") @Schema(example = "0002", description = "Código del anexo tal como figura en la ficha RUC (4 dígitos; `0000` es el domicilio fiscal, se configura en datos fiscales)") String codigo,
        @NotBlank @Size(max = 100) @Schema(example = "Tienda Miraflores", description = "Nombre propio del local (1–100 caracteres), solo para identificarlo en el portal y la API") String nombre,
        @NotNull @Valid @Schema(description = "Domicilio del local: va en el XML como `cac:RegistrationAddress` de los comprobantes de las series asignadas a él") DatosFiscalesRequest.DomicilioDto domicilio) {}
