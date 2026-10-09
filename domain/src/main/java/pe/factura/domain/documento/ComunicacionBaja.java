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

    private ComunicacionBaja(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante,
                             String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado) {
        this.id = id; this.tenantId = tenantId; this.fechaGeneracion = fechaGeneracion; this.correlativo = correlativo;
        this.comprobanteId = comprobanteId; this.tipoComprobante = tipoComprobante; this.serie = serie; this.numero = numero;
        this.fechaReferencia = fechaReferencia; this.motivo = motivo; this.condicion = condicion; this.estado = estado;
    }

    /**
     * Una boleta que ya no se puede enviar sola (pasó el envío individual, 1079) y todavía está dentro de los 7 días del resumen diario: se informa en
     * uno, con su línea en estado 1 (alta). Solo si SUNAT todavía no la tiene: firmada o con el envío fallido.
     */
    public static ComunicacionBaja altaEnResumen(Comprobante c, int correlativoDelDia, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (c.tipo() != TipoDocumento.BOLETA) throw new DomainException("RESUMEN_INVALIDO", "Solo una boleta se informa en un resumen diario; " + c.serie() + "-" + c.numero() + " no lo es");
        if (!c.estado().esEnviable())
            throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " está " + c.estado() + ": solo se informa en el resumen una boleta que SUNAT todavía no tiene");
        if (!c.soloPorResumen(hoy))
            throw new DomainException("RESUMEN_INVALIDO", c.serie() + "-" + c.numero() + " no está entre el fin del envío individual y el séptimo día: va sola o ya venció");
        if (correlativoDelDia < 1) throw new DomainException("RESUMEN_INVALIDO", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), c.serie(), c.numero(), c.fechaEmision(),
                "Pasó el envío individual: se informa en el resumen diario", Condicion.ALTA, EstadoBaja.GENERADA);
    }

    /**
     * Solo un comprobante aceptado por SUNAT (2398/2323; en el RC, 2663) y emitido hace como máximo 7 días (2957) puede darse de baja. El motivo
     * va en la línea del RA (3–100 caracteres: 2315, 4203); el RC no lo lleva, pero se pide igual: es lo que explica la anulación en khipu.
     */
    public static ComunicacionBaja crear(Comprobante c, int correlativoDelDia, String motivo, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (!c.estado().esFinalAceptado())
            throw new DomainException("BAJA_INVALIDA", (c.tipo() == TipoDocumento.BOLETA ? "2663" : "2105/2398") + " - Solo se puede dar de baja un comprobante aceptado por SUNAT; "
                    + c.serie() + "-" + c.numero() + " está " + c.estado());
        if (ChronoUnit.DAYS.between(c.fechaEmision(), hoy) > PLAZO_DIAS)
            throw new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja " + c.serie() + "-" + c.numero() + " venció: se emitió el " + c.fechaEmision()
                    + " y la comunicación debe presentarse dentro de los " + PLAZO_DIAS + " días calendario");
        String m = motivo == null ? "" : motivo.strip();
        if (m.length() < 3 || m.length() > 100 || m.chars().anyMatch(Character::isISOControl))
            throw new DomainException("BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
        if (correlativoDelDia < 1) throw new DomainException("BAJA_INVALIDA", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), c.serie(), c.numero(), c.fechaEmision(), m, Condicion.BAJA, EstadoBaja.GENERADA);
    }

    /** Solo para persistencia. */
    public static ComunicacionBaja rehidratar(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante,
                                              String serie, long numero, LocalDate fechaReferencia, String motivo, Condicion condicion, EstadoBaja estado, String ticket,
                                              String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError) {
        ComunicacionBaja b = new ComunicacionBaja(id, tenantId, fechaGeneracion, correlativo, comprobanteId, tipoComprobante, serie, numero, fechaReferencia, motivo, condicion, estado);
        b.ticket = ticket; b.xmlKey = xmlKey; b.cdrKey = cdrKey; b.cdr = cdr; b.intentos = intentos; b.ultimoError = ultimoError;
        return b;
    }

    /** Una boleta se anula en el resumen diario (SummaryDocuments), no en una comunicación de baja (VoidedDocuments). */
    public boolean resumenDiario() { return tipoComprobante == TipoDocumento.BOLETA; }

    /** Si informa una boleta (alta) en vez de anularla. */
    public boolean alta() { return condicion == Condicion.ALTA; }

    /** {@code RA-20260918-1} (o {@code RC-…} para una boleta): cbc:ID y parte del nombre de archivo (reglas 2220, 2346). */
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
