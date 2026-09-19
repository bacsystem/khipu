package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.PersonalizacionPdf;

public record PersonalizacionPdfResponse(
        @Schema(example = "moderno") String plantilla,
        @Schema(example = "#1F5F4A") String colorPrimario,
        @Schema(example = "true", description = "Si la empresa cargó un logo; se descarga en `GET /v1/empresa/logo`") boolean tieneLogo,
        @Schema(example = "Gracias por su preferencia") String pieDePagina,
        @Schema(example = "Entrega en 48 h") String observacionesPorDefecto) {

    public static PersonalizacionPdfResponse de(PersonalizacionPdf p) {
        return new PersonalizacionPdfResponse(p.plantilla().nombre(), p.colorPrimario(), p.tieneLogo(), p.pieDePagina(), p.observacionesPorDefecto());
    }
}
