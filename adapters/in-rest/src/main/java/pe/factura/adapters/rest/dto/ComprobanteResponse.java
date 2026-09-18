package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.CargoCalculado;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.ItemCalculado;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;

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
        @Schema(example = "2026-10-14", description = "Fecha de vencimiento informada, o `null`") LocalDate fechaVencimiento,
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
        @Schema(description = "Detracción (SPOT), solo en operaciones 1001–1004") DetraccionDto detraccion,
        @Schema(description = "Retención del IGV informada (código 62); el cliente paga total − monto") RetencionDto retencionIgv,
        @Schema(description = "Percepción cobrada (51/52/53); el cliente paga total + monto") PercepcionDto percepcion,
        @Schema(description = "Facturas de anticipo regularizadas en esta factura; sus importes ya pagados se restan del total") List<AnticipoDto> anticipos,
        @Schema(description = "Orden de compra, guías de remisión y otros documentos relacionados; `null` si no hay ninguno") ReferenciasDto referencias,
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

    public record DetraccionDto(
            @Schema(example = "022") String codigoBienServicio,
            @Schema(example = "Otros servicios empresariales", description = "Descripción del catálogo 54") String descripcion,
            @Schema(example = "12") BigDecimal porcentaje,
            @Schema(example = "1416.00", description = "Monto a depositar, siempre en PEN") BigDecimal monto,
            @Schema(example = "00-000-123456") String cuentaBancoNacion,
            @Schema(example = "001", description = "Catálogo 59") String medioPago) {
        static DetraccionDto de(Detraccion d) {
            return d == null ? null : new DetraccionDto(d.codigoBienServicio(), d.descripcionBienServicio(), d.porcentaje(), d.monto(), d.cuentaBancoNacion(), d.medioPago());
        }
    }

    public record RetencionDto(@Schema(example = "3") BigDecimal porcentaje, @Schema(example = "35.40") BigDecimal monto,
                               @Schema(example = "1144.60", description = "Importe total menos la retención") BigDecimal netoCobrar) {}

    public record PercepcionDto(@Schema(example = "51") String regimen, @Schema(example = "Percepción venta interna") String descripcion,
                                @Schema(example = "2") BigDecimal porcentaje, @Schema(example = "1180.00") BigDecimal base, @Schema(example = "23.60") BigDecimal monto,
                                @Schema(example = "1203.60", description = "Importe total más la percepción: lo que paga el cliente") BigDecimal totalConPercepcion) {}

    public record AnticipoDto(@Schema(example = "F001-120", description = "Factura de anticipo (serie-número)") String comprobante,
                              @Schema(example = "F001") String serie, @Schema(example = "120") Long numero,
                              @Schema(example = "1000.00", description = "Valor sin IGV que se descuenta de la base") BigDecimal monto,
                              @Schema(example = "1180.00", description = "Importe que el cliente pagó con el anticipo (IGV incluido)") BigDecimal importePagado,
                              @Schema(example = "gravado", description = "`gravado`, `exonerado` o `inafecto`") String afectacion,
                              @Schema(example = "04", description = "Código SUNAT del descuento global por anticipo (catálogo 53: 04/05/06)") String codigoSunat,
                              @Schema(example = "2026-09-01") LocalDate fechaPago) {
        static AnticipoDto de(Anticipo a) {
            return new AnticipoDto(a.comprobante(), a.serie(), a.numero(), a.monto(), a.importePagado(), a.afectacion().name().toLowerCase(), a.codigoSunat(), a.fechaPago());
        }
    }

    /** Orden de compra, guías y otros documentos relacionados tal como se enviaron; `null` cuando la factura no referencia ninguno. */
    public record ReferenciasDto(
            @Schema(example = "OC-2026-0457") String ordenCompra,
            @Schema(description = "Guías de remisión (catálogo 01: 09 remitente, 31 transportista)") List<DocumentoDto> guias,
            @Schema(description = "Otros documentos (catálogo 12: 04–09, 99)") List<DocumentoDto> documentosRelacionados) {
        public record DocumentoDto(@Schema(example = "09") String tipo, @Schema(example = "T001-123") String numero) {}
        static ReferenciasDto de(Referencias r) {
            return r.vacias() ? null : new ReferenciasDto(r.ordenCompra(),
                    r.guias().isEmpty() ? null : r.guias().stream().map(g -> new DocumentoDto(g.tipo(), g.numero())).toList(),
                    r.otros().isEmpty() ? null : r.otros().stream().map(d -> new DocumentoDto(d.tipo(), d.numero())).toList());
        }
    }

    public record ReceptorDto(
            @Schema(example = "6", description = "Catálogo 06 SUNAT: 6=RUC, 1=DNI") String tipoDoc,
            @Schema(example = "20554198211") String numDoc,
            @Schema(example = "CORPORACION GRAFICA ANDINA S.A.C.") String razonSocial,
            @Schema(example = "Av. Argentina 2450, Lima") String direccion) {}

    /** Descuento tal como se aplicó: lo enviado (tipo/valor), el monto resultante y el código SUNAT del catálogo 53. */
    public record DescuentoDto(
            @Schema(example = "PORCENTAJE", description = "PORCENTAJE | MONTO") String tipo,
            @Schema(example = "10") BigDecimal valor,
            @Schema(example = "100.00", description = "Monto del descuento sin IGV") BigDecimal monto,
            @Schema(example = "true") boolean afectaBaseIgv,
            @Schema(example = "00", description = "Catálogo 53: 00/01 por línea, 02/03 global") String codigo) {}

    /** Cargo tal como se aplicó: lo enviado (tipo/valor), el monto resultante y el código SUNAT del catálogo 53. */
    public record CargoDto(
            @Schema(example = "MONTO", description = "PORCENTAJE | MONTO") String tipo,
            @Schema(example = "25.00") BigDecimal valor,
            @Schema(example = "25.00", description = "Monto del cargo sin IGV") BigDecimal monto,
            @Schema(example = "false", description = "`true` si se suma a la base del IGV (47/49)") boolean afectaBaseIgv,
            @Schema(example = "recargo_consumo", description = "`recargo_consumo` cuando es el 46; `null` en los demás") String motivo,
            @Schema(example = "50", description = "Código SUNAT derivado (catálogo 53): 47/48 por línea, 46/49/50 global") String codigo) {
        static CargoDto de(CargoCalculado cc) {
            return new CargoDto(cc.cargo().tipo().name(), cc.cargo().valor(), cc.monto(), cc.afectaBase(),
                    cc.cargo().motivo().map(Cargo.Motivo::nombre).orElse(null), cc.codigo());
        }
        static List<CargoDto> de(List<CargoCalculado> cargos) {
            return cargos.isEmpty() ? null : cargos.stream().map(CargoDto::de).toList();
        }
    }

    public record ItemDto(
            @Schema(example = "SRV-001") String codigo,
            @Schema(example = "Servicio de consultoría") String descripcion,
            @Schema(example = "ZZ") String unidad,
            @Schema(example = "1.00") BigDecimal cantidad,
            @Schema(example = "2000.00") BigDecimal precioUnitario,
            @Schema(example = "10", description = "Afectación del IGV, catálogo 07: `10` gravado, `20` exonerado, `30` inafecto; gratuitas `11`–`16` (gravadas), `21` (exonerada), `31`–`37` (inafectas)") String tipoAfectacionIgv,
            @Schema(example = "1000.00", description = "Valor de venta de la línea sin IGV, neto de descuento que afecta la base y con los cargos 47 (en gratuitas, el valor referencial)") BigDecimal valorVenta,
            @Schema(example = "180.00", description = "IGV de la línea; en gratuitas gravadas se informa pero no se cobra") BigDecimal igv,
            @Schema(example = "1180.00", description = "Lo que paga el cliente por la línea, con cargos 48 (0.00 en gratuitas)") BigDecimal precioVenta,
            @Schema(example = "false", description = "true si la afectación es gratuita (11–16, 21, 31–37)") boolean gratuita,
            DescuentoDto descuento,
            @Schema(description = "Cargos de la línea aplicados, si los hubo") List<CargoDto> cargos,
            @Schema(description = "ISC de la línea, si lo tiene") IscDto isc,
            @Schema(example = "0.00", description = "ICBPER de la línea (bolsas × monto vigente)") BigDecimal icbper,
            @Schema(example = "15101505", description = "Código de producto SUNAT (catálogo 25), o `null`") String codigoSunat,
            @Schema(description = "GTIN del producto, o `null`") GtinDto gtin) {}

    public record GtinDto(@Schema(example = "GTIN-13") String tipo, @Schema(example = "7750182000123") String codigo) {}

    public record IscDto(@Schema(example = "01") String sistema, @Schema(example = "35") BigDecimal tasa, @Schema(example = "350.00") BigDecimal monto) {}

    public record CdrDto(
            @Schema(example = "0", description = "Código de respuesta SUNAT: `0` aceptado; 2000–3999 rechazado (corregir y reemitir); 4000+ aceptado con observaciones; 1000–1999 error del emisor (fault, sin CDR)") String codigo,
            @Schema(example = "La Factura numero F001-125, ha sido aceptada", description = "Descripción oficial de SUNAT") String descripcion,
            List<String> observaciones) {}

    public record TotalesDto(
            @Schema(example = "1000.00") BigDecimal gravado,
            @Schema(example = "0.00") BigDecimal exonerado,
            @Schema(example = "0.00") BigDecimal inafecto,
            @Schema(example = "180.00") BigDecimal igv,
            @Schema(example = "0.00", description = "Base de las operaciones gratuitas (tributo 9996): no se cobra") BigDecimal gratuito,
            @Schema(example = "0.00", description = "IGV de las operaciones gratuitas gravadas: solo informativo, no se cobra") BigDecimal igvGratuitas,
            @Schema(example = "0.00", description = "Total ISC (se suma al precio de venta y a la base del IGV)") BigDecimal isc,
            @Schema(example = "0.00", description = "Total ICBPER (bolsas de plástico)") BigDecimal icbper,
            @Schema(example = "1000.00", description = "Total valor de venta onerosa (suma de bases, LineExtensionAmount)") BigDecimal totalValorVenta,
            @Schema(example = "1180.00", description = "Total precio de venta = valor de venta + tributos (TaxInclusiveAmount)") BigDecimal totalPrecioVenta,
            @Schema(example = "0.00", description = "Descuentos que no afectan la base (línea 01 + global 03), AllowanceTotalAmount") BigDecimal totalDescuentos,
            @Schema(example = "0.00", description = "Cargos que no afectan la base (línea 48 + globales 46/50), ChargeTotalAmount") BigDecimal totalCargos,
            @Schema(example = "0.00", description = "Suma de los importes ya pagados con facturas de anticipo, IGV incluido (PrepaidAmount)") BigDecimal totalAnticipos,
            @Schema(example = "0.00", description = "Redondeo aplicado al importe total (PayableRoundingAmount), entre −1.00 y 1.00") BigDecimal redondeo,
            @Schema(example = "1180.00", description = "Importe a pagar (PayableAmount) = precio de venta + cargos − descuentos que no afectan la base − anticipos + redondeo") BigDecimal total,
            @Schema(description = "Descuento global aplicado, si lo hubo") DescuentoDto descuentoGlobal,
            @Schema(description = "Cargos globales aplicados, si los hubo") List<CargoDto> cargos) {}

    public static ComprobanteResponse de(Comprobante c, String base) {
        String p = base + "/" + c.id();
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.fechaVencimiento(), c.moneda(), c.tipoOperacion(),
                de(c.receptor()), c.totales().items().stream().map(ComprobanteResponse::de).toList(),
                c.estado().name(), c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(),
                        c.totales().gratuito(), c.totales().igvGratuitas(), c.totales().isc(), c.totales().icbper(), c.totales().totalValorVenta(), c.totales().totalPrecioVenta(), c.totales().totalDescuentos(), c.totales().totalCargos(), c.totales().totalAnticipos(), c.totales().redondeo(), c.totales().total(),
                        c.totales().descuentoGlobal() == null ? null : new DescuentoDto(c.totales().descuentoGlobal().descuento().tipo().name(),
                                c.totales().descuentoGlobal().descuento().valor(), c.totales().descuentoGlobal().monto(),
                                c.totales().descuentoGlobal().afectaBase(), c.totales().descuentoGlobal().codigo()),
                        CargoDto.de(c.totales().cargosGlobales())),
                FormaPagoDto.de(c.formaPago()),
                DetraccionDto.de(c.detraccion()),
                c.retencion() == null ? null : new RetencionDto(c.retencion().porcentaje(), c.retencion().monto(), c.totales().total().subtract(c.retencion().monto())),
                c.percepcion() == null ? null : new PercepcionDto(c.percepcion().regimen(), c.percepcion().descripcionRegimen(), c.percepcion().porcentaje(),
                        c.percepcion().base(), c.percepcion().monto(), c.percepcion().totalConPercepcion(c.totales().total())),
                c.anticipos().isEmpty() ? null : c.anticipos().stream().map(AnticipoDto::de).toList(),
                ReferenciasDto.de(c.referencias()),
                enlaces(c, p));
    }

    /** `cdr` solo cuando existe la constancia: un rechazo por SOAPFault trae código y descripción pero SUNAT no emitió CDR. */
    private static Map<String, String> enlaces(Comprobante c, String p) {
        return c.cdrKey() == null ? Map.of("xml", p + "/xml") : Map.of("xml", p + "/xml", "cdr", p + "/cdr");
    }

    private static ReceptorDto de(Receptor r) {
        return r == null ? null : new ReceptorDto(r.tipoDoc(), r.numDoc(), r.razonSocial(), r.direccion());
    }

    private static ItemDto de(ItemCalculado ic) {
        Item i = ic.item();
        DescuentoDto d = i.tieneDescuento()
                ? new DescuentoDto(i.descuento().tipo().name(), i.descuento().valor(), ic.descuento(), i.descuento().afectaBaseIgv(), i.descuento().codigoSunat(false))
                : null;
        IscDto isc = ic.tieneIsc() ? new IscDto(i.isc().sistema(), ic.iscPorcentaje(), ic.isc()) : null;
        return new ItemDto(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo(), ic.valorVenta(), ic.igv(), ic.precioVenta(), ic.gratuita(), d, CargoDto.de(ic.cargos()), isc, ic.icbper(),
                i.tieneCodigoSunat() ? i.codigoSunat().codigo() : null, i.gtin() == null ? null : new GtinDto(i.gtin().tipo(), i.gtin().codigo()));
    }
}
