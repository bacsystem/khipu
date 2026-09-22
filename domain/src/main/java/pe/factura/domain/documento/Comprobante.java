package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
    /** Tasa del IGV aplicada a las líneas gravadas, en porcentaje ({@link TasaIgv}); una nota hereda la de la factura que modifica. */
    private final BigDecimal tasaIgv;
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

    private Comprobante(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision, LocalTime horaEmision, LocalDate fechaVencimiento,
                        String moneda, String tipoOperacion, Receptor receptor, List<Item> items, FormaPago formaPago,
                        Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencion, Percepcion percepcion, List<Anticipo> anticipos,
                        Referencias referencias, BigDecimal redondeo, Nota nota, BigDecimal tasaIgv, EstadoDocumento estado) {
        this.id = id; this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.numero = numero;
        this.fechaEmision = fechaEmision; this.horaEmision = horaEmision; this.fechaVencimiento = fechaVencimiento; this.moneda = moneda; this.tipoOperacion = tipoOperacion;
        this.receptor = receptor; this.items = List.copyOf(items); this.formaPago = formaPago; this.descuentoGlobal = descuentoGlobal;
        this.cargos = cargos == null ? List.of() : List.copyOf(cargos);
        this.anticipos = anticipos == null ? List.of() : List.copyOf(anticipos);
        this.referencias = referencias == null ? Referencias.ninguna() : referencias;
        this.nota = nota;
        this.tasaIgv = TasaIgv.normalizar(tasaIgv);
        this.totales = Totales.calcular(this.items, descuentoGlobal, this.cargos, this.anticipos, Icbper.tasaVigente(fechaEmision), redondeo, this.tasaIgv);
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
        return new FacturaBuilder(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items);
    }

    public static final class FacturaBuilder {
        private final UUID tenantId; private final String serie; private final LocalDate fechaEmision; private final String moneda; private final String tipoOperacion;
        private final Receptor receptor; private final List<Item> items;
        private LocalDate fechaVencimiento; private FormaPago formaPago = FormaPago.contado(); private Descuento descuentoGlobal; private List<Cargo> cargos = List.of();
        private Detraccion detraccion; private RetencionIgv retencion; private Percepcion percepcion; private List<Anticipo> anticipos = List.of();
        private Referencias referencias; private BigDecimal redondeo; private BigDecimal tasaIgv = TasaIgv.GENERAL;

        private FacturaBuilder(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor, List<Item> items) {
            this.tenantId = tenantId; this.serie = serie; this.fechaEmision = fechaEmision; this.moneda = moneda; this.tipoOperacion = tipoOperacion; this.receptor = receptor; this.items = items;
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

        public Comprobante crear(Clock clock) {
            FormaPago formaPago = this.formaPago;
            List<Anticipo> anticipos = this.anticipos;
            Detraccion detraccion = this.detraccion;
            Percepcion percepcion = this.percepcion;
            if (!TipoDocumento.FACTURA.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "Serie de factura inválida: " + serie);
            if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(TipoDocumento.FACTURA, fechaEmision, clock);
            if (fechaVencimiento != null && fechaVencimiento.isBefore(fechaEmision))
                throw new DomainException("FECHA_INVALIDA", "La fecha de vencimiento no puede ser anterior a la de emisión");
            if (items == null || items.isEmpty()) throw new DomainException("SIN_ITEMS", "La factura debe tener al menos un ítem");
            if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La factura requiere un receptor con RUC");
            receptor.exigirValidoParaFactura();
            if (moneda == null || !moneda.matches("PEN|USD|EUR")) throw new DomainException("MONEDA_INVALIDA", "Moneda no soportada: " + moneda);
            String operacion = tipoOperacion == null ? "0101" : tipoOperacion;
            validarTipoOperacion(operacion);
            if (detraccion == null && Detraccion.TIPOS_OPERACION.contains(operacion))
                throw new DomainException("DETRACCION_INVALIDA", "3127 - El tipo de operación " + operacion + " exige los datos de la detracción (bien/servicio, porcentaje, monto y cuenta)");
            if (detraccion != null) detraccion.validarContra(operacion);
            if (percepcion == null && Percepcion.TIPO_OPERACION.equals(operacion) && !formaPago.esCredito())
                throw new DomainException("PERCEPCION_INVALIDA", "3093 - Una operación sujeta a percepción (2001) al contado debe informar la percepción");
            if (anticipos != null && anticipos.stream().map(Anticipo::comprobante).distinct().count() < anticipos.size())
                throw new DomainException("ANTICIPO_INVALIDO", "3215 - La misma factura de anticipo aparece más de una vez");
            Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, TipoDocumento.FACTURA, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), fechaVencimiento,
                    moneda, operacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencion, percepcion, anticipos, referencias, redondeo, null, tasaIgv, EstadoDocumento.RECIBIDO);
            formaPago.validarContra(c.totales.total(), fechaEmision);
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
        private BigDecimal tasaIgv = TasaIgv.GENERAL;

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

        public Comprobante crear(Clock clock) {
            if (tipo != TipoDocumento.NOTA_CREDITO && tipo != TipoDocumento.NOTA_DEBITO)
                throw new DomainException("NOTA_INVALIDA", "El tipo de nota debe ser 07 (crédito) u 08 (débito)");
            if (nota == null) throw new DomainException("NOTA_INVALIDA", "2524 - La nota debe indicar el documento que modifica y el motivo");
            if (!tipo.serieValida(serie) || serie.charAt(0) != nota.serieAfectada().charAt(0))
                throw new DomainException("SERIE_INVALIDA", "1001 - La serie de una nota sobre " + nota.documentoAfectado() + " debe ser " + nota.serieAfectada().charAt(0) + "### : " + serie);
            if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
            exigirDentroDelPlazoDeEnvio(tipo, fechaEmision, clock);
            boolean nc13 = nota.corrigeCuotas(tipo);
            if ((items == null || items.isEmpty()) && !nc13) throw new DomainException("SIN_ITEMS", "La nota debe tener al menos un ítem");
            if (receptor == null) throw new DomainException("RECEPTOR_INVALIDO", "2014 - La nota sobre una factura requiere un receptor con RUC");
            receptor.exigirValidoParaFactura();
            if (moneda == null || !moneda.matches("PEN|USD|EUR")) throw new DomainException("MONEDA_INVALIDA", "Moneda no soportada: " + moneda);
            nota.validarMotivoPara(tipo);
            if (nc13 && (formaPago == null || !formaPago.esCredito()))
                throw new DomainException("NOTA_INVALIDA", "3257 - Una nota de crédito con motivo 13 debe indicar la forma de pago al crédito con las cuotas corregidas");
            // La NC 13 no mueve importes: una sola línea de valor 0 (regla 3315). La forma de pago de una nota solo tiene sentido
            // en la NC 13 y se valida contra la factura modificada (3320/3321), no contra la nota.
            return new Comprobante(UUID.randomUUID(), tenantId, tipo, serie, null, fechaEmision, LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS), null,
                    moneda, tipoOperacion, receptor, nc13 ? List.of(nota.lineaSinImporte()) : items, formaPago == null ? FormaPago.contado() : formaPago,
                    nc13 ? null : descuentoGlobal, nc13 ? List.of() : cargos, null, null, null, List.of(), null, null, nota, tasaIgv, EstadoDocumento.RECIBIDO);
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

    /** Una fecha de emisión cuyo plazo de envío ya venció daría un comprobante que SUNAT rechaza (2108) con el número consumido. */
    private static void exigirDentroDelPlazoDeEnvio(TipoDocumento tipo, LocalDate fechaEmision, Clock clock) {
        if (PlazoEnvio.vencido(tipo, fechaEmision, LocalDate.now(clock)))
            throw new DomainException("FECHA_INVALIDA", "2108 - Con fecha de emisión " + fechaEmision + " el plazo de envío a SUNAT venció el "
                    + PlazoEnvio.fechaLimite(tipo, fechaEmision) + " (" + PlazoEnvio.dias(tipo) + " días calendario)");
    }

    /** Regla 3206: el tipo de operación debe existir en el catálogo 51 y aplicar a facturas (columna "Tipo de Comprobante asociado"). */
    private static void validarTipoOperacion(String operacion) {
        CatalogoSunat.Entrada e = CatalogoSunat.porId("51").flatMap(c -> c.entrada(operacion))
                .orElseThrow(() -> new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " no existe en el catálogo 51"));
        String aplicaA = e.extra().getOrDefault("Tipo de Comprobante asociado", "");
        if (!aplicaA.toLowerCase().contains("factura"))
            throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " (" + e.descripcion() + ") no aplica a facturas: " + aplicaA);
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
        private List<Anticipo> anticipos = List.of(); private Referencias referencias; private BigDecimal redondeo; private Nota nota; private BigDecimal tasaIgv = TasaIgv.GENERAL;
        private String hash, nombreArchivo, xmlKey, cdrKey, ultimoError, observaciones; private Cdr cdr; private int intentos;

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
        public Persistido tasaIgv(BigDecimal t) { this.tasaIgv = t; return this; }
        public Persistido firma(String hash, String nombreArchivo, String xmlKey) { this.hash = hash; this.nombreArchivo = nombreArchivo; this.xmlKey = xmlKey; return this; }
        public Persistido cdr(Cdr cdr, String cdrKey) { this.cdr = cdr; this.cdrKey = cdrKey; return this; }
        public Persistido envio(int intentos, String ultimoError) { this.intentos = intentos; this.ultimoError = ultimoError; return this; }
        public Persistido observaciones(String o) { this.observaciones = o; return this; }

        public Comprobante rehidratar() {
            Comprobante c = new Comprobante(id, tenantId, tipo, serie, numero, fechaEmision, horaEmision, fechaVencimiento, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencion, percepcion, anticipos, referencias, redondeo, nota, tasaIgv, estado);
            c.hash = hash; c.nombreArchivo = nombreArchivo; c.xmlKey = xmlKey; c.cdrKey = cdrKey; c.cdr = cdr;
            c.intentos = intentos; c.ultimoError = ultimoError; c.observaciones = observaciones;
            return c;
        }
    }

    public void asignarNumero(long numero, String rucEmisor) {
        if (this.numero != null) throw new DomainException("NUMERO_YA_ASIGNADO", "El comprobante ya tiene número");
        this.numero = numero;
        this.nombreArchivo = NombreArchivo.de(rucEmisor, tipo, serie, numero);
    }

    public void firmar(String hash, String xmlKey) {
        transitar(EstadoDocumento.FIRMADO);
        this.hash = hash; this.xmlKey = xmlKey;
    }

    public void marcarEnviado() { transitar(EstadoDocumento.ENVIADO); }

    public void aplicarCdr(Cdr cdr, String cdrKey) {
        EstadoDocumento destino = cdr.esRechazo() ? EstadoDocumento.RECHAZADO
                : cdr.tieneObservaciones() ? EstadoDocumento.ACEPTADO_CON_OBS : EstadoDocumento.ACEPTADO;
        transitar(destino);
        this.cdr = cdr; this.cdrKey = cdrKey; this.ultimoError = null;
    }

    /** CDR recuperado de SUNAT para un comprobante que ya tenía estado final pero perdió su constancia en el storage. */
    public void restaurarCdr(Cdr cdr, String cdrKey) {
        if (!estado.esFinalAceptado() && estado != EstadoDocumento.RECHAZADO && estado != EstadoDocumento.ANULADO)
            throw new DomainException("TRANSICION_INVALIDA", "Solo se restaura el CDR de un comprobante ya resuelto por SUNAT; este está " + estado);
        this.cdr = cdr; this.cdrKey = cdrKey;
    }

    public void marcarErrorEnvio(String motivo) {
        transitar(EstadoDocumento.ERROR_ENVIO);
        this.intentos++; this.ultimoError = motivo;
    }

    /** SUNAT aceptó la comunicación de baja que lo incluye: el número queda consumido y el comprobante deja de ser válido. */
    public void anular() { transitar(EstadoDocumento.ANULADO); }

    /** Último día en que SUNAT acepta recibirlo ({@link PlazoEnvio}). */
    public LocalDate fechaLimiteEnvio() { return PlazoEnvio.fechaLimite(tipo, fechaEmision); }

    public boolean fueraDePlazo(LocalDate hoy) { return PlazoEnvio.vencido(tipo, fechaEmision, hoy); }

    /** Venció el plazo sin llegar a SUNAT: terminal, el número queda consumido y hay que emitir de nuevo (2108). */
    public void marcarFueraDePlazo(LocalDate hoy) {
        if (!fueraDePlazo(hoy)) throw new DomainException("TRANSICION_INVALIDA", "El plazo de envío vence el " + fechaLimiteEnvio() + ": todavía se puede enviar");
        transitar(EstadoDocumento.FUERA_DE_PLAZO);
        this.ultimoError = "2108 - Presentación fuera de fecha: el plazo venció el " + fechaLimiteEnvio();
    }

    public void rechazarPorFault(String codigo, String descripcion) {
        if (estado.esEnviable()) transitar(EstadoDocumento.ENVIADO);
        transitar(EstadoDocumento.RECHAZADO);
        this.cdr = new Cdr(codigo, descripcion, List.of());
    }

    private void transitar(EstadoDocumento destino) {
        if (!estado.puedeTransitarA(destino))
            throw new DomainException("TRANSICION_INVALIDA", "No se puede pasar de " + estado + " a " + destino);
        estado = destino;
    }

}
