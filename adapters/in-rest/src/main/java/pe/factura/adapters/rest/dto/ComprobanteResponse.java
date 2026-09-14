package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.Comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ComprobanteResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "01", description = "Catálogo 01 SUNAT: 01=factura, 03=boleta") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "125") Long numero,
        @Schema(example = "2026-09-14") LocalDate fechaEmision,
        @Schema(example = "PEN") String moneda,
        @Schema(example = "ACEPTADO") String estadoDocumento,
        @Schema(example = "a1b2c3d4e5f6...") String hash,
        @Schema(example = "1") Integer intentos,
        @Schema(example = "null") String ultimoError,
        CdrDto cdr, TotalesDto totales,
        @Schema(example = "{\"xml\": \"/v1/facturas/{id}/xml\", \"cdr\": \"/v1/facturas/{id}/cdr\"}") Map<String, String> enlaces) {
    public record CdrDto(
            @Schema(example = "0") String codigo,
            @Schema(example = "La Factura numero F001-125, ha sido aceptada") String descripcion,
            List<String> observaciones) {}

    public record TotalesDto(
            @Schema(example = "1000.00") BigDecimal gravado,
            @Schema(example = "0.00") BigDecimal exonerado,
            @Schema(example = "0.00") BigDecimal inafecto,
            @Schema(example = "180.00") BigDecimal igv,
            @Schema(example = "1180.00") BigDecimal total) {}

    public static ComprobanteResponse de(Comprobante c, String base) {
        String p = base + "/" + c.id();
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.moneda(),
                c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(), c.totales().total()),
                Map.of("xml", p + "/xml", "cdr", p + "/cdr"));
    }
}
