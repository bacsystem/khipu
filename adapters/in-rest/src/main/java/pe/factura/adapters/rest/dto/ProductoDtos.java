package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.factura.domain.documento.ProductoGuardado;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pedidos y respuestas de los productos guardados (#320). */
public final class ProductoDtos {
    private ProductoDtos() {}

    @Schema(name = "ProductoGuardar")

    public record Guardar(
            @Size(max = 30) @Schema(example = "SKU-001", description = "Código interno (opcional); único por empresa cuando se informa") String codigo,
            @NotBlank @Size(max = 500) @Schema(example = "Servicio de consultoría", description = "Descripción de la línea, hasta 500 caracteres") String descripcion,
            @NotBlank @Size(max = 5) @Schema(example = "ZZ", description = "Unidad de medida del catálogo 03 (`GET /v1/catalogos/03`): NIU unidad (bienes), ZZ unidad (servicios), KGM, HUR…") String unidad,
            @NotBlank @Schema(example = "10", description = "Afectación del IGV, catálogo 07: 10–17, 20, 21, 30–37 o 40") String tipoAfectacionIgv,
            @NotNull @Schema(example = "118.00", description = "Precio unitario de la línea: con IGV incluido en la gravada (10), y sin impuesto en las demás. Mayor que cero") BigDecimal precioUnitario,
            @Size(max = 8) @Schema(example = "80101500", description = "Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos); opcional") String codigoSunat) {
        public ProductoGuardado.Datos datos() { return new ProductoGuardado.Datos(codigo, descripcion, unidad, tipoAfectacionIgv, precioUnitario, codigoSunat); }
    }

    public record Producto(UUID id, String codigo, String descripcion, String unidad, String tipoAfectacionIgv, BigDecimal precioUnitario, String codigoSunat, Instant creadoEn, Instant actualizadoEn) {
        public static Producto de(ProductoGuardado p) {
            return new Producto(p.id(), p.codigo(), p.descripcion(), p.unidad(), p.tipoAfectacionIgv(), p.precioUnitario(), p.codigoSunat(), p.creadoEn(), p.actualizadoEn());
        }
    }
}
