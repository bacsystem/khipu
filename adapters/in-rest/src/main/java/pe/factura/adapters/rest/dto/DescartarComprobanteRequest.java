package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Por qué un administrador descarta un comprobante en error de envío (#196). Obligatorio, de hasta 200 caracteres: queda en la bitácora. */
public record DescartarComprobanteRequest(@Schema(example = "El cliente lo reemitió con otra serie", maxLength = 200) String motivo) {}
