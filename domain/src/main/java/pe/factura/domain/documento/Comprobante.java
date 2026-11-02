package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Comprobante {
    private final UUID id;
    private final UUID tenantId;
    private final TipoDocumento tipo;
    private final String serie;
    private Long numero;
    private final LocalDate fechaEmision;
    /** Hora local (zona del reloj de la aplicación) en que se creó el comprobante: cbc:IssueTime, informativa para SUNAT. */
    private final LocalTime horaEmision;
    /** Fecha de vencimiento del pago (cbc:DueDate, campo 8): informativa, sin validación SUNAT; nunca anterior a la emisión. */
    private final LocalDate fechaVencimiento;
    private final String moneda;
    private final String tipoOperacion;
    private final Receptor receptor;
    private final List<Item> items;
    private final FormaPago formaPago;
    private final Descuento descuentoGlobal;
    private final List<Cargo> cargos;
    private final Detraccion detraccion;
    private final RetencionIgv retencion;
    private final Percepcion percepcion;
    private final List<Anticipo> anticipos;
    /** Orden de compra, guías de remisión y otros documentos relacionados; nunca nulo. */
    private final Referencias referencias;
    /** Solo en notas de crédito/débito: comprobante que modifican y motivo; {@code null} en facturas. */
    private final Nota nota;
    /** Solo en la liquidación de compra (#327): domicilio del vendedor, lugar de la operación y tasa de la retención de renta; {@code null} en los demás. */
    private final LiquidacionCompra liquidacion;
    /** Solo en la retención (20) y la percepción (40), #40: régimen, tasa y los documentos que el agente paga o cobra; {@code null} en los demás. */
    private final ComprobanteDeAgente agente;
    /** Leyendas del catálogo 52 declaradas por el emisor (2001–2005, 2008…); nunca nula. Las automáticas las decide la plantilla. */
    private final List<String> leyendas;
    /** Incoterm y país de uso de una factura de exportación (0200–0208); {@code null} en las demás. Una nota hereda los de su factura. */
    private final Exportacion exportacion;
    /** Tasa del IGV aplicada a las líneas gravadas, en porcentaje ({@link TasaIgv}); una nota hereda la de la factura que modifica. */
    private final BigDecimal tasaIgv;
    /**
     * Soles por unidad de la moneda del comprobante (#316), de hasta 3 enteros y 3 decimales; {@code null} en soles y cuando el emisor no lo informó. El XML de SUNAT no lo
     * lleva: sirve para el registro de ventas (SIRE), el equivalente en soles del PDF y el tope de S/ 700 de una boleta sin documento en otra moneda. Una nota hereda el
     * de su documento.
     */
    private BigDecimal tipoCambio;
    private final Totales totales;
    private EstadoDocumento estado;
    private String hash;
    private String nombreArchivo;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;
    /** Texto libre que solo va a la representación impresa (bloque "Observaciones"); no forma parte del XML firmado. */
    private String observaciones;
    /**
     * Correo del cliente (#325), normalizado ({@link CorreoDelCliente}), o {@code null}: a dónde se envían el PDF, el XML y el CDR cuando SUNAT lo acepta, si la empresa activó el envío
     * automático. No va al XML ni a SUNAT. Una nota hereda el de su documento.
     */
    private String correoCliente;
    /** Cambios de estado ocurridos en memoria y aún no persistidos (#4); el repositorio los vacía al guardar. */
    private final List<EventoDocumento> eventosPendientes = new ArrayList<>();

    private Comprobante(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision, LocalTime horaEmision, LocalDate fechaVencimiento,
                        String moneda, String tipoOperacion, Receptor receptor, List<Item> items, FormaPago formaPago,
                        Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencion, Percepcion percepcion, List<Anticipo> anticipos,
                        Referencias referencias, BigDecimal redondeo, Nota nota, LiquidacionCompra liquidacion, ComprobanteDeAgente agente, BigDecimal tasaIgv, List<String> leyendas, Exportacion exportacion,
                        EstadoDocumento estado) {
        this.id = id; this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.numero = numero;
        this.fechaEmision = fechaEmision; this.horaEmision = horaEmision; this.fechaVencimiento = fechaVencimiento; this.moneda = moneda; this.tipoOperacion = tipoOperacion;
        this.receptor = receptor; this.items = List.copyOf(items); this.formaPago = formaPago; this.descuentoGlobal = descuentoGlobal;
        this.cargos = cargos == null ? List.of() : List.copyOf(cargos);
        this.anticipos = anticipos == null ? List.of() : List.copyOf(anticipos);
        this.referencias = referencias == null ? Referencias.ninguna() : referencias;
        this.nota = nota;
        // Una sola fuente de verdad: los datos de la liquidación van si y solo si el tipo es 04; si no, el neto, la plantilla y la baja lo leerían distinto.
        if ((tipo == TipoDocumento.LIQUIDACION_COMPRA) != (liquidacion != null))
            throw new IllegalStateException("Los datos de la liquidación de compra van si y solo si el comprobante es del tipo 04 (tipo " + tipo.codigo() + ")");
        this.liquidacion = liquidacion;
        // #40: lo mismo con la retención y la percepción, y el tipo de los datos es el del comprobante (un 20 no lleva los datos de un 40).
        if (tipo.deAgente() != (agente != null) || (agente != null && agente.tipo() != tipo))
            throw new IllegalStateException("Los datos del agente van si y solo si el comprobante es una retención (20) o una percepción (40), y del mismo tipo (tipo " + tipo.codigo() + ")");
        this.agente = agente;
        this.leyendas = leyendas == null ? List.of() : List.copyOf(leyendas);
        this.exportacion = exportacion;
        this.tasaIgv = TasaIgv.normalizar(tasaIgv);
        // #40: la retención y la percepción no tienen líneas de venta: su importe total es lo retenido o percibido (cbc:TotalInvoiceAmount), en soles.
        Totales calculados = agente != null ? Totales.deAgente(agente.importeTotal(), redondeo)
                : Totales.calcular(this.items, descuentoGlobal, this.cargos, this.anticipos, Icbper.tasaVigente(fechaEmision), redondeo, this.tasaIgv);
        // #388: SUNAT no admite descuentos ni cargos en una nota; sale con las líneas netas. Los ítems y el descuento global guardados son los de siempre.
        // #327: la liquidación de compra paga al vendedor el neto de lo que el comprador le retiene (IGV y renta, regla 4312).
        this.totales = nota != null ? calculados.paraNota() : liquidacion != null ? calculados.paraLiquidacion(liquidacion.retencionRenta(calculados)) : calculados;
        // Detracción, retención y percepción se completan contra el importe total ya calculado (montos por defecto, 3208 y tolerancias SUNAT).
        this.detraccion = detraccion == null ? null : detraccion.completarContra(moneda, this.totales.total());
        this.retencion = retencion == null ? null : retencion.completarContra(this.totales.total());
        this.percepcion = percepcion == null ? null : percepcion.completarContra(tipoOperacion, formaPago, moneda, this.totales.total());
        this.estado = estado;
    }

    /**
     * Único punto de entrada para crear una factura (#74): los datos obligatorios van en {@link #factura}, los opcionales
     * se encadenan en el {@link FacturaBuilder} y {@link FacturaBuilder#crear} aplica las reglas de emisión.
     */
    public static FacturaBuilder factura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor, List<Item> items) {
        return new FacturaBuilder(TipoDocumento.FACTURA, tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items);
    }

    /**
     * Boleta de venta (#20): mismo armado y mismas reglas de líneas, totales, detracción y percepción que la factura, y se envía igual, sola con sendBill. Cambian el
     * receptor ({@link Receptor#exigirValidoParaBoleta}, y sin documento solo hasta {@link #TOPE_BOLETA_SIN_DOCUMENTO}) y la regla del plazo (1079). Crédito, retención,
     * anticipos y exportación todavía no se emiten en boleta: se rechazan en vez de salir con un XML que nadie probó.
     */
    public static FacturaBuilder boleta(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor, List<Item> items) {
        return new FacturaBuilder(TipoDocumento.BOLETA, tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items);
    }

    /**
     * Tope de líneas de un comprobante: el número de orden de la línea (cbc:ID) es numérico de hasta 3 dígitos y distinto de cero. SUNAT rechaza más con 2023 en
     * la factura y la boleta (Factura2_0 fila 145, Boleta2_0 fila 145) y con 2137 en las notas (NotaCredito2_0 fila 161, NotaDebito2_0 fila 138).
     */
    public static final int MAX_LINEAS = 999;

    /** Hasta este importe (en soles) una boleta puede no identificar al comprador (guía del resumen diario, campos 12 y 13). */
    public static final BigDecimal TOPE_BOLETA_SIN_DOCUMENTO = new BigDecimal("700.00");

    public static final class FacturaBuilder {
        private final TipoDocumento tipo;
        private final UUID tenantId; private final String serie; private final LocalDate fechaEmision; private final String moneda; private final String tipoOperacion;
        private final Receptor receptor; private final List<Item> items;
        private LocalDate fechaVencimiento; private FormaPago formaPago = FormaPago.contado(); private Descuento descuentoGlobal; private List<Cargo> cargos = List.of();
        private Detraccion detraccion; private RetencionIgv retencion; private Percepcion percepcion; private List<Anticipo> anticipos = List.of();
        private Referencias referencias; private BigDecimal redondeo; private BigDecimal tasaIgv = TasaIgv.GENERAL; private List<String> leyendas = List.of();
        private Exportacion exportacion; private BigDecimal tipoCambio;

        private FacturaBuilder(TipoDocumento tipo, UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor, List<Item> items) {
            this.tipo = tipo; this.tenantId = tenantId; this.serie = serie; this.fechaEmision = fechaEmision; this.moneda = moneda; this.tipoOperacion = tipoOperacion; this.receptor = receptor; this.items = items;
        }
        public FacturaBuilder fechaVencimiento(LocalDate v) { this.fechaVencimiento = v; return this; }
        /** Si no se llama, la factura es al contado; llamarlo con nulo es un error del emisor (3244). */
        public FacturaBuilder formaPago(FormaPago fp) {
            if (fp == null) throw new DomainException("FORMA_PAGO_INVALIDA", "3244 - Debe consignar la forma de pago (contado o crédito)");
            this.formaPago = fp; return this;
        }
        public FacturaBuilder descuentoGlobal(Descuento d) { this.descuentoGlobal = d; return this; }
        public FacturaBuilder cargos(List<Cargo> c) { this.cargos = c == null ? List.of() : c; return this; }
        public FacturaBuilder detraccion(Detraccion d) { this.detraccion = d; return this; }
        public FacturaBuilder retencion(RetencionIgv r) { this.retencion = r; return this; }
        public FacturaBuilder percepcion(Percepcion p) { this.percepcion = p; return this; }
        public FacturaBuilder anticipos(List<Anticipo> a) { this.anticipos = a == null ? List.of() : a; return this; }
        public FacturaBuilder referencias(Referencias r) { this.referencias = r; return this; }
        public FacturaBuilder redondeo(BigDecimal r) { this.redondeo = r; return this; }
        /** Porcentaje ({@link TasaIgv}); nulo = general. */
        public FacturaBuilder tasaIgv(BigDecimal t) { this.tasaIgv = t == null ? TasaIgv.GENERAL : t; return this; }
        /** Códigos del catálogo 52 que declara el emisor ({@link Leyenda#validar}). */
        public FacturaBuilder leyendas(List<String> l) { this.leyendas = Leyenda.validar(l); return this; }
        /** Incoterm y país de uso ({@link Exportacion}); solo en los tipos de operación 0200–0208. */
        public FacturaBuilder exportacion(Exportacion e) { this.exportacion = e; return this; }
        /** Soles por unidad de {@code moneda} (#316); solo fuera de soles. Opcional. */
        public FacturaBuilder tipoCambio(BigDecimal t) { this.tipoCambio = t; return this; }

        public Comprobante crear(Clock clock) {
            FormaPago formaPago = this.formaPago;
            List<Anticipo> anticipos = this.anticipos;
            Detraccion detraccion = this.detraccion;
            Percepcion percepcion = this.percepcion;
            boolean boleta = tipo == TipoDocumento.BOLETA;
            String nombre = boleta ? "boleta" : "factura";
            if (!tipo.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "Serie de " + nombre + " inválida (" + (boleta ? "B" : "F") + "###): " + serie);
            if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(PlazoEnvio.de(tipo, null), fechaEmision, clock);
            if (fechaVencimiento != null && fechaVencimiento.isBefore(fechaEmision))
                throw new DomainException("FECHA_INVALIDA", "La fecha de vencimiento no puede ser anterior a la de emisión");
            if (items == null || items.isEmpty()) throw new DomainException("SIN_ITEMS", "La " + nombre + " debe tener al menos un ítem");
            exigirHastaElTopeDeLineas("2023", nombre, items);
            items.forEach(Item::exigirValidoParaFactura);
            String operacion = tipoOperacion == null ? "0101" : tipoOperacion;
            validarTipoOperacion(operacion, tipo);
            // 0202 y 0205 pasan: Exportacion.validar les da su mensaje propio (falta el huésped).
            if (!boleta && !OPERACIONES_EN_FACTURA.contains(operacion) && !Exportacion.CON_HUESPED.contains(operacion))
                throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " todavía no se emite en factura; en factura: " + String.join(", ", OPERACIONES_EN_FACTURA.stream().sorted().toList()));
            if (boleta) {
                exigirSoloLoQueEmiteUnaBoleta(operacion);
                exigirDocumentosRelacionadosDeBoleta();
                if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La boleta requiere el comprador: su documento o, hasta S/ 700, tipo_doc y num_doc «-»");
                receptor.exigirValidoParaBoleta();
                receptor.exigirSinRucEnExportacionOEnNoDomiciliado(operacion);
            } else {
                if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La factura requiere un receptor" + (Exportacion.es(operacion) ? "" : " con RUC"));
                receptor.exigirValidoParaFactura(operacion);
            }
            exigirMonedaYTipoCambio(moneda, tipoCambio);
            Exportacion.validar(exportacion, operacion);
            exigirAfectacionSegunOperacion(operacion, items);
            if (detraccion == null && Detraccion.TIPOS_OPERACION.contains(operacion))
                throw new DomainException("DETRACCION_INVALIDA", "3127 - El tipo de operación " + operacion + " exige los datos de la detracción (bien/servicio, porcentaje, monto y cuenta)");
            if (detraccion != null) detraccion.validarContra(operacion);
            Detraccion.validarDatosSectoriales(operacion, items);
            if (percepcion == null && Percepcion.TIPO_OPERACION.equals(operacion) && !formaPago.esCredito())
                throw new DomainException("PERCEPCION_INVALIDA", "3093 - Una operación sujeta a percepción (2001) al contado debe informar la percepción");
            if (anticipos != null && anticipos.stream().map(Anticipo::comprobante).distinct().count() < anticipos.size())
                throw new DomainException("ANTICIPO_INVALIDO", "3215 - La misma factura de anticipo aparece más de una vez");
            Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, tipo, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), fechaVencimiento,
                    moneda, operacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencion, percepcion, anticipos, referencias, redondeo, null, null, null, tasaIgv, leyendas, exportacion, EstadoDocumento.RECIBIDO);
            c.tipoCambio = tipoCambio;
            formaPago.validarContra(c.totales.total(), fechaEmision);
            // Sin tipo de cambio no hay cómo comparar otra moneda con S/ 700: fuera de soles se identifica siempre al comprador. Con él (#316) se mide en soles.
            if (boleta && receptor.sinDocumento() && c.superaElTopeSinDocumento())
                throw new DomainException("RECEPTOR_INVALIDO", "Una boleta de más de S/ " + TOPE_BOLETA_SIN_DOCUMENTO + " (o en otra moneda sin tipo_cambio) identifica al comprador con su documento; esta es de "
                        + moneda + " " + c.totales.total() + c.totalEnSoles().filter(s -> !Monedas.SOLES.equals(moneda)).map(s -> " (S/ " + s + ")").orElse(""));
            c.totales.items().forEach(ItemCalculado::exigirBasePvpValida);
            c.totales.items().forEach(ItemCalculado::exigirValorDeVentaPositivo);
            for (String l : leyendas) {
                String regla = Leyenda.EXIGEN_EXONERADO.get(l);
                if (regla != null && c.totales.exonerado().signum() <= 0)
                    throw new DomainException("LEYENDA_INVALIDA", "La leyenda " + l + " exige un total exonerado mayor a 0.00 (regla " + regla + ")");
            }
            return c;
        }

        /**
         * #362 B9, regla 4009 de Boleta2_0 (fila 140): el catálogo 12 de otros documentos relacionados de la boleta no incluye 06 ni 07, que la factura sí admite. SUNAT beta aceptó
         * con la observación 4009 una boleta con cada uno (B001-34 y B001-35); se rechazan antes de numerar. Las notas no llevan documentos relacionados.
         */
        private void exigirDocumentosRelacionadosDeBoleta() {
            if (referencias == null) return;
            for (DocumentoRelacionado d : referencias.otros())
                if (DocumentoRelacionado.TIPOS_SOLO_FACTURA.contains(d.tipo()))
                    throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4009 - El documento relacionado de tipo " + d.tipo() + " no se admite en una boleta (solo 04, 05, 08, 09 o 99, catálogo 12): "
                            + "SUNAT la observaría; es válido solo en factura");
        }

        /** Lo que la boleta todavía no emite: ninguno tiene reglas en Boleta2_0 que hayamos probado contra SUNAT (y la forma de pago ni existe en la boleta). */
        private void exigirSoloLoQueEmiteUnaBoleta(String operacion) {
            if (formaPago.esCredito()) throw new DomainException("FORMA_PAGO_INVALIDA", "Una boleta se emite al contado: la venta al crédito va en factura");
            if (retencion != null) throw new DomainException("RETENCION_INVALIDA", "La retención del IGV la hace un agente de retención con RUC: va en factura, no en boleta");
            // Lista blanca (274-H2): del catálogo 51 que aplica a boletas, solo aquellos cuyos datos khipu arma en el XML. Otros (exportación, 0401 a no
            // domiciliados, 0302 con medio de pago…) consumían número y SUNAT los rechazaba.
            if (!OPERACIONES_EN_BOLETA.contains(operacion))
                throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " todavía no se emite en boleta; en boleta: " + String.join(", ", OPERACIONES_EN_BOLETA.stream().sorted().toList()));
            // 3240 (Boleta2_0 fila 254): un contribuyente del NRUS no cobra el ICBPER. Solo está en la boleta: la factura no admite el 0113 y las notas no heredan la regla.
            if (OPERACION_NRUS.equals(operacion) && items.stream().anyMatch(Item::icbper))
                throw new DomainException("ITEM_INVALIDO", "3240 - El impuesto ICBPER no aplica para el NRUS (tipo de operación " + OPERACION_NRUS + "): quite el icbper de las líneas o emita con el tipo de operación 0101");
        }
    }

    /**
     * Venta interna, NRUS, exportación (sin 0202/0205, que piden el huésped), no domiciliados (0401), detracción (1001–1004) y percepción (2001): lo que khipu sabe armar
     * en una boleta. #296: e-beta aceptó la exportación y el 0401 con un comprador con pasaporte, con DNI y sin documento, y rechazó con 2800 al comprador con RUC. Quedan
     * fuera 0301, 0302 y 2100–2105 hasta que tengan sus datos.
     */
    static final java.util.Set<String> OPERACIONES_EN_BOLETA = java.util.Set.of("0101", "0113", "0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401", "1001", "1002", "1003", "1004", "2001");

    /**
     * #394: venta interna, exportación (sin hospedaje ni paquete turístico, que piden el huésped), no domiciliados (0401), detracción y percepción. e-beta rechazó
     * con el número ya consumido el 0112, 0301, 0302, 2002, 2100–2102, 2104 y 2106 porque khipu no arma sus datos; aceptó el 0401. El 2103 también lo aceptó, pero
     * Factura2_0 (fila 437, regla 1080) lo reserva a los RUC afiliados al SEE-Empresas supervisadas, que e-beta no cruza: no se emite.
     */
    static final java.util.Set<String> OPERACIONES_EN_FACTURA = java.util.Set.of("0101", "0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401",
            "1001", "1002", "1003", "1004", "2001");

    /** Venta interna - NRUS (catálogo 51): solo en boleta. */
    static final String OPERACION_NRUS = "0113";

    /**
     * Liquidación de compra (04, #327): el emisor —el tenant— compra a una persona sin RUC ({@code vendedor}: DNI, carné de extranjería o pasaporte, nombre y la dirección de su
     * domicilio) y le retiene el IGV y el Impuesto a la Renta. {@code datos} trae el resto del domicilio del vendedor, el lugar de la operación y la tasa de la retención. Único
     * punto de entrada: obligatorios aquí, opcionales en el {@link LiquidacionBuilder}, y {@link LiquidacionBuilder#crear} aplica las reglas de LiquidacionCompra2_0.
     */
    public static LiquidacionBuilder liquidacionCompra(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor vendedor,
                                                       LiquidacionCompra datos, List<Item> items) {
        return new LiquidacionBuilder(tenantId, serie, fechaEmision, moneda, tipoOperacion, vendedor, datos, items);
    }

    public static final class LiquidacionBuilder {
        private final UUID tenantId; private final String serie; private final LocalDate fechaEmision; private final String moneda; private final String tipoOperacion;
        private final Receptor vendedor; private final LiquidacionCompra datos; private final List<Item> items;
        private Referencias referencias; private BigDecimal redondeo; private BigDecimal tipoCambio; private BigDecimal tasaIgv = TasaIgv.GENERAL; private List<Anticipo> anticipos = List.of();

        private LiquidacionBuilder(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor vendedor, LiquidacionCompra datos, List<Item> items) {
            this.tenantId = tenantId; this.serie = serie; this.fechaEmision = fechaEmision; this.moneda = moneda; this.tipoOperacion = tipoOperacion;
            this.vendedor = vendedor; this.datos = datos; this.items = items;
        }
        /** Guías de remisión (09/31) y otros documentos (solo el 99 «otros»: el 10 es la liquidación de anticipo, que va con los anticipos). Sin orden de compra: la hoja no la tiene. */
        public LiquidacionBuilder referencias(Referencias r) { this.referencias = r; return this; }
        /** PayableRoundingAmount, |x| ≤ 1 (4314). */
        public LiquidacionBuilder redondeo(BigDecimal r) { this.redondeo = r; return this; }
        /** Soles por unidad de {@code moneda} (#316); solo fuera de soles. Opcional: no va al XML. */
        public LiquidacionBuilder tipoCambio(BigDecimal t) { this.tipoCambio = t; return this; }
        /** Porcentaje ({@link TasaIgv}); nulo = general. El servicio siempre pasa la vigente a la fecha, sin el padrón de tasa especial: quien vende es el vendedor sin RUC, no el tenant (regla 4310). */
        public LiquidacionBuilder tasaIgv(BigDecimal t) { this.tasaIgv = t == null ? TasaIgv.GENERAL : t; return this; }
        /** Liquidaciones de anticipo que se regularizarían (PrepaidPayment, descuentos 04–06 y 61): todavía no se emiten; con alguna, {@link #crear} rechaza. */
        public LiquidacionBuilder anticipos(List<Anticipo> a) { this.anticipos = a == null ? List.of() : a; return this; }

        public Comprobante crear(Clock clock) {
            TipoDocumento tipo = TipoDocumento.LIQUIDACION_COMPRA;
            if (!tipo.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "1001 - Serie de liquidación de compra inválida (L###): " + serie);
            if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(PlazoEnvio.de(tipo, null), fechaEmision, clock);
            if (items == null || items.isEmpty()) throw new DomainException("SIN_ITEMS", "La liquidación de compra debe tener al menos un ítem");
            exigirHastaElTopeDeLineas("2023", "liquidación de compra", items);
            items.forEach(Item::exigirValidoParaFactura);
            items.forEach(LiquidacionBuilder::exigirLineaDeLiquidacion);
            String operacion = tipoOperacion == null ? LiquidacionCompra.OPERACION_POR_DEFECTO : tipoOperacion;
            validarTipoOperacion(operacion, tipo);
            if (LiquidacionCompra.OPERACION_ORO.equals(operacion))
                throw new DomainException("TIPO_OPERACION_INVALIDO", "2466 - La compra de oro (0503) exige en cada línea el código, la ley, la naturaleza y el nombre del derecho minero (propiedades 6000, 6004, 6005 y 6006 del catálogo 55, reglas 2466–2469), que khipu todavía no arma; en liquidación de compra: "
                        + String.join(", ", LiquidacionCompra.OPERACIONES.stream().sorted().toList()));
            if (!LiquidacionCompra.OPERACIONES.contains(operacion))
                throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " todavía no se emite en liquidación de compra; en liquidación de compra: " + String.join(", ", LiquidacionCompra.OPERACIONES.stream().sorted().toList()));
            if (vendedor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La liquidación de compra requiere el vendedor: su documento (DNI, carné de extranjería o pasaporte), nombre y domicilio");
            vendedor.exigirValidoComoVendedor();
            if (datos == null) throw new DomainException("LIQUIDACION_INVALIDA", "2453 - La liquidación de compra lleva el ubigeo y la condición del domicilio del vendedor y el lugar de la operación");
            datos.validar();
            exigirMonedaYTipoCambio(moneda, tipoCambio);
            exigirDocumentosRelacionados();
            if (!anticipos.isEmpty())
                throw new DomainException("ANTICIPO_INVALIDO", "khipu todavía no regulariza anticipos en una liquidación de compra (PrepaidPayment, descuentos 04–06 y la retención de renta del anticipo, 61): emítala por el importe total");
            // La hoja de la liquidación lo observa con 4314 (no con la 3303 de la factura y las notas): se dice antes de que Totales lo rechace con su código.
            if (redondeo != null && (redondeo.scale() > 2 || redondeo.abs().compareTo(BigDecimal.ONE) > 0))
                throw new DomainException("REDONDEO_INVALIDO", "4314 - El redondeo del importe neto admite 2 decimales y no puede superar 1.00 en valor absoluto");
            // Sin espacios en los extremos: la regla 2593 rechaza el domicilio que empieza con espacio, y el nombre se imprime igual en el PDF.
            Receptor receptor = new Receptor(vendedor.tipoDoc(), vendedor.numDoc(), vendedor.razonSocial().strip(), vendedor.direccion().strip(), vendedor.pais());
            Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, tipo, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), null,
                    moneda, operacion, receptor, items, FormaPago.contado(), null, List.of(), null, null, null, List.of(), referencias, redondeo, null, datos, null, tasaIgv, List.of(), null, EstadoDocumento.RECIBIDO);
            c.tipoCambio = tipoCambio;
            c.totales.items().forEach(ItemCalculado::exigirValorDeVentaPositivo);
            // 2033: el monto de la retención de cada línea es «diferente de cero»; una línea de menos de 0.34 la redondea a 0.00 y SUNAT la rechazaría con el número consumido.
            for (ItemCalculado l : c.totales.items())
                if (datos.rentaDe(l).signum() <= 0)
                    throw new DomainException("ITEM_INVALIDO", "2033 - La retención de renta de la línea «" + l.item().descripcion() + "» (" + datos.tasaRetencionRenta().toPlainString() + " % de "
                            + l.valorVenta() + ") redondea a 0.00: suba el importe de la línea");
            return c;
        }

        /**
         * Lo que una línea de liquidación de compra lleva según la hoja: afectación 10, 20 o 30 (una línea solo admite 1000, 9996, 9997 o 9998 con la retención 3000 y,
         * opcionalmente, 9999: 3105, 3223) y el código de producto SUNAT, obligatorio (3506: ERROR en la hoja; e-beta, 2026-10-20, aceptó una línea sin él, pero khipu sigue la
         * hoja, que es lo que aplicará producción). khipu no arma todavía lo que la hoja deja abierto y nadie probó: gratuitas (9996,
         * cuya retención la hoja excluye de las sumas, 4345/4346), otros tributos (9999, que el comprador también retendría, 4312), descuentos y cargos (la hoja no tiene
         * AllowanceCharge de línea), ni lo que no existe en ella: ISC, ICBPER, IVAP, exportación, GTIN, placa y los datos sectoriales de la detracción.
         */
        private static void exigirLineaDeLiquidacion(Item i) {
            String linea = "La línea «" + i.descripcion() + "» de una liquidación de compra";
            TipoAfectacionIgv af = i.afectacion();
            if (af.gratuita())
                throw new DomainException("AFECTACION_INVALIDA", linea + " no admite una afectación gratuita (" + af.codigo() + "): khipu todavía no emite compras gratuitas; use 10, 20 o 30");
            if (af != TipoAfectacionIgv.GRAVADO && af != TipoAfectacionIgv.EXONERADO && af != TipoAfectacionIgv.INAFECTO)
                throw new DomainException("AFECTACION_INVALIDA", "3105 - " + linea + " es gravada (10), exonerada (20) o inafecta (30); la afectación " + af.codigo() + " no existe en ella");
            if (!i.tieneCodigoSunat())
                throw new DomainException("ITEM_INVALIDO", "3506 - " + linea + " lleva el código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos)");
            if (i.tieneIsc() || i.icbper())
                throw new DomainException("ITEM_INVALIDO", "3223 - " + linea + " no lleva ISC ni ICBPER: solo IGV, la retención de renta y otros tributos");
            if (i.tieneOtrosTributos())
                throw new DomainException("ITEM_INVALIDO", linea + " todavía no admite otros tributos (9999) en khipu");
            if (i.tieneDescuento() || i.tieneCargos())
                throw new DomainException("ITEM_INVALIDO", linea + " no admite descuentos ni cargos: LiquidacionCompra2_0 no tiene cargos ni descuentos de línea; indique el precio neto");
            if (i.tieneGtin() || i.tienePlaca() || i.tieneHidrobiologico() || i.tieneTransporte())
                throw new DomainException("ITEM_INVALIDO", linea + " no lleva GTIN, placa ni datos de detracción: no forman parte de LiquidacionCompra2_0");
        }

        /**
         * Guías (09/31, 4005) y otros documentos: la hoja admite el 10 y el 99 (4009). El 10 es la liquidación emitida por anticipos, que se referencia junto con su pago
         * (DocumentStatusCode, 3214–3216): va con los anticipos, que todavía no se regularizan. La orden de compra no está en la hoja.
         */
        private void exigirDocumentosRelacionados() {
            if (referencias == null) return;
            if (referencias.ordenCompra() != null)
                throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "Una liquidación de compra no lleva orden de compra (LiquidacionCompra2_0 no la tiene)");
            for (DocumentoRelacionado d : referencias.otros())
                if (!"99".equals(d.tipo()))
                    throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4009 - En una liquidación de compra el documento relacionado es del tipo 99 (otros); recibido " + d.tipo());
        }
    }

    /**
     * Comprobante de retención (20) o de percepción (40), #40: lo emite la empresa designada por SUNAT como agente al proveedor al que paga (retención: tiene RUC) o al cliente al
     * que cobra (percepción). {@code contraparte} es ese proveedor o cliente; {@code datos}, el régimen y los documentos pagados o cobrados, y su tipo decide cuál de los dos se
     * emite. Único punto de entrada: obligatorios aquí, el redondeo en el {@link AgenteBuilder}, y {@link AgenteBuilder#crear} aplica las reglas de Retenciones1_0 / Percepciones1_0.
     * Que la empresa sea agente lo decide el servicio con la designación que guarda ({@code Tenant#exigirAgenteDe}).
     */
    public static AgenteBuilder deAgente(UUID tenantId, String serie, LocalDate fechaEmision, Receptor contraparte, ComprobanteDeAgente datos) {
        return new AgenteBuilder(tenantId, serie, fechaEmision, contraparte, datos);
    }

    public static final class AgenteBuilder {
        private final UUID tenantId; private final String serie; private final LocalDate fechaEmision; private final Receptor contraparte; private final ComprobanteDeAgente datos;
        private BigDecimal redondeo;

        private AgenteBuilder(UUID tenantId, String serie, LocalDate fechaEmision, Receptor contraparte, ComprobanteDeAgente datos) {
            this.tenantId = tenantId; this.serie = serie; this.fechaEmision = fechaEmision; this.contraparte = contraparte; this.datos = datos;
        }
        /** PayableRoundingAmount del importe total pagado o cobrado, |x| ≤ 1 (3303 en Retenciones1_0 y Percepciones1_0). */
        public AgenteBuilder redondeo(BigDecimal r) { this.redondeo = r; return this; }

        /** {@code rucAgente}: el RUC de la empresa que emite, que el proveedor o el cliente no pueden repetir (2620, 2604). */
        public Comprobante crear(String rucAgente, Clock clock) {
            if (datos == null) throw new IllegalArgumentException("Faltan los datos de la retención o la percepción");
            TipoDocumento tipo = datos.tipo();
            boolean ret = datos.esRetencion();
            String nombre = ret ? "comprobante de retención" : "comprobante de percepción";
            if (!tipo.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "1001 - Serie de " + nombre + " inválida (" + (ret ? "R" : "P") + "###): " + serie);
            if (fechaEmision == null || fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(PlazoEnvio.de(tipo, null), fechaEmision, clock);
            if (contraparte == null)
                throw new DomainException("RECEPTOR_INVALIDO", ret ? "2723 - El comprobante de retención requiere el proveedor: su RUC y razón social" : "2679 - El comprobante de percepción requiere el cliente: su documento y nombre");
            if (ret) contraparte.exigirValidoComoProveedor(rucAgente);
            else contraparte.exigirValidoComoClienteDePercepcion(rucAgente);
            datos.validar(fechaEmision);
            ComprobanteDeAgente.validarRedondeo(redondeo, ret);
            Receptor receptor = new Receptor(contraparte.tipoDoc(), contraparte.numDoc(), contraparte.razonSocial().strip(),
                    contraparte.direccion() == null || contraparte.direccion().isBlank() ? null : contraparte.direccion().strip(), contraparte.pais());
            Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, tipo, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), null,
                    Monedas.SOLES, null, receptor, List.of(), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, redondeo, null, null, datos, TasaIgv.GENERAL,
                    List.of(), null, EstadoDocumento.RECIBIDO);
            if (c.importeNetoDeAgente().signum() <= 0)
                throw new DomainException("REDONDEO_INVALIDO", (ret ? "2730 - El importe total pagado" : "2687 - El importe total cobrado") + " (netos + redondeo) debe ser mayor que cero; queda en " + c.importeNetoDeAgente());
            return c;
        }
    }

    /**
     * Nota de crédito (07) o de débito (08) sobre una factura. Comparte con la factura receptor, ítems, descuentos, cargos y
     * totales; añade el documento modificado y el motivo. La serie debe empezar como la del documento que modifica (regla
     * 1001/2117: F… para facturas). Los límites frente a la factura modificada (3286 y afines) los aplica el servicio, que es
     * quien la tiene a mano. Único punto de entrada (#74): obligatorios aquí, opcionales en el {@link NotaBuilder}.
     */
    public static NotaBuilder nota(UUID tenantId, TipoDocumento tipo, String serie, LocalDate fechaEmision, Nota nota, Receptor receptor, List<Item> items) {
        return new NotaBuilder(tenantId, tipo, serie, fechaEmision, nota, receptor, items);
    }

    public static final class NotaBuilder {
        private final UUID tenantId; private final TipoDocumento tipo; private final String serie; private final LocalDate fechaEmision; private final Nota nota;
        private final Receptor receptor; private final List<Item> items;
        private String moneda = "PEN"; private String tipoOperacion = "0101"; private FormaPago formaPago; private Descuento descuentoGlobal; private List<Cargo> cargos = List.of();
        private BigDecimal tasaIgv = TasaIgv.GENERAL; private Exportacion exportacion; private BigDecimal redondeo; private boolean copiaDelDocumento; private BigDecimal tipoCambio;

        private NotaBuilder(UUID tenantId, TipoDocumento tipo, String serie, LocalDate fechaEmision, Nota nota, Receptor receptor, List<Item> items) {
            this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.fechaEmision = fechaEmision; this.nota = nota; this.receptor = receptor; this.items = items;
        }
        public NotaBuilder moneda(String m) { this.moneda = m; return this; }
        public NotaBuilder tipoOperacion(String t) { this.tipoOperacion = t == null ? "0101" : t; return this; }
        /** Solo la NC 13 la usa (cuotas corregidas); nulo = contado. */
        public NotaBuilder formaPago(FormaPago fp) { this.formaPago = fp; return this; }
        public NotaBuilder descuentoGlobal(Descuento d) { this.descuentoGlobal = d; return this; }
        public NotaBuilder cargos(List<Cargo> c) { this.cargos = c == null ? List.of() : c; return this; }
        /** La de la factura que modifica ({@link TasaIgv}); nulo = general. */
        public NotaBuilder tasaIgv(BigDecimal t) { this.tasaIgv = t == null ? TasaIgv.GENERAL : t; return this; }
        /** Los de la factura que modifica; nulo si no es exportación. */
        public NotaBuilder exportacion(Exportacion e) { this.exportacion = e; return this; }
        /** El de la factura o boleta que la nota modifica (#316): una nota no fija el suyo. */
        public NotaBuilder tipoCambio(BigDecimal t) { this.tipoCambio = t; return this; }
        /**
         * El de la factura que modifica, en la nota total: sin él la nota salía por el total sin redondear y, con el redondeo
         * habitual (negativo), por encima del PayableAmount de la factura (3286, sin tolerancia). La NC admite
         * PayableRoundingAmount con |x| ≤ 1 (NotaCredito2_0 fila 406, regla 3303).
         */
        public NotaBuilder redondeo(BigDecimal r) { this.redondeo = r; return this; }
        /**
         * Nota total (#308): los ítems son los del documento que modifica. Se validan como los propios, porque SUNAT cruza la unidad de la
         * nota con el catálogo 03 igual que la del documento (2936); solo cambia el mensaje, que dice cómo emitirla (#348-H1).
         */
        public NotaBuilder copiaDelDocumento() { this.copiaDelDocumento = true; return this; }

        public Comprobante crear(Clock clock) {
            if (tipo != TipoDocumento.NOTA_CREDITO && tipo != TipoDocumento.NOTA_DEBITO)
                throw new DomainException("NOTA_INVALIDA", "El tipo de nota debe ser 07 (crédito) u 08 (débito)");
            // 2524 es la ausencia de cac:BillingReference (el motivo ausente es 2128, ya en Nota); la cita anterior mezclaba ambas.
            if (nota == null) throw new DomainException("NOTA_INVALIDA", "2524 - La nota debe indicar el documento que modifica");
            // La 1001 solo fija el formato del ID de la nota; que la letra coincida con la factura sale de 2116/2119 (la nota se busca
            // en el listado de facturas F/E). Más estricto que SUNAT a propósito: una serie que no coincide no llega a numerarse.
            if (!tipo.serieValida(serie) || serie.charAt(0) != nota.serieAfectada().charAt(0))
                throw new DomainException("SERIE_INVALIDA", "1001/2116 - La serie de una nota sobre " + nota.documentoAfectado() + " debe ser " + nota.serieAfectada().charAt(0) + "### : " + serie);
            if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(PlazoEnvio.de(tipo, nota), fechaEmision, clock);
            boolean nc13 = nota.corrigeCuotas(tipo);
            if ((items == null || items.isEmpty()) && !nc13) throw new DomainException("SIN_ITEMS", "La nota debe tener al menos un ítem");
            if (items != null) exigirHastaElTopeDeLineas("2137", "nota", items);
            if (items != null) items.forEach(this::exigirLineaValida);
            if (nota.deBoleta()) {
                // El comprador de la boleta (RCP art. 10 num. 1.3), que puede no tener documento ni RUC.
                if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "La nota sobre una boleta lleva el comprador de la boleta");
                receptor.exigirValidoParaBoleta();
            } else {
                if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La nota sobre una factura requiere un receptor" + (Exportacion.es(tipoOperacion) ? "" : " con RUC"));
                receptor.exigirValidoParaFactura(tipoOperacion);
            }
            exigirMonedaYTipoCambio(moneda, tipoCambio);
            Exportacion.validar(exportacion, tipoOperacion);
            nota.validarMotivoPara(tipo);
            if (!nc13) {
                exigirAfectacionSegunOperacion(tipoOperacion, items);
                nota.validarAfectacionesPara(tipo, items);
                exigirSinDescuentosQueNoAfectenLaBase();
            }
            if (nc13 && (formaPago == null || !formaPago.esCredito()))
                throw new DomainException("NOTA_INVALIDA", "3257 - Una nota de crédito con motivo 13 debe indicar la forma de pago al crédito con las cuotas corregidas");
            // La NC 13 no mueve importes: una sola línea de valor 0 (regla 3315). La forma de pago de una nota solo tiene sentido
            // en la NC 13 y se valida contra la factura modificada (3320/3321), no contra la nota.
            Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, tipo, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), null,
                    moneda, tipoOperacion, receptor, nc13 ? List.of(nota.lineaSinImporte()) : items, formaPago == null ? FormaPago.contado() : formaPago,
                    nc13 ? null : descuentoGlobal, nc13 ? List.of() : cargos, null, null, null, List.of(), null, nc13 ? null : redondeo, nota, null, null, tasaIgv, List.of(), exportacion, EstadoDocumento.RECIBIDO);
            c.tipoCambio = tipoCambio;
            c.totales.items().forEach(ItemCalculado::exigirBasePvpValida);
            if (!nc13) c.totales.items().forEach(ItemCalculado::exigirValorDeVentaPositivo);
            return c;
        }

        /**
         * #388: el importe total de una nota es bases + IGV + ISC + otros cargos + redondeo (3280, hojas NotaCredito2_0 y NotaDebito2_0): no resta ningún descuento, y las notas
         * no tienen AllowanceTotalAmount. Un descuento que no afecta la base (01 de línea, 03 global) bajaría lo que se paga sin tocar el IGV, así que SUNAT rechazaría la nota
         * con 3280 con el número ya consumido (e-beta, 2026-10-18). Los que afectan la base (00 y 02) y los cargos sí caben: pasan al valor de las líneas ({@link Totales#paraNota}).
         */
        private void exigirSinDescuentosQueNoAfectenLaBase() {
            if (descuentoGlobal != null && !descuentoGlobal.afectaBaseIgv())
                throw new DomainException("DESCUENTO_INVALIDO", "3280 - Una nota no admite un descuento global que no afecta la base del IGV (código 03): el importe total de la nota no resta descuentos"
                        + (copiaDelDocumento ? ", y " + nota.documentoAfectado() + " lo lleva. " : ". ") + "Indique los ítems de la nota por su importe neto, sin ese descuento");
            if (items != null && items.stream().anyMatch(i -> i.tieneDescuento() && !i.descuento().afectaBaseIgv()))
                throw new DomainException("DESCUENTO_INVALIDO", "3280 - Una nota no admite un descuento de línea que no afecta la base del IGV (código 01): el importe total de la nota no resta descuentos"
                        + (copiaDelDocumento ? ", y " + nota.documentoAfectado() + " lo lleva. " : ". ") + "Indique los ítems de la nota por su importe neto, sin ese descuento");
        }

        /**
         * Una boleta informada en un resumen diario (que no lleva líneas) pudo quedar aceptada con una unidad que no existe; la nota que la
         * copia sale sola por sendBill y SUNAT la rechazaría (2936) con el número ya consumido. Con ítems propios la nota pasa.
         */
        private void exigirLineaValida(Item item) {
            if (copiaDelDocumento && item.unidad() != null && !item.unidad().isBlank() && !item.unidadEnCatalogo())
                throw new DomainException("ITEM_INVALIDO", "2936 - La " + (nota.deBoleta() ? "boleta " : "factura ") + nota.documentoAfectado() + " lleva la unidad " + item.unidad()
                        + ", que no existe en el catálogo 03, y SUNAT rechazaría la nota que la copia: indique los items de la nota con una unidad del catálogo (GET /v1/catalogos/03)");
            if (copiaDelDocumento) item.exigirValidoComoCopia();
            else item.exigirValidoParaFactura();
        }
    }

    public boolean esNota() { return nota != null; }

    public static final int MAX_OBSERVACIONES = 1000;

    /** Observaciones del PDF; en blanco las borra. Se admiten saltos de línea, no otros caracteres de control. */
    public void anotar(String observaciones) {
        if (observaciones == null || observaciones.isBlank()) { this.observaciones = null; return; }
        String s = observaciones.strip();
        if (s.length() > MAX_OBSERVACIONES || s.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r'))
            throw new DomainException("OBSERVACIONES_INVALIDAS", "Las observaciones admiten hasta " + MAX_OBSERVACIONES + " caracteres, solo con saltos de línea como caracteres especiales");
        this.observaciones = s;
    }

    /** El correo del cliente (#325); en blanco lo quita. Lanza {@code RECEPTOR_INVALIDO} si no es un correo válido. */
    public void dirigirA(String email) { this.correoCliente = CorreoDelCliente.normalizar(email); }

    /** Regla 2023 (factura y boleta) o 2137 (notas): el número de orden de la línea es de hasta 3 dígitos, así que no cabe más de {@link #MAX_LINEAS}. */
    private static void exigirHastaElTopeDeLineas(String reglaSunat, String documento, List<Item> items) {
        if (items.size() > MAX_LINEAS)
            throw new DomainException("ITEM_INVALIDO", reglaSunat + " - Una " + documento + " admite hasta " + MAX_LINEAS + " líneas (el número de orden del ítem es de hasta 3 dígitos); recibidas " + items.size()
                    + ": divida la operación en más de un comprobante");
    }

    /** Una fecha de emisión cuyo plazo de envío ya venció daría un comprobante que SUNAT rechaza (2108, o 1079 en lo de boleta) con el número consumido. */
    private static void exigirDentroDelPlazoDeEnvio(PlazoEnvio plazo, LocalDate fechaEmision, Clock clock) {
        if (plazo.vencido(fechaEmision, LocalDate.now(clock)))
            throw new DomainException("FECHA_INVALIDA", plazo.motivoDeRechazo(fechaEmision) + " (fecha de emisión " + fechaEmision + ")");
    }

    /** Su plazo de envío: el de la boleta si es una boleta o una nota que la modifica, el de la factura si no (#293). */
    private PlazoEnvio plazoEnvio() { return PlazoEnvio.de(tipo, nota); }

    /** Una boleta o una nota que modifica una boleta: va sola hasta el 5.º día, en el resumen diario hasta el 7.º y se anula solo en él (#293). */
    public boolean esDeBoleta() { return tipo == TipoDocumento.BOLETA || (nota != null && nota.deBoleta()); }

    /**
     * Si SUNAT contesta {@code getStatusCdr} para este comprobante (#357). Según el manual del programador (p. 22 y 24) el servicio cubre solo la factura, la nota de crédito y
     * la nota de débito (01, 07, 08) y, según su Anexo 2, solo de serie F: la boleta y las notas B quedan fuera. Su CDR es el del envío individual o el del resumen diario
     * (que se consulta con el ticket), el mismo criterio de {@link #esDeBoleta()}. Qué responde SUNAT si igual se le pregunta no está documentado ni se puede comprobar en e-beta (no publica billConsultService: responde 404 y solo existe
     * en producción): no se le pregunta.
     */
    public boolean consultableEnGetStatusCdr() { return !esDeBoleta() && !esLiquidacionCompra() && !esDeAgente(); }

    /** Una retención (20) o una percepción (40), #40: tampoco la cubre {@code getStatusCdr} (manual, p. 22: 01, 07 y 08) y se revierte en un resumen de reversiones (RR). */
    public boolean esDeAgente() { return agente != null; }

    /**
     * Importe total pagado al proveedor (sac:SUNATTotalPaid) o cobrado al cliente (sac:SUNATTotalCashed): la suma de los netos de cada documento más el redondeo (2629/2668), en soles.
     * Cero si no es una retención ni una percepción.
     */
    public BigDecimal importeNetoDeAgente() { return agente == null ? BigDecimal.ZERO.setScale(2) : agente.netoTotal().add(totales.redondeo()); }

    /** Una liquidación de compra (#327): el emisor es el comprador y el {@link #receptor()} es el vendedor. Tampoco la cubre {@code getStatusCdr} (manual, p. 22: 01, 07 y 08). */
    public boolean esLiquidacionCompra() { return tipo == TipoDocumento.LIQUIDACION_COMPRA; }

    /** Importe retenido al vendedor por el Impuesto a la Renta (tributo 3000), o cero si no es una liquidación de compra. */
    public BigDecimal retencionRenta() { return liquidacion == null ? BigDecimal.ZERO.setScale(2) : liquidacion.retencionRenta(totales); }

    /** Regla 3206: el tipo de operación debe existir en el catálogo 51 y aplicar al tipo de comprobante (columna "Tipo de Comprobante asociado": "Factura", "Boleta"…). */
    private static void validarTipoOperacion(String operacion, TipoDocumento tipo) {
        CatalogoSunat.Entrada e = CatalogoSunat.porId("51").flatMap(c -> c.entrada(operacion))
                .orElseThrow(() -> new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " no existe en el catálogo 51"));
        String aplicaA = e.extra().getOrDefault("Tipo de Comprobante asociado", "");
        String nombre = switch (tipo) { case BOLETA -> "boleta"; case LIQUIDACION_COMPRA -> "liquidación de compra"; default -> "factura"; };
        if (!aplicaA.toLowerCase().contains(nombre))
            throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " (" + e.descripcion() + ") no aplica a "
                    + (tipo == TipoDocumento.LIQUIDACION_COMPRA ? "liquidaciones de compra" : nombre + "s") + ": " + aplicaA);
    }

    /**
     * La moneda debe ser una del catálogo 02 que khipu emite (3088) y el tipo de cambio, si se informa, solo va fuera de soles y es positivo de hasta 3 enteros y 3 decimales
     * (el formato del campo del registro de ventas). Lo comparten la factura, la boleta y las notas.
     */
    private static void exigirMonedaYTipoCambio(String moneda, BigDecimal tipoCambio) {
        if (!Monedas.admitida(moneda))
            throw new DomainException("MONEDA_INVALIDA", "3088 - Moneda no soportada: " + moneda + " (catálogo 02; admitidas: " + String.join(", ", Monedas.codigos()) + ")");
        if (tipoCambio == null) return;
        if (Monedas.SOLES.equals(moneda))
            throw new DomainException("TIPO_CAMBIO_INVALIDO", "Un comprobante en soles no lleva tipo de cambio: tipo_cambio solo va fuera de PEN");
        if (tipoCambio.signum() <= 0 || tipoCambio.scale() > 3 || tipoCambio.precision() - tipoCambio.scale() > 3)
            throw new DomainException("TIPO_CAMBIO_INVALIDO", "El tipo de cambio es positivo, de hasta 3 enteros y 3 decimales (soles por " + moneda + "); recibido " + tipoCambio.toPlainString());
    }

    /** Importe total en soles al tipo de cambio del comprobante; vacío en soles o si el emisor no informó el tipo de cambio. */
    public java.util.Optional<BigDecimal> totalEnSoles() {
        if (tipoCambio == null || Monedas.SOLES.equals(moneda)) return java.util.Optional.empty();
        return java.util.Optional.of(totales.total().multiply(tipoCambio).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Una boleta sin documento pasa de S/ 700: el total en soles, o cualquier total fuera de soles sin tipo de cambio con el que medirlo. */
    private boolean superaElTopeSinDocumento() {
        if (Monedas.SOLES.equals(moneda)) return totales.total().compareTo(TOPE_BOLETA_SIN_DOCUMENTO) > 0;
        return totalEnSoles().map(s -> s.compareTo(TOPE_BOLETA_SIN_DOCUMENTO) > 0).orElse(true);
    }

    /** Regla 2642: una operación de exportación (0200–0208) lleva todas sus líneas con afectación 40; fuera de ella, ninguna (3107). */
    private static void exigirAfectacionSegunOperacion(String operacion, List<Item> items) {
        boolean exportacion = Exportacion.es(operacion);
        if (exportacion && items.stream().anyMatch(i -> !i.afectacion().exportacion()))
            throw new DomainException("AFECTACION_INVALIDA", "2642 - En una exportación (" + operacion + ") todos los ítems llevan tipo_afectacion_igv 40");
        if (!exportacion && items.stream().anyMatch(i -> i.afectacion().exportacion()))
            throw new DomainException("AFECTACION_INVALIDA", "3107 - La afectación 40 (exportación) exige un tipo de operación 0200–0208; recibido " + operacion);
    }

    /**
     * Solo para persistencia: reconstruye sin validar reglas de creación. Único punto de entrada (#74): identidad y datos
     * de cabecera aquí, el resto en el {@link Persistido} builder, y {@link Persistido#rehidratar()} arma el objeto.
     */
    public static Persistido persistido(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision, EstadoDocumento estado, Receptor receptor, List<Item> items) {
        return new Persistido(id, tenantId, tipo, serie, numero, fechaEmision, estado, receptor, items);
    }

    public static final class Persistido {
        private final UUID id; private final UUID tenantId; private final TipoDocumento tipo; private final String serie; private final Long numero; private final LocalDate fechaEmision;
        private final EstadoDocumento estado; private final Receptor receptor; private final List<Item> items;
        private LocalTime horaEmision; private LocalDate fechaVencimiento; private String moneda = "PEN"; private String tipoOperacion = "0101";
        private FormaPago formaPago = FormaPago.contado(); private Descuento descuentoGlobal; private List<Cargo> cargos = List.of(); private Detraccion detraccion; private RetencionIgv retencion; private Percepcion percepcion;
        private List<Anticipo> anticipos = List.of(); private Referencias referencias; private BigDecimal redondeo; private Nota nota; private LiquidacionCompra liquidacion; private ComprobanteDeAgente agente; private BigDecimal tasaIgv = TasaIgv.GENERAL; private List<String> leyendas = List.of();
        private Exportacion exportacion; private BigDecimal tipoCambio;
        private String hash, nombreArchivo, xmlKey, cdrKey, ultimoError, observaciones, correoCliente; private Cdr cdr; private int intentos;

        private Persistido(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision, EstadoDocumento estado, Receptor receptor, List<Item> items) {
            this.id = id; this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.numero = numero; this.fechaEmision = fechaEmision; this.estado = estado; this.receptor = receptor; this.items = items;
        }
        public Persistido horaEmision(LocalTime h) { this.horaEmision = h; return this; }
        public Persistido fechaVencimiento(LocalDate f) { this.fechaVencimiento = f; return this; }
        public Persistido moneda(String m) { this.moneda = m; return this; }
        public Persistido tipoOperacion(String t) { this.tipoOperacion = t; return this; }
        public Persistido formaPago(FormaPago fp) { this.formaPago = fp == null ? FormaPago.contado() : fp; return this; }
        public Persistido descuentoGlobal(Descuento d) { this.descuentoGlobal = d; return this; }
        public Persistido cargos(List<Cargo> c) { this.cargos = c == null ? List.of() : c; return this; }
        public Persistido detraccion(Detraccion d) { this.detraccion = d; return this; }
        public Persistido retencion(RetencionIgv r) { this.retencion = r; return this; }
        public Persistido percepcion(Percepcion p) { this.percepcion = p; return this; }
        public Persistido anticipos(List<Anticipo> a) { this.anticipos = a == null ? List.of() : a; return this; }
        public Persistido referencias(Referencias r) { this.referencias = r; return this; }
        public Persistido redondeo(BigDecimal r) { this.redondeo = r; return this; }
        public Persistido nota(Nota n) { this.nota = n; return this; }
        public Persistido liquidacion(LiquidacionCompra l) { this.liquidacion = l; return this; }
        public Persistido agente(ComprobanteDeAgente a) { this.agente = a; return this; }
        public Persistido tasaIgv(BigDecimal t) { this.tasaIgv = t; return this; }
        public Persistido leyendas(List<String> l) { this.leyendas = l == null ? List.of() : l; return this; }
        public Persistido exportacion(Exportacion e) { this.exportacion = e; return this; }
        public Persistido tipoCambio(BigDecimal t) { this.tipoCambio = t; return this; }
        public Persistido firma(String hash, String nombreArchivo, String xmlKey) { this.hash = hash; this.nombreArchivo = nombreArchivo; this.xmlKey = xmlKey; return this; }
        public Persistido cdr(Cdr cdr, String cdrKey) { this.cdr = cdr; this.cdrKey = cdrKey; return this; }
        public Persistido envio(int intentos, String ultimoError) { this.intentos = intentos; this.ultimoError = ultimoError; return this; }
        public Persistido observaciones(String o) { this.observaciones = o; return this; }
        /** Tal como se guardó: lo ya emitido no se vuelve a validar. */
        public Persistido correoCliente(String e) { this.correoCliente = e; return this; }

        public Comprobante rehidratar() {
            Comprobante c = new Comprobante(id, tenantId, tipo, serie, numero, fechaEmision, horaEmision, fechaVencimiento, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencion, percepcion, anticipos, referencias, redondeo, nota, liquidacion, agente, tasaIgv, leyendas, exportacion, estado);
            c.hash = hash; c.nombreArchivo = nombreArchivo; c.xmlKey = xmlKey; c.cdrKey = cdrKey; c.cdr = cdr;
            c.intentos = intentos; c.ultimoError = ultimoError; c.observaciones = observaciones; c.tipoCambio = tipoCambio; c.correoCliente = correoCliente;
            return c;
        }
    }

    public void asignarNumero(long numero, String rucEmisor) {
        if (this.numero != null) throw new DomainException("NUMERO_YA_ASIGNADO", "El comprobante ya tiene número");
        this.numero = numero;
        this.nombreArchivo = NombreArchivo.de(rucEmisor, tipo, serie, numero);
    }

    public void firmar(String hash, String xmlKey) {
        transitar(EstadoDocumento.FIRMADO, "Firmado; resumen " + hash);
        this.hash = hash; this.xmlKey = xmlKey;
    }

    public void marcarEnviado() { transitar(EstadoDocumento.ENVIADO, "Enviado a SUNAT (intento " + (intentos + 1) + ")"); }

    /**
     * Una boleta que pasó el envío individual va en el resumen diario {@code identificador} (274-H1): queda ENVIADA hasta que SUNAT responda el ticket, y
     * el CDR del resumen la acepta o la rechaza ({@link #aplicarCdr}).
     */
    public void informarEnResumen(String identificador, boolean fueraDePlazo) {
        if (!esDeBoleta()) throw new DomainException("RESUMEN_INVALIDO", "Solo una boleta o una nota que la modifica se informa en un resumen diario");
        transitar(EstadoDocumento.ENVIADO, "Informada a SUNAT en el resumen diario " + identificador + (fueraDePlazo
                ? " fuera de plazo: pasó el 7.º día (vencía el " + fechaLimiteEnvio() + "), SUNAT puede observarla con la 4443"
                : ": pasó el envío individual (1079)"));
    }

    /**
     * Una boleta, o una nota que la modifica, que ya no se puede enviar sola, pero se puede informar en un resumen diario ({@link PlazoEnvio#soloPorResumen}): en
     * plazo hasta el 7.º día, fuera de plazo después ({@link #informaFueraDePlazo}).
     */
    public boolean soloPorResumen(LocalDate hoy) { return plazoEnvio().soloPorResumen(fechaEmision, hoy); }

    /** Si hoy se informaría en el resumen diario pasado el 7.º día: SUNAT no la rechaza, puede observarla con la 4443 (#331; e-beta no la observó). */
    public boolean informaFueraDePlazo(LocalDate hoy) { return plazoEnvio().informaFueraDePlazo(fechaEmision, hoy); }

    /**
     * Una boleta, o una nota que la modifica, que sigue sin informarse (FIRMADA o con el envío fallando) y ya pide atención (#356): desde el 5.º día ({@link PlazoEnvio#porInformar}).
     * Es el «atención requerida» de lo que no tiene un estado propio para eso: la boleta nunca pasa a FUERA_DE_PLAZO (#331).
     */
    public boolean porInformar(LocalDate hoy) { return estado.esEnviable() && plazoEnvio().porInformar(fechaEmision, hoy); }

    /** Lo mismo que {@link #porInformar}, pero ya en el último día del resumen diario o pasado (#356): el segundo aviso, el urgente. */
    public boolean porVencerElPlazo(LocalDate hoy) { return estado.esEnviable() && plazoEnvio().porVencerElPlazo(fechaEmision, hoy); }

    /** Si el outbox debe seguir reintentando su envío aunque agote los intentos (#356): lo de boleta, mientras siga en plazo del resumen diario. */
    public boolean noSeAbandonaTodavia(LocalDate hoy) { return plazoEnvio().noSeAbandonaTodavia(fechaEmision, hoy); }

    public void aplicarCdr(Cdr cdr, String cdrKey) {
        EstadoDocumento destino = cdr.esRechazo() ? EstadoDocumento.RECHAZADO
                : cdr.esObservado() ? EstadoDocumento.ACEPTADO_CON_OBS : EstadoDocumento.ACEPTADO;
        transitar(destino, cdr.codigo() + " - " + cdr.descripcion() + (cdr.tieneObservaciones() ? " (" + String.join("; ", cdr.observaciones()) + ")" : ""));
        this.cdr = cdr; this.cdrKey = cdrKey; this.ultimoError = null;
    }

    /**
     * #362 B10: el identificador del resumen diario (RC-aaaammdd-n) cuyo CDR es el de este comprobante, si lo es. Una boleta (o una nota de boleta) informada en un resumen no
     * recibe un CDR propio: SUNAT acepta el resumen y esa constancia es la que la boleta guarda y entrega. Se sabe por la clave con que se guardó
     * ({@link ComunicacionBaja#identificadorDelResumenDeCdr}), sin la baja.
     */
    public java.util.Optional<String> resumenDelCdr() { return ComunicacionBaja.identificadorDelResumenDeCdr(cdrKey); }

    /** CDR recuperado de SUNAT para un comprobante que ya tenía estado final pero perdió su constancia en el storage. */
    public void restaurarCdr(Cdr cdr, String cdrKey) {
        if (!estado.esFinalAceptado() && estado != EstadoDocumento.RECHAZADO && estado != EstadoDocumento.ANULADO)
            throw new DomainException("TRANSICION_INVALIDA", "Solo se restaura el CDR de un comprobante ya resuelto por SUNAT; este está " + estado);
        this.cdr = cdr; this.cdrKey = cdrKey;
    }

    public void marcarErrorEnvio(String motivo) {
        transitar(EstadoDocumento.ERROR_ENVIO, motivo);
        this.intentos++; this.ultimoError = motivo;
    }

    /**
     * Un administrador deja de intentar un envío que falla (#196). Solo desde {@link EstadoDocumento#ERROR_ENVIO}: lo que SUNAT ya resolvió, lo que está en camino y lo
     * que ya es terminal no se descarta. No borra los intentos ni el último fallo: es lo que explica por qué se descartó.
     */
    public void descartar(String motivo) {
        if (estado != EstadoDocumento.ERROR_ENVIO)
            throw new DomainException("ESTADO_NO_DESCARTABLE", "Solo se descarta un comprobante en error de envío; este está " + estado);
        transitar(EstadoDocumento.DESCARTADO, "Descartado por un administrador: " + motivo);
    }

    /**
     * SUNAT aceptó la baja que lo incluye: el número queda consumido y el comprobante deja de ser válido. Lo de boleta se anula en un resumen diario, no en una
     * comunicación de baja ({@link ComunicacionBaja}): el historial nombra el canal que de verdad se usó (#286).
     */
    public void anular() {
        transitar(EstadoDocumento.ANULADO, esDeBoleta() ? "Anulación aceptada por SUNAT en el resumen diario"
                : esDeAgente() ? "Reversión aceptada por SUNAT en el resumen de reversiones" : "Comunicación de baja aceptada por SUNAT");
    }

    /** Último día en que SUNAT acepta recibirlo ({@link PlazoEnvio}). */
    public LocalDate fechaLimiteEnvio() { return plazoEnvio().fechaLimite(fechaEmision); }

    /**
     * Venció el plazo y ya no le queda canal para llegar a SUNAT ({@link PlazoEnvio#sinCanal}): lo que pasa a FUERA_DE_PLAZO. Una boleta, o una nota B que va en el
     * resumen diario, nunca (#331): pasado el 7.º día se informa igual, fuera de plazo.
     */
    public boolean fueraDePlazo(LocalDate hoy) { return plazoEnvio().sinCanal(fechaEmision, hoy); }

    /** Venció el plazo sin llegar a SUNAT: terminal para el envío individual, con el motivo de SUNAT ({@link PlazoEnvio#motivoDeRechazo}). */
    public void marcarFueraDePlazo(LocalDate hoy) {
        if (informaFueraDePlazo(hoy))
            throw new DomainException("TRANSICION_INVALIDA", "El plazo de envío venció el " + fechaLimiteEnvio() + ", pero todavía se informa en un resumen diario, fuera de plazo (4443)");
        if (!fueraDePlazo(hoy)) throw new DomainException("TRANSICION_INVALIDA", "El plazo de envío vence el " + fechaLimiteEnvio() + ": todavía se puede enviar");
        this.ultimoError = plazoEnvio().motivoDeRechazo(fechaEmision);
        transitar(EstadoDocumento.FUERA_DE_PLAZO, this.ultimoError);
    }

    public void rechazarPorFault(String codigo, String descripcion) {
        if (estado.esEnviable()) marcarEnviado();
        transitar(EstadoDocumento.RECHAZADO, codigo + " - " + descripcion);
        this.cdr = new Cdr(codigo, descripcion, List.of());
    }

    private void transitar(EstadoDocumento destino) { transitar(destino, null); }

    private void transitar(EstadoDocumento destino, String detalle) {
        if (!estado.puedeTransitarA(destino))
            throw new DomainException("TRANSICION_INVALIDA", "No se puede pasar de " + estado + " a " + destino);
        eventosPendientes.add(EventoDocumento.pendiente(estado, destino, detalle));
        estado = destino;
    }

    /** Cambios de estado aún no persistidos, en orden; el repositorio los guarda y llama a {@link #eventosGuardados()}. */
    public List<EventoDocumento> eventosPendientes() { return List.copyOf(eventosPendientes); }

    public void eventosGuardados() { eventosPendientes.clear(); }

}
