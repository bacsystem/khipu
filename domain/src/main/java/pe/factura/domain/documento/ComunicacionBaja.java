package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * La retención (20) y la percepción (40), #40, tampoco van en un RA: se revierten en un resumen de reversiones ({@code RR-yyyymmdd-N}, hoja Resumen de reversiones1_0), que es el
 * mismo VoidedDocuments con otro identificador, enviado con sendSummary al servicio de otros CPE. Su hoja no tiene plazo para el 20 ni el 40 (la 2958 de 5 días es solo del 04).
 * <p>
 * El mismo resumen sirve también para informar una boleta que pasó el envío individual sin llegar a SUNAT (1079, 274-H1): su línea va en estado 1 (alta,
 * {@link #altaEnResumen}) y, al aceptarse, la boleta queda ACEPTADA en vez de ANULADA.
 */
@Getter
public class ComunicacionBaja {
    public static final int PLAZO_DIAS = 7;
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
    /** El LineID de esta fila en su resumen (1 en un RA y en un RC de una sola línea). */
    private final int linea;
    /** Cuántas líneas tiene el resumen al que pertenece (#301): las filas de un mismo RC comparten identificador, XML, ticket y CDR. */
    private final int lineas;
    private EstadoBaja estado;
    private String ticket;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;

    private ComunicacionBaja(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante, boolean deBoleta,
                             String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado, int linea, int lineas) {
        this.id = id; this.tenantId = tenantId; this.fechaGeneracion = fechaGeneracion; this.correlativo = correlativo;
        this.comprobanteId = comprobanteId; this.tipoComprobante = tipoComprobante; this.deBoleta = deBoleta; this.serie = serie; this.numero = numero;
        this.fechaReferencia = fechaReferencia; this.motivo = motivo; this.condicion = condicion; this.estado = estado; this.linea = linea; this.lineas = lineas;
    }

    /**
     * Una boleta que ya no se puede enviar sola (pasó el envío individual, 1079): se informa en un resumen diario, con su línea en estado 1 (alta). Solo si
     * SUNAT todavía no la tiene: firmada o con el envío fallido. Hasta el 7.º día en plazo; después, fuera de plazo (#331): SUNAT no rechaza el alta tardía; la norma
     * prevé que la observe (4443, y entonces la boleta queda ACEPTADO_CON_OBS), pero e-beta, con 8 y 30 días de atraso, la aceptó sin observaciones (ACEPTADO). La NC 10 y
     * la ND 03 de boleta no van en el resumen nunca.
     * <p>
     * Una alta es un RC propio, con la línea de este solo comprobante (como cada baja). Varias de una misma fecha de emisión se agrupan con {@link #altasEnResumen} (#301).
     */
    public static ComunicacionBaja altaEnResumen(Comprobante c, int correlativoDelDia, Clock clock) {
        return altasEnResumen(List.of(c), correlativoDelDia, clock).get(0);
    }

    /** SFS 0158: un resumen diario lleva hasta 500 líneas. */
    public static final int MAX_LINEAS_RESUMEN = 500;

    /**
     * Las boletas (y notas de boleta) de una misma fecha de emisión que ya solo van en el resumen diario, informadas en <b>un</b> RC (#301): una fila por comprobante, todas con
     * el mismo identificador (y, por tanto, el mismo XML, ticket y CDR), en el orden dado como LineID 1, 2, … Hasta {@value #MAX_LINEAS_RESUMEN} líneas.
     * <p>
     * Una línea con error rechaza el resumen entero (2987), así que aquí solo entran comprobantes que ya pasaron su validación al emitirse y que SUNAT todavía no tiene. SUNAT pide que
     * todas compartan la fecha de emisión (cbc:ReferenceDate) y, como las tardías (pasado el 7.º día) pueden observarse con la 4443, no se mezclan con las que están en plazo; con la misma
     * fecha de emisión esto último se cumple solo, porque el plazo se cuenta desde ella. Las altas nunca comparten resumen con una baja (3095/3096): una baja siempre es un RC propio.
     */
    public static List<ComunicacionBaja> altasEnResumen(List<Comprobante> comprobantes, int correlativoDelDia, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (comprobantes.isEmpty()) throw new DomainException("RESUMEN_INVALIDO", "Un resumen diario lleva al menos una línea");
        if (comprobantes.size() > MAX_LINEAS_RESUMEN)
            throw new DomainException("RESUMEN_INVALIDO", "0158 - Un resumen diario lleva hasta " + MAX_LINEAS_RESUMEN + " líneas; se pidieron " + comprobantes.size());
        if (correlativoDelDia < 1) throw new DomainException("RESUMEN_INVALIDO", "El correlativo del día debe ser mayor que cero");
        LocalDate fechaEmision = comprobantes.get(0).fechaEmision();
        for (Comprobante c : comprobantes) {
            if (!c.esDeBoleta())
                throw new DomainException("RESUMEN_INVALIDO", "Solo una boleta o una nota que la modifica se informa en un resumen diario; " + c.serie() + "-" + c.numero() + " no lo es");
            if (!c.estado().esEnviable())
                throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " está " + c.estado() + ": solo se informa en el resumen una boleta que SUNAT todavía no tiene");
            if (!c.soloPorResumen(hoy))
                throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " no va en el resumen diario: todavía va sola, o es una nota de crédito 10 o de débito 03");
            if (!c.fechaEmision().equals(fechaEmision))
                throw new DomainException("RESUMEN_INVALIDO", "2375 - Todas las líneas de un resumen diario son de la misma fecha de emisión; " + c.serie() + "-" + c.numero() + " es del " + c.fechaEmision() + " y el resumen del " + fechaEmision);
        }
        long distintos = comprobantes.stream().map(Comprobante::id).distinct().count();
        if (distintos != comprobantes.size()) throw new DomainException("RESUMEN_INVALIDO", "Un comprobante no puede ir dos veces en el mismo resumen");
        String motivo = comprobantes.get(0).informaFueraDePlazo(hoy)
                ? "Pasó el 7.º día: se informa en el resumen diario fuera de plazo (SUNAT puede observarla con 4443)"
                : "Pasó el envío individual: se informa en el resumen diario";
        List<ComunicacionBaja> filas = new ArrayList<>(comprobantes.size());
        for (int i = 0; i < comprobantes.size(); i++) {
            Comprobante c = comprobantes.get(i);
            filas.add(new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), true, c.serie(), c.numero(), c.fechaEmision(),
                    motivo, Condicion.ALTA, EstadoBaja.GENERADA, i + 1, comprobantes.size()));
        }
        return List.copyOf(filas);
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
        // #327: la hoja Comunicación de Baja1_0 no admite el 04 (2308: 01, 07, 08, 25, 28, 30, 34, 42 y 56); la liquidación de compra se anula en un resumen de reversiones
        // (RR-…, Resumen de reversiones1_0: tipos 20, 40 y 04, dentro de 5 días desde su CDR, 2958), que khipu todavía no emite.
        if (c.tipo() == TipoDocumento.LIQUIDACION_COMPRA)
            throw new DomainException("BAJA_INVALIDA", "2308 - Una liquidación de compra no se da de baja con una comunicación de baja: SUNAT la anula en un resumen de reversiones"
                    + " (hasta 5 días después de su constancia de recepción, regla 2958), que khipu todavía no emite");
        if (!c.estado().esFinalAceptado())
            throw new DomainException("BAJA_INVALIDA", (c.esDeBoleta() ? "2663" : c.esDeAgente() ? "2750" : "2105/2398") + " - Solo se puede " + (c.esDeAgente() ? "revertir" : "dar de baja")
                    + " un comprobante aceptado por SUNAT; " + c.serie() + "-" + c.numero() + " está " + c.estado());
        // #40: el resumen de reversiones no tiene plazo para la retención ni la percepción.
        if (!c.esDeAgente() && ChronoUnit.DAYS.between(c.fechaEmision(), hoy) > PLAZO_DIAS)
            throw new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja " + c.serie() + "-" + c.numero() + " venció: se emitió el " + c.fechaEmision()
                    + " y la comunicación debe presentarse dentro de los " + PLAZO_DIAS + " días calendario siguientes a su emisión");
        String m = motivo == null ? "" : motivo.strip();
        if (m.length() < 3 || m.length() > 100 || m.chars().anyMatch(Character::isISOControl))
            throw new DomainException("BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
        if (correlativoDelDia < 1) throw new DomainException("BAJA_INVALIDA", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), c.esDeBoleta(), c.serie(), c.numero(), c.fechaEmision(), m,
                Condicion.BAJA, EstadoBaja.GENERADA, 1, 1);
    }

    /** Solo para persistencia; {@code linea} y {@code lineas} son la posición de la fila en su resumen diario y las líneas que tiene (#301; 1 y 1 en una baja). */
    public static ComunicacionBaja rehidratar(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante, boolean deBoleta,
                                              String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado, String ticket,
                                              String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError, int linea, int lineas) {
        ComunicacionBaja b = new ComunicacionBaja(id, tenantId, fechaGeneracion, correlativo, comprobanteId, tipoComprobante, deBoleta, serie, numero, fechaReferencia, motivo, condicion, estado, linea, lineas);
        b.ticket = ticket; b.xmlKey = xmlKey; b.cdrKey = cdrKey; b.cdr = cdr; b.intentos = intentos; b.ultimoError = ultimoError;
        return b;
    }

    /** Si el resumen al que pertenece tiene más de una línea (#301): su rechazo no dice cuál comprobante lo causó. */
    public boolean deVariasLineas() { return lineas > 1; }

    /**
     * Un alta de varias líneas que SUNAT rechazó entero (#301): el comprobante de esta fila se vuelve a informar <b>solo</b>, para aislar la línea que lo rompió, y por eso no se
     * agrupa con otros. Es la regla de la que parte {@code BoletasPorInformar.paraAgruparEnResumen} en SQL ({@code condicion = 'ALTA' AND estado = 'RECHAZADA' AND lineas > 1}):
     * si cambia aquí, cambia allá.
     */
    public boolean aislaSuComprobante() { return alta() && estado == EstadoBaja.RECHAZADA && deVariasLineas(); }

    /** Si esta fila es de un resumen generado después que {@code otra} (por fecha de generación y, el mismo día, por correlativo): sirve para saber cuál es el más reciente de un comprobante. */
    public boolean esPosteriorA(ComunicacionBaja otra) {
        int porFecha = fechaGeneracion.compareTo(otra.fechaGeneracion);
        return porFecha > 0 || (porFecha == 0 && correlativo > otra.correlativo);
    }

    /**
     * Las filas de un mismo resumen comparten el ciclo —un envío, un ticket, un CDR—: tras avanzar una (la primera), las demás copian su estado. Solo entre filas del mismo
     * resumen, y sin pasar por {@link #transitar}: el avance ya fue validado en la fila de origen.
     */
    public void copiarCicloDe(ComunicacionBaja otra) {
        if (!otra.tenantId.equals(tenantId) || !otra.fechaGeneracion.equals(fechaGeneracion) || otra.correlativo != correlativo)
            throw new DomainException("TRANSICION_INVALIDA", "Solo las filas de un mismo resumen comparten su ciclo");
        this.estado = otra.estado; this.ticket = otra.ticket; this.xmlKey = otra.xmlKey; this.cdrKey = otra.cdrKey; this.cdr = otra.cdr;
        this.intentos = otra.intentos; this.ultimoError = otra.ultimoError;
    }

    /** Una boleta, o una nota que la modifica, se anula en el resumen diario (SummaryDocuments), no en una comunicación de baja (VoidedDocuments: 2308/2310). */
    public boolean resumenDiario() { return deBoleta; }

    /** Una retención o una percepción (#40) se revierte en un resumen de reversiones (RR): VoidedDocuments como el RA, con otro identificador y otro servicio de SUNAT. */
    public boolean reversion() { return tipoComprobante.deAgente(); }

    /**
     * Un alta informada pasado el 7.º día desde la emisión (#331): SUNAT no la rechaza; puede observarla con la 4443 (e-beta no lo hizo). Sale de las fechas que ya
     * guarda (la del RC y el cbc:ReferenceDate), así que vale también para las altas ya persistidas.
     * <p>
     * Es una previsión, no la respuesta de SUNAT: SUNAT cuenta hasta la recepción del resumen (RC fila 14), y un alta generada el 7.º día cuyo envío falla y se
     * reintenta al día siguiente llega fuera de plazo aunque esto diga que no. Si SUNAT la observó con la 4443, está en el CDR del comprobante (y si no, no hay).
     */
    public boolean fueraDePlazo() { return alta() && ChronoUnit.DAYS.between(fechaReferencia, fechaGeneracion) > PlazoEnvio.DIAS_RESUMEN_DIARIO; }

    /** Si informa una boleta (alta) en vez de anularla. */
    public boolean alta() { return condicion == Condicion.ALTA; }

    /** {@code RA-20260918-1} (o {@code RC-…} para una boleta o una nota que la modifica, {@code RR-…} para una retención o una percepción): cbc:ID y parte del nombre de archivo (2220, 2346). */
    public String identificador() { return (resumenDiario() ? "RC-" : reversion() ? "RR-" : "RA-") + fechaGeneracion.format(FECHA_ID) + "-" + correlativo; }
    private static final java.util.regex.Pattern CDR_DE_RESUMEN = java.util.regex.Pattern.compile("(?:^|/)R-\\d{11}-(RC-\\d{8}-\\d+)\\.zip$");

    /**
     * #362 B10: de qué resumen diario es el CDR guardado en esa clave de almacenamiento ({@code …/R-<ruc>-RC-aaaammdd-n.zip}, la que arma el envío del resumen), o vacío si no
     * es la de un resumen: el CDR propio de un comprobante se guarda como {@code R-<ruc>-<tipo>-<serie>-<n>.zip}. Solo el RC: el de un RA es de la baja, nunca del comprobante.
     */
    public static java.util.Optional<String> identificadorDelResumenDeCdr(String cdrKey) {
        if (cdrKey == null) return java.util.Optional.empty();
        java.util.regex.Matcher m = CDR_DE_RESUMEN.matcher(cdrKey);
        return m.find() ? java.util.Optional.of(m.group(1)) : java.util.Optional.empty();
    }

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

    /**
     * Catálogo 19 (estado del ítem del resumen diario): 1 adicionar, 3 anulado. El 2 (modificar) no existe a propósito (#360): SUNAT lo permite, pero una boleta aceptada
     * con un dato equivocado se corrige con una nota de crédito o de débito, no reescribiendo la línea del resumen; así el comprobante queda como lo vio SUNAT.
     */
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
        ACEPTADA, RECHAZADA;

        public boolean puedeTransitarA(EstadoBaja d) {
            return switch (this) {
                case GENERADA, ERROR_ENVIO -> d == ENVIADA || d == ERROR_ENVIO || d == RECHAZADA;
                case ENVIADA -> d == ACEPTADA || d == RECHAZADA;
                case ACEPTADA, RECHAZADA -> false;
            };
        }
    }
}
