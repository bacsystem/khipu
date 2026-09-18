package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.time.Clock;
import java.time.LocalDate;
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
    private final String moneda;
    private final String tipoOperacion;
    private final Receptor receptor;
    private final List<Item> items;
    private final FormaPago formaPago;
    private final Descuento descuentoGlobal;
    private final Detraccion detraccion;
    private final RetencionIgv retencion;
    private final Percepcion percepcion;
    private final List<Anticipo> anticipos;
    private final Totales totales;
    private EstadoDocumento estado;
    private String hash;
    private String nombreArchivo;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;

    private Comprobante(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero, LocalDate fechaEmision,
                        String moneda, String tipoOperacion, Receptor receptor, List<Item> items, FormaPago formaPago,
                        Descuento descuentoGlobal, Detraccion detraccion, RetencionIgv retencion, Percepcion percepcion, List<Anticipo> anticipos, EstadoDocumento estado) {
        this.id = id; this.tenantId = tenantId; this.tipo = tipo; this.serie = serie; this.numero = numero;
        this.fechaEmision = fechaEmision; this.moneda = moneda; this.tipoOperacion = tipoOperacion;
        this.receptor = receptor; this.items = List.copyOf(items); this.formaPago = formaPago; this.descuentoGlobal = descuentoGlobal;
        this.anticipos = anticipos == null ? List.of() : List.copyOf(anticipos);
        this.totales = Totales.calcular(this.items, descuentoGlobal, this.anticipos, Icbper.tasaVigente(fechaEmision));
        // Detracción, retención y percepción se completan contra el importe total ya calculado (montos por defecto, 3208 y tolerancias SUNAT).
        this.detraccion = detraccion == null ? null : detraccion.completarContra(moneda, this.totales.total());
        this.retencion = retencion == null ? null : retencion.completarContra(this.totales.total());
        this.percepcion = percepcion == null ? null : percepcion.completarContra(tipoOperacion, formaPago, moneda, this.totales.total());
        this.estado = estado;
    }

    /** Factura al contado (la forma de pago por defecto). */
    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda,
                                           String tipoOperacion, Receptor receptor, List<Item> items, Clock clock) {
        return crearFactura(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items, FormaPago.contado(), clock);
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda,
                                           String tipoOperacion, Receptor receptor, List<Item> items, FormaPago formaPago, Clock clock) {
        return crearFactura(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items, formaPago, null, clock);
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                           Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, Clock clock) {
        return crearFactura(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, null, clock);
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                           Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, Detraccion detraccion, Clock clock) {
        return crearFactura(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, detraccion, null, null, clock);
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                           Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, Detraccion detraccion,
                                           RetencionIgv retencion, Percepcion percepcion, Clock clock) {
        return crearFactura(tenantId, serie, fechaEmision, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, detraccion, retencion, percepcion, List.of(), clock);
    }

    public static Comprobante crearFactura(UUID tenantId, String serie, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                           Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, Detraccion detraccion,
                                           RetencionIgv retencion, Percepcion percepcion, List<Anticipo> anticipos, Clock clock) {
        if (!TipoDocumento.FACTURA.serieValida(serie)) throw new DomainException("SERIE_INVALIDA", "Serie de factura inválida: " + serie);
        if (fechaEmision.isAfter(LocalDate.now(clock))) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
        if (items == null || items.isEmpty()) throw new DomainException("SIN_ITEMS", "La factura debe tener al menos un ítem");
        if (receptor == null || !receptor.esRuc()) throw new DomainException("RECEPTOR_INVALIDO", "La factura requiere un receptor con RUC válido");
        if (moneda == null || !moneda.matches("PEN|USD|EUR")) throw new DomainException("MONEDA_INVALIDA", "Moneda no soportada: " + moneda);
        if (formaPago == null) throw new DomainException("FORMA_PAGO_INVALIDA", "3244 - Debe consignar la forma de pago (contado o crédito)");
        String operacion = tipoOperacion == null ? "0101" : tipoOperacion;
        validarTipoOperacion(operacion);
        if (detraccion == null && Detraccion.TIPOS_OPERACION.contains(operacion))
            throw new DomainException("DETRACCION_INVALIDA", "3127 - El tipo de operación " + operacion + " exige los datos de la detracción (bien/servicio, porcentaje, monto y cuenta)");
        if (detraccion != null) detraccion.validarContra(operacion);
        if (percepcion == null && Percepcion.TIPO_OPERACION.equals(operacion) && !formaPago.esCredito())
            throw new DomainException("PERCEPCION_INVALIDA", "3093 - Una operación sujeta a percepción (2001) al contado debe informar la percepción");
        if (anticipos != null && anticipos.stream().map(Anticipo::comprobante).distinct().count() < anticipos.size())
            throw new DomainException("ANTICIPO_INVALIDO", "3215 - La misma factura de anticipo aparece más de una vez");
        Comprobante c = new Comprobante(UUID.randomUUID(), tenantId, TipoDocumento.FACTURA, serie, null, fechaEmision,
                moneda, operacion, receptor, items, formaPago, descuentoGlobal, detraccion, retencion, percepcion, anticipos, EstadoDocumento.RECIBIDO);
        formaPago.validarContra(c.totales.total(), fechaEmision);
        return c;
    }

    /** Regla 3206: el tipo de operación debe existir en el catálogo 51 y aplicar a facturas (columna "Tipo de Comprobante asociado"). */
    private static void validarTipoOperacion(String operacion) {
        CatalogoSunat.Entrada e = CatalogoSunat.porId("51").flatMap(c -> c.entrada(operacion))
                .orElseThrow(() -> new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " no existe en el catálogo 51"));
        String aplicaA = e.extra().getOrDefault("Tipo de Comprobante asociado", "");
        if (!aplicaA.toLowerCase().contains("factura"))
            throw new DomainException("TIPO_OPERACION_INVALIDO", "3206 - El tipo de operación " + operacion + " (" + e.descripcion() + ") no aplica a facturas: " + aplicaA);
    }

    /** Solo para persistencia: reconstruye sin validar reglas de creación. */
    public static Comprobante rehidratar(UUID id, UUID tenantId, TipoDocumento tipo, String serie, Long numero,
                                         LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor receptor,
                                         List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, Detraccion detraccion,
                                         RetencionIgv retencion, Percepcion percepcion, List<Anticipo> anticipos, EstadoDocumento estado,
                                         String hash, String nombreArchivo, String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError) {
        Comprobante c = new Comprobante(id, tenantId, tipo, serie, numero, fechaEmision, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, detraccion, retencion, percepcion, anticipos, estado);
        c.hash = hash; c.nombreArchivo = nombreArchivo; c.xmlKey = xmlKey; c.cdrKey = cdrKey; c.cdr = cdr;
        c.intentos = intentos; c.ultimoError = ultimoError;
        return c;
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

    public void marcarErrorEnvio(String motivo) {
        transitar(EstadoDocumento.ERROR_ENVIO);
        this.intentos++; this.ultimoError = motivo;
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
