package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Comunicación de baja (VoidedDocuments, {@code RA-yyyymmdd-N}): anula ante SUNAT una factura o nota ya aceptada, dentro
 * de los 7 días calendario siguientes a su emisión (regla 2957). Viaja por {@code sendSummary} (asíncrono: SUNAT devuelve
 * un ticket y el CDR se recoge con {@code getStatus}); al aceptarse, el comprobante pasa a {@code ANULADO}.
 * <p>
 * Una comunicación por comprobante: SUNAT exige que todos los documentos de un RA compartan fecha de emisión
 * ({@code ReferenceDate}, regla 2375) y el caso de uso es "anular esta factura"; el correlativo es por empresa y día.
 * <p>
 * Una boleta no va en un RA (2308): se anula en el resumen diario (SummaryDocuments, {@code RC-yyyymmdd-N}, #20) con su línea en estado 3. El ciclo es el
 * mismo (sendSummary, ticket, getStatus, CDR, ANULADO), igual que el plazo (2957, 7 días) y que SUNAT la tenga (2663: «el documento indicado no existe»), así que es esta misma
 * baja con otro identificador. El correlativo del día se comparte entre RA y RC: SUNAT solo exige que cada identificador no se repita (2223).
 * <p>
 * El mismo resumen sirve también para informar una boleta que pasó el envío individual sin llegar a SUNAT (1079, 274-H1): su línea va en estado 1 (alta,
 * {@link #altaEnResumen}) y, al aceptarse, la boleta queda ACEPTADA en vez de ANULADA.
 */
@Getter
public class ComunicacionBaja {
    public static final int PLAZO_DIAS = 7;
    /**
     * Desde cuándo el resumen diario informa las gratuitas por afectación (catálogo 11: 06, 07, 08; el 05 se desactiva) y su IGV en 9996 «GRA» (Control de Cambios
     * de SUNAT, filas 516 y 543, postergadas del 2026-08-01 al 2027-01-01; #289). Decide la fecha de generación del resumen (#337): ver {@link #formatoCaducado}.
     */
    public static final LocalDate GRATUITAS_POR_AFECTACION_DESDE = LocalDate.of(2027, 1, 1);
    private static final DateTimeFormatter FECHA_ID = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UUID id;
    private final UUID tenantId;
    private final LocalDate fechaGeneracion;
    private final int correlativo;
    private final UUID comprobanteId;
    private final TipoDocumento tipoComprobante;
    /** Una boleta o una nota que la modifica ({@link Comprobante#esDeBoleta()}): va en un resumen diario, nunca en un RA (2308/2310). */
    private final boolean deBoleta;
    private final String serie;
    private final long numero;
    /** Fecha de emisión del comprobante dado de baja: cbc:ReferenceDate. */
    private final LocalDate fechaReferencia;
    private final String motivo;
    /** Estado de la línea en el resumen diario (catálogo 19): alta o baja. En un RA, siempre baja. */
    private final Condicion condicion;
    private EstadoBaja estado;
    private String ticket;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;

    private ComunicacionBaja(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante, boolean deBoleta,
                             String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado) {
        this.id = id; this.tenantId = tenantId; this.fechaGeneracion = fechaGeneracion; this.correlativo = correlativo;
        this.comprobanteId = comprobanteId; this.tipoComprobante = tipoComprobante; this.deBoleta = deBoleta; this.serie = serie; this.numero = numero;
        this.fechaReferencia = fechaReferencia; this.motivo = motivo; this.condicion = condicion; this.estado = estado;
    }

    /**
     * Una boleta que ya no se puede enviar sola (pasó el envío individual, 1079): se informa en un resumen diario, con su línea en estado 1 (alta). Solo si
     * SUNAT todavía no la tiene: firmada o con el envío fallido. Hasta el 7.º día en plazo; después, fuera de plazo (#331): SUNAT no rechaza el alta tardía,
     * la observa (4443), y la boleta queda ACEPTADO_CON_OBS. La NC 10 y la ND 03 de boleta no van en el resumen nunca.
     * <p>
     * Cada alta es un RC propio, con la línea de este solo comprobante (como cada baja): una boleta tardía nunca comparte resumen con una en plazo. Es lo que
     * pide #331 mientras la observación 4443 no esté validada en e-beta: si SUNAT rechazara el resumen entero, solo cae la boleta tardía, que antes de #331
     * nunca se informaba. Agruparlas en un mismo RC por fecha de emisión queda para después, separando siempre las tardías.
     */
    public static ComunicacionBaja altaEnResumen(Comprobante c, int correlativoDelDia, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (!c.esDeBoleta())
            throw new DomainException("RESUMEN_INVALIDO", "Solo una boleta o una nota que la modifica se informa en un resumen diario; " + c.serie() + "-" + c.numero() + " no lo es");
        if (!c.estado().esEnviable())
            throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " está " + c.estado() + ": solo se informa en el resumen una boleta que SUNAT todavía no tiene");
        if (!c.soloPorResumen(hoy))
            throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " no va en el resumen diario: todavía va sola, o es una nota de crédito 10 o de débito 03");
        if (correlativoDelDia < 1) throw new DomainException("RESUMEN_INVALIDO", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), true, c.serie(), c.numero(), c.fechaEmision(),
                motivoDeAlta(c.informaFueraDePlazo(hoy)), Condicion.ALTA, EstadoBaja.GENERADA);
    }

    private static String motivoDeAlta(boolean fueraDePlazo) {
        return fueraDePlazo
                ? "Pasó el 7.º día: se informa en el resumen diario fuera de plazo (SUNAT la observa con 4443)"
                : "Pasó el envío individual: se informa en el resumen diario";
    }

    /**
     * Solo un comprobante aceptado por SUNAT (2398/2323; en el RC, 2663) y emitido hace como máximo 7 días (2957) puede darse de baja. El motivo
     * va en la línea del RA (3–100 caracteres: 2315, 4203); el RC no lo lleva, pero se pide igual: es lo que explica la anulación en khipu.
     * <p>
     * El plazo se cuenta desde la fecha de emisión, en hora de Lima (la zona del {@code clock}), para todo comprobante, boletas y notas de boleta
     * incluidas: es la regla de la RS 000048-2026, vigente desde 2027-01-01 (RS 000143-2026). Hasta 2026-12-31 la boleta lo cuenta desde su CDR
     * aceptada, que nunca es anterior a la emisión: la regla de 2027 es la más estricta y cumple las dos (#302).
     */
    public static ComunicacionBaja crear(Comprobante c, int correlativoDelDia, String motivo, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (!c.estado().esFinalAceptado())
            throw new DomainException("BAJA_INVALIDA", (c.esDeBoleta() ? "2663" : "2105/2398") + " - Solo se puede dar de baja un comprobante aceptado por SUNAT; "
                    + c.serie() + "-" + c.numero() + " está " + c.estado());
        if (ChronoUnit.DAYS.between(c.fechaEmision(), hoy) > PLAZO_DIAS)
            throw new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja " + c.serie() + "-" + c.numero() + " venció: se emitió el " + c.fechaEmision()
                    + " y la comunicación debe presentarse dentro de los " + PLAZO_DIAS + " días calendario siguientes a su emisión");
        String m = motivo == null ? "" : motivo.strip();
        if (m.length() < 3 || m.length() > 100 || m.chars().anyMatch(Character::isISOControl))
            throw new DomainException("BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
        if (correlativoDelDia < 1) throw new DomainException("BAJA_INVALIDA", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), c.esDeBoleta(), c.serie(), c.numero(), c.fechaEmision(), m,
                Condicion.BAJA, EstadoBaja.GENERADA);
    }

    /** Solo para persistencia. */
    public static ComunicacionBaja rehidratar(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante, boolean deBoleta,
                                              String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado, String ticket,
                                              String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError) {
        ComunicacionBaja b = new ComunicacionBaja(id, tenantId, fechaGeneracion, correlativo, comprobanteId, tipoComprobante, deBoleta, serie, numero, fechaReferencia, motivo, condicion, estado);
        b.ticket = ticket; b.xmlKey = xmlKey; b.cdrKey = cdrKey; b.cdr = cdr; b.intentos = intentos; b.ultimoError = ultimoError;
        return b;
    }

    /** Una boleta, o una nota que la modifica, se anula en el resumen diario (SummaryDocuments), no en una comunicación de baja (VoidedDocuments: 2308/2310). */
    public boolean resumenDiario() { return deBoleta; }

    /**
     * Un alta informada pasado el 7.º día desde la emisión (#331): SUNAT la acepta con la observación 4443 en vez de rechazarla. Sale de las fechas que ya
     * guarda (la del RC y el cbc:ReferenceDate), así que vale también para las altas ya persistidas.
     * <p>
     * Es una previsión, no la respuesta de SUNAT: SUNAT cuenta hasta la recepción del resumen (RC fila 14), y un alta generada el 7.º día cuyo envío falla y se
     * reintenta al día siguiente llega fuera de plazo aunque esto diga que no. La 4443 que de verdad aplicó está en el CDR del comprobante.
     */
    public boolean fueraDePlazo() { return alta() && ChronoUnit.DAYS.between(fechaReferencia, fechaGeneracion) > PlazoEnvio.DIAS_RESUMEN_DIARIO; }

    /** Si informa una boleta (alta) en vez de anularla. */
    public boolean alta() { return condicion == Condicion.ALTA; }

    /** Si este resumen informa las gratuitas por afectación (06–08 y 9996) o con el 05: lo decide su fecha de generación (#337). */
    public boolean gratuitasPorAfectacion() { return !fechaGeneracion.isBefore(GRATUITAS_POR_AFECTACION_DESDE); }

    /**
     * #338: un RC con gratuitas generado hasta el 2026-12-31 (con el 05) que SUNAT todavía no recibió (sin ticket) y se intenta desde el 2027-01-01: lleva el 05
     * ya desactivado y SUNAT lo rechazaría. No se puede corregir su fecha (la del ID es la de generación, 2346): se reemplaza por uno nuevo ({@link #regenerar}).
     * {@code hoy} es la fecha de Lima, la del {@code clock}: el cruce es a la medianoche de Lima, no de UTC. Un RA no lleva totales y no cambia.
     */
    public boolean formatoCaducado(Comprobante c, LocalDate hoy) {
        return resumenDiario() && sinEnviar() && !gratuitasPorAfectacion() && !hoy.isBefore(GRATUITAS_POR_AFECTACION_DESDE)
                && c.id().equals(comprobanteId) && c.totales().tieneGratuitas();
    }

    /**
     * La baja ya no se puede presentar hoy: pasaron los 7 días desde la emisión (2957, el mismo tope que {@link #crear}). SUNAT lo mide al recibirla, así que una
     * baja admitida el 7.º día que hay que regenerar al 8.º ya no está en plazo. Un alta no vence: fuera de plazo SUNAT la observa (4443), no la rechaza.
     */
    public boolean plazoVencido(LocalDate hoy) { return !alta() && ChronoUnit.DAYS.between(fechaReferencia, hoy) > PLAZO_DIAS; }

    /**
     * #338: el mismo resumen, generado de nuevo hoy, para reemplazar a este cuando su formato caducó ({@link #formatoCaducado}): otro correlativo, la fecha y el
     * formato de hoy, el mismo comprobante, la misma condición y el mismo motivo. Un alta se vuelve a juzgar con la fecha nueva (#331): pasado el 7.º día sale
     * fuera de plazo. Una baja con el plazo vencido no se regenera (2957: ver {@link #plazoVencido} y {@link #rechazarPorPlazo}).
     */
    public ComunicacionBaja regenerar(int correlativoDelDia, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (!resumenDiario() || !sinEnviar())
            throw new DomainException("RESUMEN_INVALIDO", "Solo se regenera un resumen diario que SUNAT todavía no recibió; " + identificador() + " está " + estado);
        if (plazoVencido(hoy))
            throw new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja " + serie + "-" + numero + " venció: se emitió el " + fechaReferencia
                    + " y la comunicación debe presentarse dentro de los " + PLAZO_DIAS + " días calendario siguientes a su emisión");
        if (correlativoDelDia < 1) throw new DomainException("RESUMEN_INVALIDO", "El correlativo del día debe ser mayor que cero");
        boolean fueraDePlazo = alta() && ChronoUnit.DAYS.between(fechaReferencia, hoy) > PlazoEnvio.DIAS_RESUMEN_DIARIO;
        return new ComunicacionBaja(UUID.randomUUID(), tenantId, hoy, correlativoDelDia, comprobanteId, tipoComprobante, deBoleta, serie, numero, fechaReferencia,
                alta() ? motivoDeAlta(fueraDePlazo) : motivo, condicion, EstadoBaja.GENERADA);
    }

    /** #338: este resumen caducó y lo reemplaza {@code nuevo}: queda DESCARTADO, fuera de todo reintento, y dice cuál lo reemplazó. */
    public void reemplazarPor(ComunicacionBaja nuevo) {
        transitar(EstadoBaja.DESCARTADA);
        this.ultimoError = "Reemplazado por " + nuevo.identificador() + ": generado con el código 05 de las gratuitas, que SUNAT desactivó el "
                + GRATUITAS_POR_AFECTACION_DESDE + ", y todavía sin enviar";
    }

    /**
     * #338: un envío de este resumen que ya estaba en vuelo cuando otro proceso lo descartó llegó igual a SUNAT. No revive (sigue DESCARTADO: lo que sigue es el
     * que lo reemplazó), pero el ticket queda anotado para poder revisarlo en SUNAT.
     */
    public void registrarEnvioTardio(String ticket) {
        if (estado != EstadoBaja.DESCARTADA) throw new DomainException("TRANSICION_INVALIDA", "Solo un resumen descartado registra un envío tardío; " + identificador() + " está " + estado);
        this.ticket = ticket;
        this.ultimoError = ultimoError + ". Un envío que ya estaba en curso llegó igual a SUNAT (ticket " + ticket + "): khipu no lo sigue, sigue con el que lo reemplazó";
    }

    /**
     * #338: la baja caducó y ya no se puede regenerar en plazo ({@link #plazoVencido}). No se envía: SUNAT la rechazaría con 2957. Queda RECHAZADA con ese código,
     * como si SUNAT lo hubiera dicho, y el comprobante sigue vigente (se corrige con una nota de crédito).
     */
    public void rechazarPorPlazo(LocalDate hoy) {
        if (!plazoVencido(hoy)) throw new DomainException("TRANSICION_INVALIDA", "La baja " + identificador() + " todavía está en plazo");
        transitar(EstadoBaja.RECHAZADA);
        this.cdr = new Cdr("2957", "El plazo para dar de baja venció: el comprobante se emitió el " + fechaReferencia + " y la comunicación debía presentarse dentro de los "
                + PLAZO_DIAS + " días calendario siguientes. " + identificador() + " no se envió: tenía el código 05 de las gratuitas, que SUNAT desactivó el "
                + GRATUITAS_POR_AFECTACION_DESDE + ", y uno nuevo llegaría fuera de plazo", java.util.List.of());
    }

    /** Firmado y guardado, pero sin ticket de SUNAT: generado o con el envío fallido. */
    private boolean sinEnviar() { return estado == EstadoBaja.GENERADA || estado == EstadoBaja.ERROR_ENVIO; }

    /** {@code RA-20260918-1} (o {@code RC-…} para una boleta o una nota que la modifica): cbc:ID y parte del nombre de archivo (reglas 2220, 2346). */
    public String identificador() { return (resumenDiario() ? "RC-" : "RA-") + fechaGeneracion.format(FECHA_ID) + "-" + correlativo; }
    /** {@code RUC-RA-20260918-1} o {@code RUC-RC-…}, sin extensión. */
    public String nombreArchivo(String rucEmisor) { return rucEmisor + "-" + identificador(); }

    public void firmar(String xmlKey) { this.xmlKey = xmlKey; }
    public void marcarEnviada(String ticket) { transitar(EstadoBaja.ENVIADA); this.ticket = ticket; this.ultimoError = null; }
    public void marcarErrorEnvio(String motivo) { transitar(EstadoBaja.ERROR_ENVIO); this.intentos++; this.ultimoError = motivo; }
    /** getStatus falló o SUNAT sigue procesando (98): el ticket se conserva y se vuelve a consultar. */
    public void registrarConsultaPendiente(String motivo) { if (estado != EstadoBaja.ENVIADA) throw new DomainException("TRANSICION_INVALIDA", "Sin ticket que consultar"); this.intentos++; this.ultimoError = motivo; }
    public void aplicarCdr(Cdr cdr, String cdrKey) {
        transitar(cdr.esRechazo() ? EstadoBaja.RECHAZADA : EstadoBaja.ACEPTADA);
        this.cdr = cdr; this.cdrKey = cdrKey; this.ultimoError = null;
    }
    /** SUNAT rechazó por SOAPFault al consultar el ticket (sin CDR). */
    public void rechazarPorFault(String codigo, String descripcion) {
        transitar(EstadoBaja.RECHAZADA);
        this.cdr = new Cdr(codigo, descripcion, java.util.List.of());
    }

    public boolean pendiente() { return estado == EstadoBaja.GENERADA || estado == EstadoBaja.ERROR_ENVIO || estado == EstadoBaja.ENVIADA; }

    private void transitar(EstadoBaja destino) {
        if (!estado.puedeTransitarA(destino)) throw new DomainException("TRANSICION_INVALIDA", "La baja no puede pasar de " + estado + " a " + destino);
        estado = destino;
    }

    /** Catálogo 19 (estado del ítem del resumen diario): 1 adicionar, 3 anulado. */
    public enum Condicion {
        ALTA("1"), BAJA("3");

        private final String codigo;
        Condicion(String codigo) { this.codigo = codigo; }
        public String codigo() { return codigo; }
    }

    public enum EstadoBaja {
        /** XML firmado y guardado; aún no enviada. */ GENERADA,
        /** SUNAT devolvió ticket; el CDR se recoge con getStatus. */ ENVIADA,
        /** sendSummary no obtuvo ticket (transitorio); se reintenta el envío. */ ERROR_ENVIO,
        ACEPTADA, RECHAZADA,
        /** #338: sin enviar, caducó (su formato ya no vale) y otro resumen lo reemplaza. Terminal: nadie lo reintenta. */ DESCARTADA;

        public boolean puedeTransitarA(EstadoBaja d) {
            return switch (this) {
                case GENERADA, ERROR_ENVIO -> d == ENVIADA || d == ERROR_ENVIO || d == RECHAZADA || d == DESCARTADA;
                case ENVIADA -> d == ACEPTADA || d == RECHAZADA;
                case ACEPTADA, RECHAZADA, DESCARTADA -> false;
            };
        }
    }
}
