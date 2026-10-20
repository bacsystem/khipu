package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.EventoDocumento;
import pe.factura.domain.documento.Hidrobiologico;
import pe.factura.domain.documento.TransporteCarga;
import pe.factura.domain.documento.CargoCalculado;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.ComprobanteDeAgente;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.EnvioAlCliente;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.ItemCalculado;
import pe.factura.domain.documento.Leyenda;
import pe.factura.domain.documento.LiquidacionCompra;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import pe.factura.domain.documento.Totales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Los registros anidados llevan @Schema(name = "…Respuesta") cuando FacturaRequest tiene uno con el mismo nombre simple (ItemDto, DescuentoDto…): springdoc nombra los esquemas por nombre simple
// y sin esto un solo esquema se queda con el de la petición, y la referencia /developers documenta mal la respuesta (valor_venta, igv… ausentes).
public record ComprobanteResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "01", description = "Catálogo 01 SUNAT: 01=factura, 03=boleta, 04=liquidación de compra, 07=nota de crédito, 08=nota de débito, 20=retención, 40=percepción") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "125") Long numero,
        @Schema(example = "2026-09-14") LocalDate fechaEmision,
        @Schema(example = "2026-10-14", description = "Fecha de vencimiento informada, o `null`") LocalDate fechaVencimiento,
        @Schema(example = "2026-09-17", description = "Último día en que SUNAT acepta recibirlo (RS 193-2020: 3 días calendario desde la emisión; una boleta, 7: va sola hasta el 5.º día y después khipu la informa en el resumen diario). Pasado ese día, una factura o nota de factura FIRMADO o en ERROR_ENVIO pasa a FUERA_DE_PLAZO; una boleta (o nota sobre boleta que va en el resumen) se sigue informando en un resumen diario, fuera de plazo: SUNAT no la rechaza y puede observarla con la 4443") LocalDate fechaLimiteEnvio,
        @Schema(example = "PEN") String moneda,
        @Schema(example = "0101", description = "Catálogo 51 SUNAT; `null` en una retención (20) o percepción (40), que no tienen tipo de operación") String tipoOperacion,
        @Schema(example = "3.756", description = "Tipo de cambio informado (soles por unidad de `moneda`), o `null`: siempre `null` en soles") BigDecimal tipoCambio,
        @Schema(example = "443.21", description = "Importe total en soles al `tipo_cambio`, o `null` si no se informó o el comprobante es en soles") BigDecimal totalEnSoles,
        ReceptorDto receptor,
        List<ItemDto> items,
        @Schema(example = "ACEPTADO", description = "Estado del comprobante: `RECIBIDO` → `FIRMADO` → `ENVIADO` → `ACEPTADO` / `ACEPTADO_CON_OBS` / `RECHAZADO`; `ERROR_ENVIO` (SUNAT no disponible, se reintenta), `INVALIDO` (XML no válido), `ANULADO` (comunicación de baja aceptada), `FUERA_DE_PLAZO` (factura o nota que no llegó dentro del plazo de envío; terminal. Una boleta no: se informa en el resumen diario fuera de plazo y queda `ACEPTADO_CON_OBS` si SUNAT la observa con la 4443, `ACEPTADO` si no), `DESCARTADO` (un administrador dejó de intentar enviarlo; terminal)") String estadoDocumento,
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
        @Schema(description = "Solo en notas de crédito/débito: factura que modifica y motivo") NotaDto nota,
        @Schema(description = "Solo al consultar una factura: notas de crédito/débito emitidas sobre ella (todas, con su estado); `null` en listados y en la emisión") List<NotaResumenDto> notas,
        @Schema(description = "Solo al consultar y al enviar: la comunicación de baja o el resumen diario más reciente del comprobante (en curso, aceptado o rechazado), o `null`. En una boleta informada en un resumen de alta, `fuera_de_plazo` dice si salió pasado el 7.º día") BajaResponse baja,
        @Schema(description = "Solo al consultar: historial de cambios de estado e intentos de envío, del más antiguo al más reciente (vacío si no hay); `null` en listados y en la emisión") List<EventoDto> eventos,
        @Schema(example = "Entrega en almacén central.", description = "Observaciones impresas en el PDF (solo las propias del comprobante), o `null`") String observaciones,
        @Schema(description = "Leyendas del catálogo 52 declaradas por el emisor (las automáticas no se listan): código y texto tal como van en el XML") List<LeyendaDto> leyendas,
        @Schema(description = "Solo en exportaciones (0200–0208): Incoterm y país de uso del servicio; `null` en las demás") ExportacionDto exportacion,
        @Schema(description = "Solo en una liquidación de compra (04): el resto del domicilio del vendedor (que va en `receptor`), el lugar de la operación, lo que el comprador le retiene y el neto que le paga (igual a `totales.total`); `null` en los demás") LiquidacionDto liquidacion,
        @Schema(description = "Solo al consultar: el envío automático del comprobante a su cliente (`PUT /v1/empresa/envio-al-cliente`), o `null` si no se anotó (la empresa no lo tenía activado, el comprobante no traía correo o SUNAT aún no lo aceptó)") EnvioAlClienteDto envioAlCliente,
        @Schema(description = "Solo en una retención (20) o una percepción (40): régimen, tasa, cada documento pagado o cobrado con lo retenido o percibido y el neto, y los totales en soles (`totales.total` es lo retenido o percibido); `null` en los demás") AgenteDto agente,
        @Schema(example = "{\"xml\": \"/v1/facturas/{id}/xml\", \"pdf\": \"/v1/facturas/{id}/pdf\", \"cdr\": \"/v1/facturas/{id}/cdr\"}", description = "cdr solo está presente cuando SUNAT emitió la constancia") Map<String, String> enlaces) {
    @Schema(name = "FormaPagoRespuesta")
    public record FormaPagoDto(
            @Schema(example = "credito", description = "contado | credito") String tipo,
            @Schema(example = "1180.00", description = "Solo al crédito") BigDecimal montoPendiente,
            @Schema(description = "Solo al crédito; el identificador SUNAT es Cuota001, Cuota002…") List<CuotaDto> cuotas) {
        @Schema(name = "CuotaRespuesta")
        public record CuotaDto(@Schema(example = "Cuota001") String id, @Schema(example = "590.00") BigDecimal monto, @Schema(example = "2026-10-15") LocalDate vencimiento) {}

        static FormaPagoDto de(FormaPago f) {
            List<CuotaDto> cs = new ArrayList<>();
            for (int k = 0; k < f.cuotas().size(); k++) cs.add(new CuotaDto(FormaPago.idCuota(k + 1), f.cuotas().get(k).monto(), f.cuotas().get(k).vencimiento()));
            return new FormaPagoDto(f.tipo().name().toLowerCase(Locale.ROOT), f.montoPendiente(), cs);
        }
    }

    @Schema(name = "DetraccionRespuesta")
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

    @Schema(name = "RetencionRespuesta")
    public record RetencionDto(@Schema(example = "3") BigDecimal porcentaje, @Schema(example = "35.40") BigDecimal monto,
                               @Schema(example = "1144.60", description = "Importe total menos la retención") BigDecimal netoCobrar) {}

    @Schema(name = "PercepcionRespuesta")
    public record PercepcionDto(@Schema(example = "51") String regimen, @Schema(example = "Percepción venta interna") String descripcion,
                                @Schema(example = "2") BigDecimal porcentaje, @Schema(example = "1180.00") BigDecimal base, @Schema(example = "23.60") BigDecimal monto,
                                @Schema(example = "1203.60", description = "Importe total más la percepción: lo que paga el cliente") BigDecimal totalConPercepcion) {}

    @Schema(name = "AnticipoRespuesta")
    public record AnticipoDto(@Schema(example = "F001-120", description = "Factura de anticipo (serie-número)") String comprobante,
                              @Schema(example = "F001") String serie, @Schema(example = "120") Long numero,
                              @Schema(example = "1000.00", description = "Valor sin IGV que se descuenta de la base") BigDecimal monto,
                              @Schema(example = "1180.00", description = "Importe que el cliente pagó con el anticipo (IGV incluido)") BigDecimal importePagado,
                              @Schema(example = "gravado", description = "`gravado`, `exonerado` o `inafecto`") String afectacion,
                              @Schema(example = "04", description = "Código SUNAT del descuento global por anticipo (catálogo 53: 04/05/06)") String codigoSunat,
                              @Schema(example = "2026-09-01") LocalDate fechaPago) {
        // AnticipoCalculado, no Anticipo a secas: importePagado() debe calcularse con la tasa de IGV real del comprobante
        // (Totales.tasaIgv), no con la general fija — si no, una empresa del padrón (10 %/10.5 %) recibiría un importe
        // pagado incorrecto en la respuesta.
        static AnticipoDto de(Totales.AnticipoCalculado ac) {
            Anticipo a = ac.anticipo();
            return new AnticipoDto(a.comprobante(), a.serie(), a.numero(), a.monto(), ac.importePagado(), a.afectacion().name().toLowerCase(), a.codigoSunat(), a.fechaPago());
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

    /** Datos propios de una nota: el comprobante que modifica (cac:BillingReference) y el motivo (cac:DiscrepancyResponse). */
    public record NotaDto(
            @Schema(example = "01", description = "Tipo del documento modificado (catálogo 01); hoy siempre `01` factura") String tipoAfectado,
            @Schema(example = "F001-123", description = "Serie-número de la factura modificada") String documentoAfectado,
            @Schema(example = "01", description = "Motivo: catálogo 09 en notas de crédito, 10 en notas de débito") String motivo,
            @Schema(example = "Anulación de la operación", description = "Descripción oficial del motivo") String motivoDescripcion,
            @Schema(example = "El cliente devolvió la mercadería", description = "Sustento indicado al emitir") String descripcion) {
        static NotaDto de(Comprobante c) {
            Nota n = c.nota();
            return n == null ? null : new NotaDto(n.tipoAfectado().codigo(), n.documentoAfectado(), n.motivo(), n.descripcionMotivo(c.tipo()), n.descripcion());
        }
    }

    /** Nota emitida sobre una factura, tal como aparece al consultar la factura. */
    public record NotaResumenDto(
            @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
            @Schema(example = "07", description = "`07` nota de crédito, `08` nota de débito") String tipo,
            @Schema(example = "FC01-4") String comprobante,
            @Schema(example = "2026-09-18") LocalDate fechaEmision,
            @Schema(example = "01") String motivo,
            @Schema(example = "Anulación de la operación") String motivoDescripcion,
            @Schema(example = "ACEPTADO") String estadoDocumento,
            @Schema(example = "1180.00", description = "Importe total de la nota") BigDecimal total) {
        static NotaResumenDto de(Comprobante n) {
            return new NotaResumenDto(n.id(), n.tipo().codigo(), n.serie() + "-" + n.numero(), n.fechaEmision(), n.nota().motivo(),
                    n.nota().descripcionMotivo(n.tipo()), n.estado().name(), n.totales().total());
        }
    }

    public record ReceptorDto(
            @Schema(example = "6", description = "Catálogo 06 SUNAT: 6=RUC, 1=DNI") String tipoDoc,
            @Schema(example = "20554198211") String numDoc,
            @Schema(example = "CORPORACION GRAFICA ANDINA S.A.C.") String razonSocial,
            @Schema(example = "Av. Argentina 2450, Lima") String direccion,
            @Schema(example = "PE", description = "País del adquirente (ISO 3166-1, catálogo 04), o `null`") String pais,
            @Schema(example = "compras@andina.pe", description = "Correo del adquirente indicado al emitir (una nota hereda el de su comprobante), o `null`. No va al XML") String email) {}

    /**
     * El envío automático del comprobante a su cliente (#325), si se anotó: la empresa lo tenía activado y el comprobante traía correo cuando SUNAT lo aceptó.
     */
    public record EnvioAlClienteDto(
            @Schema(example = "compras@andina.pe") String destinatario,
            @Schema(example = "ENVIADO", description = "`PENDIENTE` (en cola o esperando un reintento), `ENVIADO` (el servidor de correo lo recibió), `FALLIDO` (agotó los reintentos o los adjuntos superan el tope; `ultimo_error` dice por qué) u `OMITIDO` (no correspondía mandarlo al llegar su turno: el servidor no tiene correo configurado o el comprobante ya no estaba aceptado)") String estado,
            @Schema(example = "1", description = "Intentos de envío realizados (hasta 5)") int intentos,
            @Schema(example = "null", description = "Motivo del último fallo o de la omisión, o `null`") String ultimoError,
            @Schema(example = "2026-09-13T15:01:00Z", description = "Solo si sigue `PENDIENTE`: cuándo se intenta (UTC)") java.time.Instant siguienteIntento,
            @Schema(example = "2026-09-13T15:00:12Z", description = "Cuándo salió (UTC), o `null`") java.time.Instant enviadoEn) {
        public static EnvioAlClienteDto de(EnvioAlCliente e) {
            return e == null ? null : new EnvioAlClienteDto(e.destinatario(), e.estado().name(), e.intentos(), e.ultimoError(),
                    e.estado() == EnvioAlCliente.Estado.PENDIENTE ? e.siguienteIntento() : null, e.enviadoEn());
        }
    }

    @Schema(name = "ExportacionRespuesta")
    public record ExportacionDto(
            @Schema(example = "FOB", description = "Incoterm 2020 de la venta, o `null`") String incoterm,
            @Schema(example = "US", description = "País de uso del servicio (solo 0201/0208), o `null`") String paisUso) {
        static ExportacionDto de(pe.factura.domain.documento.Exportacion e) { return e == null ? null : new ExportacionDto(e.incoterm(), e.paisUso()); }
    }

    /**
     * #327: lo propio de una liquidación de compra. El vendedor (documento, nombre y dirección) va en {@code receptor}; aquí el ubigeo y la condición de su domicilio, el lugar de
     * la operación y las retenciones: el comprador retiene el IGV y la renta, y paga el neto.
     */
    @Schema(name = "LiquidacionCompraRespuesta")
    public record LiquidacionDto(
            @Schema(example = "060101", description = "Ubigeo del domicilio del vendedor (catálogo 13)") String ubigeoVendedor,
            @Schema(example = "02", description = "Condición del domicilio del vendedor (catálogo 60: 01 punto de venta, 02 producción, 03 extracción, 04 explotación, 05 otros)") String condicionVendedor,
            LugarDto lugarOperacion,
            @Schema(example = "1180.00", description = "Subtotal de la liquidación: valor de venta + IGV (TaxInclusiveAmount)") BigDecimal subtotal,
            @Schema(example = "180.00", description = "IGV que el comprador retiene y paga en lugar del vendedor") BigDecimal igvRetenido,
            RetencionRentaDto retencionRenta,
            @Schema(example = "985.00", description = "Importe neto que se paga al vendedor: subtotal − IGV − renta + redondeo (PayableAmount, regla 4312); el mismo `totales.total`") BigDecimal netoPagar) {
        @Schema(name = "LugarDeOperacionRespuesta")
        public record LugarDto(@Schema(example = "Fundo Santa Rosa, km 12") String direccion, @Schema(example = "060101") String ubigeo, @Schema(example = "02", description = "Catálogo 60") String condicion) {}

        @Schema(name = "RetencionRentaRespuesta")
        public record RetencionRentaDto(@Schema(example = "1.5", description = "Tasa en porcentaje") BigDecimal tasa,
                                        @Schema(example = "1000.00", description = "Base: el valor de venta de las líneas") BigDecimal base,
                                        @Schema(example = "15.00", description = "Retención del Impuesto a la Renta (tributo 3000): la suma de la de cada línea") BigDecimal monto) {}

        static LiquidacionDto de(Comprobante c) {
            LiquidacionCompra lc = c.liquidacion();
            if (lc == null) return null;
            return new LiquidacionDto(lc.ubigeoVendedor(), lc.condicionVendedor(),
                    new LugarDto(lc.lugarOperacion().direccion(), lc.lugarOperacion().ubigeo(), lc.lugarOperacion().condicion()),
                    c.totales().totalPrecioVenta(), c.totales().igv(), new RetencionRentaDto(lc.tasaRetencionRenta(), lc.baseRetencion(c.totales()), c.retencionRenta()), c.totales().total());
        }
    }

    /**
     * #40: lo propio de una retención o una percepción. La contraparte (proveedor o cliente) va en {@code receptor}; aquí el régimen y su tasa, cada documento pagado o cobrado con lo
     * retenido o percibido y el neto, y los totales, todo en soles salvo el importe y el pago de cada documento, que van en su moneda.
     */
    @Schema(name = "ComprobanteDeAgenteRespuesta")
    public record AgenteDto(
            @Schema(example = "01", description = "Régimen: catálogo 23 en la retención (01 tasa 3 %, 02 tasa 6 %), catálogo 22 en la percepción (01 venta interna 2 %, 02 combustible 1 %, 03 tasa especial 0.5 %)") String regimen,
            @Schema(example = "Tasa 3%", description = "Descripción del régimen en su catálogo") String descripcionRegimen,
            @Schema(example = "3", description = "Tasa en porcentaje") BigDecimal tasa,
            @Schema(example = "35.40", description = "Importe total retenido o percibido, en soles (`cbc:TotalInvoiceAmount`); el mismo `totales.total`") BigDecimal importeTotal,
            @Schema(example = "0.00", description = "Redondeo del importe total pagado o cobrado (`PayableRoundingAmount`), entre −1.00 y 1.00") BigDecimal redondeo,
            @Schema(example = "1144.60", description = "Importe total pagado al proveedor (retención) o cobrado al cliente (percepción), en soles: la suma de los netos más el redondeo") BigDecimal importeNeto,
            List<DocumentoDto> documentos) {
        @Schema(name = "DocumentoDeAgenteRespuesta")
        public record DocumentoDto(
                @Schema(example = "01", description = "Catálogo 01: 01 factura, 03 boleta (solo en la percepción), 08 nota de débito, 12 ticket") String tipo,
                @Schema(example = "F001-123") String numero,
                @Schema(example = "2026-10-05") LocalDate fechaEmision,
                @Schema(example = "1180.00", description = "Importe total del documento, en su moneda") BigDecimal importeTotal,
                @Schema(example = "PEN") String moneda,
                PagoDto pago,
                @Schema(description = "Solo fuera de soles: soles por unidad de `moneda` con que se convirtió el pago, y su fecha; `null` en soles") TipoCambioDto tipoCambio,
                @Schema(example = "35.40", description = "Lo retenido o percibido de este pago, en soles") BigDecimal importeAgente,
                @Schema(example = "1144.60", description = "Neto pagado (pago − retenido) o cobrado (pago + percibido), en soles") BigDecimal neto) {}

        @Schema(name = "PagoDeDocumentoRespuesta")
        public record PagoDto(@Schema(example = "1") long numero, @Schema(example = "2026-10-20") LocalDate fecha,
                              @Schema(example = "1180.00", description = "Importe pagado o cobrado sin la retención o la percepción, en la moneda del documento") BigDecimal importe) {}

        @Schema(name = "TipoCambioDeDocumentoRespuesta")
        public record TipoCambioDto(@Schema(example = "3.756") BigDecimal factor, @Schema(example = "2026-10-20") LocalDate fecha) {}

        static AgenteDto de(Comprobante c) {
            ComprobanteDeAgente a = c.agente();
            if (a == null) return null;
            return new AgenteDto(a.regimen(), a.descripcionRegimen(), a.tasa(), c.totales().total(), c.totales().redondeo(), c.importeNetoDeAgente(),
                    a.documentos().stream().map(d -> new DocumentoDto(d.tipo(), d.numero(), d.fechaEmision(), d.importeTotal(), d.moneda(),
                            new PagoDto(d.numeroPago(), d.fechaPago(), d.importePago()), d.enSoles() ? null : new TipoCambioDto(d.tipoCambio(), d.fechaTipoCambio()),
                            d.importeDelAgente(a.tasa()), d.neto(c.tipo(), a.tasa()))).toList());
        }
    }

    /** Descuento tal como se aplicó: lo enviado (tipo/valor), el monto resultante y el código SUNAT del catálogo 53. */
    @Schema(name = "DescuentoRespuesta")
    public record DescuentoDto(
            @Schema(example = "PORCENTAJE", description = "PORCENTAJE | MONTO") String tipo,
            @Schema(example = "10") BigDecimal valor,
            @Schema(example = "100.00", description = "Monto del descuento sin IGV") BigDecimal monto,
            @Schema(example = "true") boolean afectaBaseIgv,
            @Schema(example = "00", description = "Catálogo 53: 00/01 por línea, 02/03 global") String codigo) {}

    /** Cargo tal como se aplicó: lo enviado (tipo/valor), el monto resultante y el código SUNAT del catálogo 53. */
    @Schema(name = "CargoRespuesta")
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

    @Schema(name = "ItemRespuesta")
    public record ItemDto(
            @Schema(example = "SRV-001") String codigo,
            @Schema(example = "Servicio de consultoría") String descripcion,
            @Schema(example = "ZZ") String unidad,
            @Schema(example = "1.00") BigDecimal cantidad,
            @Schema(example = "2000.00") BigDecimal precioUnitario,
            @Schema(example = "10", description = "Afectación del IGV, catálogo 07: `10` gravado, `17` IVAP, `20` exonerado, `30` inafecto, `40` exportación; gratuitas `11`–`16` (gravadas), `21` (exonerada), `31`–`37` (inafectas)") String tipoAfectacionIgv,
            @Schema(example = "1000.00", description = "Valor de venta de la línea sin IGV, neto de descuento que afecta la base y con los cargos 47 (en gratuitas, el valor referencial)") BigDecimal valorVenta,
            @Schema(example = "180.00", description = "IGV de la línea; en gratuitas gravadas se informa pero no se cobra. En una línea IVAP (afectación 17) este campo trae el IVAP (4 %), no el IGV") BigDecimal igv,
            @Schema(example = "1180.00", description = "Lo que paga el cliente por la línea, con cargos 48 (0.00 en gratuitas)") BigDecimal precioVenta,
            @Schema(example = "false", description = "true si la afectación es gratuita (11–16, 21, 31–37)") boolean gratuita,
            DescuentoDto descuento,
            @Schema(description = "Cargos de la línea aplicados, si los hubo") List<CargoDto> cargos,
            @Schema(description = "ISC de la línea, si lo tiene") IscDto isc,
            @Schema(example = "0.00", description = "ICBPER de la línea (bolsas × monto vigente)") BigDecimal icbper,
            @Schema(example = "15101505", description = "Código de producto SUNAT (catálogo 25), o `null`") String codigoSunat,
            @Schema(description = "GTIN del producto, o `null`") GtinDto gtin,
            @Schema(description = "Datos de recursos hidrobiológicos (solo con tipo de operación 1002), o `null`") Hidrobiologico hidrobiologico,
            @Schema(description = "Datos del servicio de transporte de carga (solo con tipo de operación 1004), o `null`") TransporteCarga transporte,
            @Schema(description = "Otros tributos (9999) de la línea, o `null`") OtrosTributosRespuestaDto otrosTributos,
            @Schema(example = "ABC-123", description = "Placa del vehículo (propiedad 7000), o `null`") String placa) {}

    /** Otros tributos de una línea: la tasa pedida y el monto que khipu calculó sobre el valor de venta. */
    public record OtrosTributosRespuestaDto(@Schema(example = "3.5") BigDecimal tasa, @Schema(example = "3.50") BigDecimal monto) {}

    @Schema(name = "GtinRespuesta")
    public record GtinDto(@Schema(example = "GTIN-13") String tipo, @Schema(example = "7750182000123") String codigo) {}

    /**
     * Devuelve lo que hace falta para volver a pedir el mismo ISC en una nota (`FacturaRequest.IscDto`): en el sistema 02 la
     * tasa es derivada (monto ÷ base) y lo que el dominio exige es el {@code monto_unitario}; sin él, una NC parcial sobre una
     * línea con ISC de monto fijo no se podía construir desde la respuesta.
     */
    @Schema(name = "IscRespuesta")
    public record IscDto(@Schema(example = "01") String sistema,
                         @Schema(example = "35", description = "Tasa aplicada en %: la enviada en los sistemas 01 y 03, derivada (monto ÷ base) en el 02") BigDecimal tasa,
                         @Schema(example = "350.00") BigDecimal monto,
                         @Schema(example = "1000.00", description = "Base del ISC de la línea (valor de venta, o PVP sugerido × cantidad en el sistema 03)") BigDecimal base,
                         @Schema(example = "3.50", description = "Solo sistema 03: PVP sugerido unitario") BigDecimal basePvp,
                         @Schema(example = "2.25", description = "Solo sistema 02: importe fijo por unidad, tal como se envió") BigDecimal montoUnitario) {}

    public record CdrDto(
            @Schema(example = "0", description = "Código de respuesta SUNAT: `0` aceptado; 2000–3999 rechazado (corregir y reemitir); 4000+ aceptado con observaciones; 1000–1999 error del emisor (fault, sin CDR)") String codigo,
            @Schema(example = "La Factura numero F001-125, ha sido aceptada", description = "Descripción oficial de SUNAT") String descripcion,
            List<String> observaciones,
            @Schema(example = "RC-20260921-1", description = "Solo en una boleta (o nota de boleta) informada en un resumen diario: el identificador de ese resumen. SUNAT no emite un CDR por boleta en el resumen: el CDR del comprobante es el del resumen, y `descripcion` habla del resumen («El Resumen diario RC-…, ha sido aceptado»). Ausente cuando el CDR es el del propio comprobante (factura, nota de factura o boleta enviada sola)") String resumen) {}

    public record LeyendaDto(@Schema(example = "2001") String codigo, @Schema(example = "BIENES TRANSFERIDOS EN LA AMAZONÍA REGIÓN SELVA PARA SER CONSUMIDOS EN LA MISMA") String texto) {}

    public record TotalesDto(
            @Schema(example = "1000.00") BigDecimal gravado,
            @Schema(example = "0.00") BigDecimal exonerado,
            @Schema(example = "0.00") BigDecimal inafecto,
            @Schema(example = "180.00") BigDecimal igv,
            @Schema(example = "18.00", description = "Tasa del IGV aplicada a las líneas gravadas, en porcentaje: 18.00, o la reducida del Padrón de Tasa Especial (restaurantes y hoteles) si la empresa la tiene activa; una nota usa la de su factura. En un comprobante IVAP (afectación 17) no se aplica: no hay líneas al IGV y la tasa del IVAP (4 %, fija) va en `ivap`") BigDecimal tasaIgv,
            @Schema(example = "0.00", description = "Base de las operaciones gratuitas (tributo 9996): no se cobra") BigDecimal gratuito,
            @Schema(example = "0.00", description = "IGV de las operaciones gratuitas gravadas: solo informativo, no se cobra") BigDecimal igvGratuitas,
            @Schema(example = "0.00", description = "Total IVAP (tributo 1016, 4 % sobre la venta de arroz pilado, afectación 17): sustituye al IGV en el comprobante") BigDecimal ivap,
            @Schema(example = "0.00", description = "Total valor de venta de exportación (tributo 9995, afectación 40, sin IGV)") BigDecimal exportacion,
            @Schema(example = "0.00", description = "Total ISC (se suma al precio de venta y a la base del IGV)") BigDecimal isc,
            @Schema(example = "0.00", description = "Total ICBPER (bolsas de plástico)") BigDecimal icbper,
            @Schema(example = "0.00", description = "Total de otros tributos (9999): suma al precio de venta y no entra en la base del IGV") BigDecimal otrosTributos,
            @Schema(example = "1000.00", description = "Total valor de venta onerosa (suma de bases, LineExtensionAmount)") BigDecimal totalValorVenta,
            @Schema(example = "1180.00", description = "Total precio de venta = valor de venta + tributos (TaxInclusiveAmount)") BigDecimal totalPrecioVenta,
            @Schema(example = "0.00", description = "Descuentos que no afectan la base (línea 01 + global 03), AllowanceTotalAmount") BigDecimal totalDescuentos,
            @Schema(example = "0.00", description = "Cargos que no afectan la base (línea 48 + globales 46/50), ChargeTotalAmount") BigDecimal totalCargos,
            @Schema(example = "0.00", description = "Suma de los importes ya pagados con facturas de anticipo, IGV incluido (PrepaidAmount)") BigDecimal totalAnticipos,
            @Schema(example = "0.00", description = "Redondeo aplicado al importe total (PayableRoundingAmount), entre −1.00 y 1.00") BigDecimal redondeo,
            @Schema(example = "1180.00", description = "Importe a pagar (PayableAmount) = precio de venta + cargos − descuentos que no afectan la base − anticipos + redondeo") BigDecimal total,
            @Schema(description = "Descuento global aplicado, si lo hubo") DescuentoDto descuentoGlobal,
            @Schema(description = "Cargos globales aplicados, si los hubo") List<CargoDto> cargos) {}

    public static ComprobanteResponse de(Comprobante c, String base) { return de(c, base, null, null, null); }

    public static ComprobanteResponse de(Comprobante c, String base, List<Comprobante> notas, ComunicacionBaja baja) { return de(c, base, notas, baja, null); }

    /** Un cambio de estado del comprobante (#4): cuándo, de qué estado a cuál y por qué (motivo del error, CDR, baja…). */
    public record EventoDto(
            @Schema(example = "2026-09-13T15:00:05Z", description = "Instante del cambio (UTC)") java.time.Instant fecha,
            @Schema(example = "FIRMADO", description = "Estado previo, o `null` en el primer evento") String estadoAnterior,
            @Schema(example = "ERROR_ENVIO") String estadoResultante,
            @Schema(example = "SUNAT no disponible (timeout)", description = "Motivo o resultado: error de envío, código y descripción del CDR, baja aceptada…") String mensaje) {
        static EventoDto de(EventoDocumento e) {
            return new EventoDto(e.ocurridoEn(), e.estadoAnterior() == null ? null : e.estadoAnterior().name(), e.estadoNuevo().name(), e.detalle());
        }
    }

    /** Con {@code notas} (las emitidas sobre esta factura) y {@code baja} (la última comunicación de baja) solo al consultar un comprobante concreto. */
    public static ComprobanteResponse de(Comprobante c, String base, List<Comprobante> notas, ComunicacionBaja baja, List<EventoDocumento> eventos) {
        return de(c, base, notas, baja, eventos, null);
    }

    /** Con {@code envio} (el envío automático al cliente, #325) solo al consultar un comprobante concreto. */
    public static ComprobanteResponse de(Comprobante c, String base, List<Comprobante> notas, ComunicacionBaja baja, List<EventoDocumento> eventos, EnvioAlCliente envio) {
        String p = base == null ? null : base + "/" + c.id();
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.fechaVencimiento(), c.fechaLimiteEnvio(), c.moneda(), c.tipoOperacion(), c.tipoCambio(), c.totalEnSoles().orElse(null),
                de(c.receptor(), c.correoCliente()), c.totales().items().stream().map(ComprobanteResponse::de).toList(),
                c.estado().name(), c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones(), c.resumenDelCdr().orElse(null)),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(), c.tasaIgv(),
                        c.totales().gratuito(), c.totales().igvGratuitas(), c.totales().ivap(), c.totales().exportacion(), c.totales().isc(), c.totales().icbper(), c.totales().otrosTributos(), c.totales().totalValorVenta(), c.totales().totalPrecioVenta(), c.totales().totalDescuentos(), c.totales().totalCargos(), c.totales().totalAnticipos(), c.totales().redondeo(), c.totales().total(),
                        c.totales().descuentoGlobal() == null ? null : new DescuentoDto(c.totales().descuentoGlobal().descuento().tipo().name(),
                                c.totales().descuentoGlobal().descuento().valor(), c.totales().descuentoGlobal().monto(),
                                c.totales().descuentoGlobal().afectaBase(), c.totales().descuentoGlobal().codigo()),
                        CargoDto.de(c.totales().cargosGlobales())),
                FormaPagoDto.de(c.formaPago()),
                DetraccionDto.de(c.detraccion()),
                c.retencion() == null ? null : new RetencionDto(c.retencion().porcentaje(), c.retencion().monto(), c.totales().total().subtract(c.retencion().monto())),
                c.percepcion() == null ? null : new PercepcionDto(c.percepcion().regimen(), c.percepcion().descripcionRegimen(), c.percepcion().porcentaje(),
                        c.percepcion().base(), c.percepcion().monto(), c.percepcion().totalConPercepcion(c.totales().total())),
                c.anticipos().isEmpty() ? null : c.totales().anticipos().stream().map(AnticipoDto::de).toList(),
                ReferenciasDto.de(c.referencias()),
                NotaDto.de(c),
                notas == null ? null : notas.stream().map(NotaResumenDto::de).toList(),
                baja == null ? null : BajaResponse.de(baja),
                eventos == null ? null : eventos.stream().map(EventoDto::de).toList(),
                c.observaciones(),
                c.leyendas().stream().map(l -> new LeyendaDto(l, Leyenda.texto(l))).toList(),
                ExportacionDto.de(c.exportacion()),
                LiquidacionDto.de(c),
                EnvioAlClienteDto.de(envio),
                AgenteDto.de(c),
                enlaces(c, p));
    }

    /** `cdr` solo cuando existe la constancia: un rechazo por SOAPFault trae código y descripción pero SUNAT no emitió CDR. */
    private static Map<String, String> enlaces(Comprobante c, String p) {
        if (p == null) return Map.of();   // #405: el cálculo no guarda nada, no hay a dónde enlazar
        return c.cdrKey() == null ? Map.of("xml", p + "/xml", "pdf", p + "/pdf") : Map.of("xml", p + "/xml", "pdf", p + "/pdf", "cdr", p + "/cdr");
    }

    private static ReceptorDto de(Receptor r, String email) {
        return r == null ? null : new ReceptorDto(r.tipoDoc(), r.numDoc(), r.razonSocial(), r.direccion(), r.pais(), email);
    }

    private static ItemDto de(ItemCalculado ic) {
        Item i = ic.item();
        DescuentoDto d = i.tieneDescuento()
                ? new DescuentoDto(i.descuento().tipo().name(), i.descuento().valor(), ic.descuento(), i.descuento().afectaBaseIgv(), i.descuento().codigoSunat(false))
                : null;
        IscDto isc = ic.tieneIsc() ? new IscDto(i.isc().sistema(), ic.iscPorcentaje(), ic.isc(), ic.iscBase(), i.isc().basePvp(), i.isc().montoUnitario()) : null;
        return new ItemDto(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo(), ic.valorVenta(), ic.igv(), ic.precioVenta(), ic.gratuita(), d, CargoDto.de(ic.cargos()), isc, ic.icbper(),
                i.tieneCodigoSunat() ? i.codigoSunat().codigo() : null, i.gtin() == null ? null : new GtinDto(i.gtin().tipo(), i.gtin().codigo()),
                i.hidrobiologico(), i.transporte(), i.tieneOtrosTributos() ? new OtrosTributosRespuestaDto(i.otrosTributos().tasa(), ic.otrosTributos()) : null, i.placa());
    }
}
