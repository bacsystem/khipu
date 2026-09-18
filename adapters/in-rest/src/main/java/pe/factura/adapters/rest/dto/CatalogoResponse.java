package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.util.List;
import java.util.Map;

@Schema(description = "Catálogo oficial de SUNAT (Anexo 8 de las reglas de validación) con sus códigos y descripciones.")
public record CatalogoResponse(
        @Schema(example = "07", description = "Número del catálogo SUNAT") String id,
        @Schema(example = "Código de tipo de afectación del IGV") String nombre,
        @Schema(description = "Cabeceras de la tabla oficial; las adicionales a Código/Descripción aparecen en `extra` de cada entrada") List<String> columnas,
        List<EntradaDto> entradas) {

    public record EntradaDto(
            @Schema(example = "10") String codigo,
            @Schema(example = "Gravado - Operación Onerosa") String descripcion,
            @Schema(description = "Columnas adicionales del catálogo (p. ej. Codigo de tributo = 1000)") Map<String, String> extra) {}

    /** Resumen para el índice: sin entradas. */
    public record ResumenDto(String id, String nombre, @Schema(example = "19") int entradas) {}

    public static CatalogoResponse de(CatalogoSunat.Catalogo c) {
        return new CatalogoResponse(c.id(), c.nombre(), c.columnas(),
                c.entradas().stream().map(e -> new EntradaDto(e.codigo(), e.descripcion(), e.extra())).toList());
    }

    public static ResumenDto resumen(CatalogoSunat.Catalogo c) { return new ResumenDto(c.id(), c.nombre(), c.entradas().size()); }
}
