package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;

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
        @Schema(example = "0101", description = "Catálogo 51 SUNAT") String tipoOperacion,
        ReceptorDto receptor,
        List<ItemDto> items,
        @Schema(example = "ACEPTADO") String estadoDocumento,
        @Schema(example = "a1b2c3d4e5f6...") String hash,
        @Schema(example = "20614798093-01-F001-00000125") String nombreArchivo,
        @Schema(example = "1") Integer intentos,
        @Schema(example = "null") String ultimoError,
        CdrDto cdr, TotalesDto totales,
        @Schema(example = "{\"xml\": \"/v1/facturas/{id}/xml\", \"cdr\": \"/v1/facturas/{id}/cdr\"}") Map<String, String> enlaces) {
    public record ReceptorDto(
            @Schema(example = "6", description = "Catálogo 06 SUNAT: 6=RUC, 1=DNI") String tipoDoc,
            @Schema(example = "20554198211") String numDoc,
            @Schema(example = "CORPORACION GRAFICA ANDINA S.A.C.") String razonSocial,
            @Schema(example = "Av. Argentina 2450, Lima") String direccion) {}

    public record ItemDto(
            @Schema(example = "SRV-001") String codigo,
            @Schema(example = "Servicio de consultoría") String descripcion,
            @Schema(example = "ZZ") String unidad,
            @Schema(example = "1.00") BigDecimal cantidad,
            @Schema(example = "2000.00") BigDecimal precioUnitario,
            @Schema(example = "10", description = "Catálogo 07 SUNAT: 10=gravado, 20=exonerado, 30=inafecto") String tipoAfectacionIgv) {}

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
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.moneda(), c.tipoOperacion(),
                de(c.receptor()), c.items().stream().map(ComprobanteResponse::de).toList(),
                c.estado().name(), c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(), c.totales().total()),
                Map.of("xml", p + "/xml", "cdr", p + "/cdr"));
    }

    private static ReceptorDto de(Receptor r) {
        return r == null ? null : new ReceptorDto(r.tipoDoc(), r.numDoc(), r.razonSocial(), r.direccion());
    }

    private static ItemDto de(Item i) {
        return new ItemDto(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo());
    }
}
