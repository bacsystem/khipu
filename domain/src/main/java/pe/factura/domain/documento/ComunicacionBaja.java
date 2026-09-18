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
 * Las boletas se dan de baja en el resumen diario (#20), no aquí.
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
    private EstadoBaja estado;
    private String ticket;
    private String xmlKey;
    private String cdrKey;
    private Cdr cdr;
    private int intentos;
    private String ultimoError;

    private ComunicacionBaja(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante,
                             String serie, long numero, LocalDate fechaReferencia, String motivo, EstadoBaja estado) {
        this.id = id; this.tenantId = tenantId; this.fechaGeneracion = fechaGeneracion; this.correlativo = correlativo;
        this.comprobanteId = comprobanteId; this.tipoComprobante = tipoComprobante; this.serie = serie; this.numero = numero;
        this.fechaReferencia = fechaReferencia; this.motivo = motivo; this.estado = estado;
    }

    /**
     * Solo un comprobante aceptado por SUNAT (2398/2323), de tipo 01/07/08 (2308) y emitido hace como máximo 7 días
     * (2957) puede darse de baja; el motivo va en la línea (3–100 caracteres: 2315, 4203).
     */
    public static ComunicacionBaja crear(Comprobante c, int correlativoDelDia, String motivo, Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        if (!c.estado().esFinalAceptado())
            throw new DomainException("BAJA_INVALIDA", "2398 - Solo se puede dar de baja un comprobante aceptado por SUNAT; " + c.serie() + "-" + c.numero() + " está " + c.estado());
        if (c.tipo() == TipoDocumento.BOLETA)
            throw new DomainException("BAJA_INVALIDA", "2308 - Las boletas se dan de baja en el resumen diario, no con una comunicación de baja");
        if (ChronoUnit.DAYS.between(c.fechaEmision(), hoy) > PLAZO_DIAS)
            throw new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja " + c.serie() + "-" + c.numero() + " venció: se emitió el " + c.fechaEmision()
                    + " y la comunicación debe presentarse dentro de los " + PLAZO_DIAS + " días calendario");
        String m = motivo == null ? "" : motivo.strip();
        if (m.length() < 3 || m.length() > 100 || m.chars().anyMatch(Character::isISOControl))
            throw new DomainException("BAJA_INVALIDA", "2315 - El motivo de la baja debe tener de 3 a 100 caracteres, sin saltos de línea");
        if (correlativoDelDia < 1) throw new DomainException("BAJA_INVALIDA", "El correlativo del día debe ser mayor que cero");
        return new ComunicacionBaja(UUID.randomUUID(), c.tenantId(), hoy, correlativoDelDia, c.id(), c.tipo(), c.serie(), c.numero(), c.fechaEmision(), m, EstadoBaja.GENERADA);
    }

    /** Solo para persistencia. */
    public static ComunicacionBaja rehidratar(UUID id, UUID tenantId, LocalDate fechaGeneracion, int correlativo, UUID comprobanteId, TipoDocumento tipoComprobante,
                                              String serie, long numero, LocalDate fechaReferencia, String motivo, EstadoBaja estado, String ticket,
                                              String xmlKey, String cdrKey, Cdr cdr, int intentos, String ultimoError) {
        ComunicacionBaja b = new ComunicacionBaja(id, tenantId, fechaGeneracion, correlativo, comprobanteId, tipoComprobante, serie, numero, fechaReferencia, motivo, estado);
        b.ticket = ticket; b.xmlKey = xmlKey; b.cdrKey = cdrKey; b.cdr = cdr; b.intentos = intentos; b.ultimoError = ultimoError;
        return b;
    }

    /** {@code RA-20260918-1}: cbc:ID y parte del nombre de archivo (reglas 2220, 2346). */
    public String identificador() { return "RA-" + fechaGeneracion.format(FECHA_ID) + "-" + correlativo; }
    /** {@code RUC-RA-20260918-1}, sin extensión. */
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
