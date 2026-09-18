package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        @Schema(example = "ACEPTADO", description = "Estado del comprobante: `RECIBIDO` → `FIRMADO` → `ENVIADO` → `ACEPTADO` / `ACEPTADO_CON_OBS` / `RECHAZADO`; `ERROR_ENVIO` (SUNAT no disponible, se reintenta), `INVALIDO` (XML no válido), `ANULADO` (comunicación de baja aceptada)") String estadoDocumento,
        @Schema(example = "a1b2c3d4e5f6...", description = "Resumen (digest) de la firma XML-DSig; se imprime en la representación impresa y en el código QR") String hash,
        @Schema(example = "20614798093-01-F001-00000125", description = "Nombre oficial del archivo: `RUC-TIPO-SERIE-NUMERO`") String nombreArchivo,
        @Schema(example = "1", description = "Intentos de envío a SUNAT realizados") Integer intentos,
        @Schema(example = "null", description = "Último error de envío (`código SUNAT - mensaje`), o `null`") String ultimoError,
        CdrDto cdr, TotalesDto totales,
        FormaPagoDto formaPago,
        @Schema(example = "{\"xml\": \"/v1/facturas/{id}/xml\", \"cdr\": \"/v1/facturas/{id}/cdr\"}", description = "cdr solo está presente cuando SUNAT emitió la constancia") Map<String, String> enlaces) {
    public record FormaPagoDto(
            @Schema(example = "credito", description = "contado | credito") String tipo,
            @Schema(example = "1180.00", description = "Solo al crédito") BigDecimal montoPendiente,
            @Schema(description = "Solo al crédito; el identificador SUNAT es Cuota001, Cuota002…") List<CuotaDto> cuotas) {
        public record CuotaDto(@Schema(example = "Cuota001") String id, @Schema(example = "590.00") BigDecimal monto, @Schema(example = "2026-10-15") LocalDate vencimiento) {}

        static FormaPagoDto de(FormaPago f) {
            List<CuotaDto> cs = new ArrayList<>();
            for (int k = 0; k < f.cuotas().size(); k++) cs.add(new CuotaDto(FormaPago.idCuota(k + 1), f.cuotas().get(k).monto(), f.cuotas().get(k).vencimiento()));
            return new FormaPagoDto(f.tipo().name().toLowerCase(Locale.ROOT), f.montoPendiente(), cs);
        }
    }

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
            @Schema(example = "0", description = "Código de respuesta SUNAT: `0` aceptado; 2000–3999 rechazado (corregir y reemitir); 4000+ aceptado con observaciones; 1000–1999 error del emisor (fault, sin CDR)") String codigo,
            @Schema(example = "La Factura numero F001-125, ha sido aceptada", description = "Descripción oficial de SUNAT") String descripcion,
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
                FormaPagoDto.de(c.formaPago()),
                enlaces(c, p));
    }

    /** `cdr` solo cuando existe la constancia: un rechazo por SOAPFault trae código y descripción pero SUNAT no emitió CDR. */
    private static Map<String, String> enlaces(Comprobante c, String p) {
        return c.cdrKey() == null ? Map.of("xml", p + "/xml") : Map.of("xml", p + "/xml", "cdr", p + "/cdr");
    }

    private static ReceptorDto de(Receptor r) {
        return r == null ? null : new ReceptorDto(r.tipoDoc(), r.numDoc(), r.razonSocial(), r.direccion());
    }

    private static ItemDto de(Item i) {
        return new ItemDto(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo());
    }
}
