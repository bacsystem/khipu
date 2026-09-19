package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.PlantillaPdf;

/** Diseño del PDF sin el logo, que tiene sus propios endpoints. Los textos en blanco borran el valor. */
public record PersonalizacionPdfRequest(
        @Pattern(regexp = "clasico|moderno|sutil|corporativo|gris", message = "plantilla: clasico, moderno, sutil, corporativo o gris") @Schema(example = "moderno", description = "`clasico` (bordes definidos, por defecto), `moderno` (aireado, acento de color), `sutil` (líneas finas), `corporativo` (cabecera y tabla en color) o `gris` (monocromo)") String plantilla,
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "color_primario: hexadecimal de 6 dígitos, p. ej. #1F5F4A") @Schema(example = "#1F5F4A", description = "Color de acento (títulos, número, cabecera de tabla según la plantilla). La plantilla `gris` lo ignora") String colorPrimario,
        @Size(max = PersonalizacionPdf.MAX_PIE) @Schema(example = "Gracias por su preferencia. Consultas: ventas@empresa.pe", description = "Texto final del PDF (una línea, hasta 300 caracteres); vacío = leyenda por defecto") String pieDePagina,
        @Size(max = PersonalizacionPdf.MAX_OBSERVACIONES) @Schema(example = "Entrega en 48 h en Lima Metropolitana.", description = "Se imprime en el bloque «Observaciones» de cada comprobante que no traiga las suyas (hasta 1000 caracteres, admite saltos de línea)") String observacionesPorDefecto) {

    public PersonalizacionPdf aDominio() {
        return new PersonalizacionPdf(PlantillaPdf.porNombre(plantilla), colorPrimario, null, pieDePagina, observacionesPorDefecto);
    }
}
