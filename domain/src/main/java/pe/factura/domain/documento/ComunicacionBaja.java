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
     * SUNAT todavía no la tiene: firmada o con el envío fallido. Hasta el 7.º día en plazo; después, fuera de plazo (#331): SUNAT no rechaza el alta tardía; la norma
     * prevé que la observe (4443, y entonces la boleta queda ACEPTADO_CON_OBS), pero e-beta, con 8 y 30 días de atraso, la aceptó sin observaciones (ACEPTADO). La NC 10 y
     * la ND 03 de boleta no van en el resumen nunca.
     * <p>
     * Cada alta es un RC propio, con la línea de este solo comprobante (como cada baja): una boleta tardía nunca comparte resumen con una en plazo. Es lo que
     * pidió #331 mientras la 4443 no estaba validada: si SUNAT rechazara el resumen entero, solo cae la boleta tardía, que antes de #331 nunca se informaba.
     * Agruparlas en un mismo RC por fecha de emisión queda para después, separando siempre las tardías.
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
        String motivo = c.informaFueraDePlazo(hoy)
                ? "Pasó el 7.º día: se informa en el resumen diario fuera de plazo (SUNAT puede observarla con 4443)"
                : "Pasó el envío individual: se informa en el resumen diario";
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), true, c.serie(), c.numero(), c.fechaEmision(),
                motivo, Condicion.ALTA, EstadoBaja.GENERADA);
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
     * Un alta informada pasado el 7.º día desde la emisión (#331): SUNAT no la rechaza; puede observarla con la 4443 (e-beta no lo hizo). Sale de las fechas que ya
     * guarda (la del RC y el cbc:ReferenceDate), así que vale también para las altas ya persistidas.
     * <p>
     * Es una previsión, no la respuesta de SUNAT: SUNAT cuenta hasta la recepción del resumen (RC fila 14), y un alta generada el 7.º día cuyo envío falla y se
     * reintenta al día siguiente llega fuera de plazo aunque esto diga que no. Si SUNAT la observó con la 4443, está en el CDR del comprobante (y si no, no hay).
     */
    public boolean fueraDePlazo() { return alta() && ChronoUnit.DAYS.between(fechaReferencia, fechaGeneracion) > PlazoEnvio.DIAS_RESUMEN_DIARIO; }

    /** Si informa una boleta (alta) en vez de anularla. */
    public boolean alta() { return condicion == Condicion.ALTA; }

    /** {@code RA-20260918-1} (o {@code RC-…} para una boleta o una nota que la modifica): cbc:ID y parte del nombre de archivo (reglas 2220, 2346). */
    public String identificador() { return (resumenDiario() ? "RC-" : "RA-") + fechaGeneracion.format(FECHA_ID) + "-" + correlativo; }
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
