package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.InformarEnResumenUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Comunicación de baja (#31): un VoidedDocuments por comprobante (o, para una boleta, un resumen diario con su línea en estado 3, #20), enviado con {@code sendSummary}. SUNAT responde un ticket;
 * el CDR se recoge con {@code getStatus} — en el acto si ya está procesado, o desde el outbox (acción {@link #ACCION_BAJA})
 * mientras SUNAT devuelva 98. Al aceptarse, el comprobante pasa a {@code ANULADO} en la misma transacción.
 */
@RequiredArgsConstructor
public class DarDeBajaService implements DarDeBajaUseCase, InformarEnResumenUseCase {
    public static final String ACCION_BAJA = "BAJA";
    /** SUNAT suele procesar un RA en segundos: se reconsulta pronto, sin el backoff largo de los envíos fallidos. */
    public static final Duration REINTENTO_CONSULTA = Duration.ofSeconds(30);
    /** Tope del intervalo entre consultas del ticket: un RA que SUNAT tarda horas en resolver se reconsulta cada 5 min, no cada 30 s. */
    public static final Duration CONSULTA_MAXIMA = Duration.ofMinutes(5);

    /** Intervalo creciente entre consultas del ticket (30 s × intentos, hasta 5 min). */
    public static Duration consultaTicket(int intentos) {
        Duration d = REINTENTO_CONSULTA.multipliedBy(Math.max(1, intentos));
        return d.compareTo(CONSULTA_MAXIMA) > 0 ? CONSULTA_MAXIMA : d;
    }

    private final BajaRepository bajas;
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final SunatBillingGateway gateway;
    private final CdrParser cdrParser;
    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override
    public ComunicacionBaja solicitar(UUID tenantId, UUID comprobanteId, String motivo) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));
        tenant.exigirCredencialesSol();
        ComunicacionBaja baja = uow.ejecutar(() -> {
            Comprobante c = comprobantes.bloquear(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            if (bajas.deComprobante(tenantId, c.id()).stream().anyMatch(ComunicacionBaja::pendiente))
                throw new DomainException("BAJA_INVALIDA", "Ya hay una comunicación de baja en curso para " + c.serie() + "-" + c.numero());
            ComunicacionBaja b = ComunicacionBaja.crear(c, bajas.siguienteCorrelativo(tenantId, LocalDate.now(clock)), motivo, clock);
            firmarYGuardar(tenant, c, b);
            bajas.guardar(b);
            return b;
        });
        return continuar(tenant, baja);
    }

    /** Ver {@link InformarEnResumenUseCase}: el mismo resumen que la baja de una boleta, con su línea en estado 1. */
    @Override
    public Comprobante informar(UUID tenantId, UUID comprobanteId) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));
        tenant.exigirCredencialesSol();
        ComunicacionBaja alta = uow.ejecutar(() -> {
            Comprobante c = comprobantes.bloquear(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            if (bajas.deComprobante(tenantId, c.id()).stream().anyMatch(ComunicacionBaja::pendiente))
                throw new DomainException("RESUMEN_INVALIDO", "Ya hay un resumen diario en curso para " + c.serie() + "-" + c.numero());
            ComunicacionBaja b = ComunicacionBaja.altaEnResumen(c, bajas.siguienteCorrelativo(tenantId, LocalDate.now(clock)), clock);
            firmarYGuardar(tenant, c, b);
            c.informarEnResumen(b.identificador(), b.fueraDePlazo());
            comprobantes.guardar(c);
            bajas.guardar(b);
            return b;
        });
        continuar(tenant, alta);
        return comprobantes.buscar(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }

    /** Una boleta va en un resumen diario (RC) y los demás en una comunicación de baja (RA): mismo ciclo, otro XML (#20). Lo deja firmado y guardado. */
    private void firmarYGuardar(Tenant tenant, Comprobante c, ComunicacionBaja b) {
        String xml = ubl.generarBaja(b, c, tenant);
        FirmaResultado firma = signer.firmar(xml, tenant.certificado());
        xsd.validarBaja(firma.xmlFirmado(), b);
        String key = tenant.id() + "/" + b.fechaGeneracion().getYear() + "/" + String.format("%02d", b.fechaGeneracion().getMonthValue()) + "/" + b.nombreArchivo(tenant.ruc()) + ".xml";
        storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
        b.firmar(key);
    }

    @Override
    public ComunicacionBaja continuar(UUID tenantId, UUID bajaId) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        return continuar(tenant, obtener(tenantId, bajaId));
    }

    private ComunicacionBaja continuar(Tenant tenant, ComunicacionBaja b) {
        if (!b.pendiente()) return b;
        if (puedeHaberCaducado(b)) {
            Renovacion r;
            try {
                r = renovarSiCaduco(tenant, b);
            } catch (RuntimeException e) {
                return dejarParaReintento(b, e);
            }
            if (!r.seguir()) return r.baja();
            b = r.baja();
        }
        return enviarYConsultar(tenant, b);
    }

    /**
     * #338: regenerar el RC caducado falló (firma, XSD, storage) y su transacción se deshizo. El viejo no se envía (lleva el 05), pero tampoco puede quedar sin
     * fila de outbox: uno GENERADA no la tiene, y quedaba «en curso» para siempre, sin nadie que lo reintentara (el mismo caso que #151 en el envío). Queda en
     * ERROR_ENVIO con el motivo y vuelve a la cola con backoff; el reintento lo regenera. Releído bajo el lock, por si otro proceso ya lo resolvió.
     */
    private ComunicacionBaja dejarParaReintento(ComunicacionBaja leida, RuntimeException e) {
        String motivo = "INFRA - No se pudo generar el resumen que reemplaza a " + leida.identificador() + ": " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return uow.ejecutar(() -> {
            comprobantes.bloquear(leida.tenantId(), leida.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            ComunicacionBaja b = obtener(leida.tenantId(), leida.id());
            if (b.estado() == EstadoBaja.DESCARTADA) return reemplazoDe(b);
            if (b.estado() != EstadoBaja.GENERADA && b.estado() != EstadoBaja.ERROR_ENVIO) return b;
            b.marcarErrorEnvio(motivo);
            bajas.guardar(b);
            outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), Backoff.siguiente(b.intentos(), clock.instant()));
            return b;
        });
    }

    /** Lo que sigue tras mirar si el resumen caducó: con qué baja, y si hay que enviarla/consultarla o devolverla tal cual. */
    private record Renovacion(ComunicacionBaja baja, boolean seguir) {}

    /**
     * El filtro barato de {@link ComunicacionBaja#formatoCaducado} (sin leer el comprobante ni bloquear nada): un RC sin ticket generado antes del cambio de
     * formato de las gratuitas, intentado desde ese día. «Hoy» es la fecha de Lima, la del {@code clock}.
     */
    private boolean puedeHaberCaducado(ComunicacionBaja b) {
        return b.resumenDiario() && b.estado() != EstadoBaja.ENVIADA && !b.gratuitasPorAfectacion()
                && !LocalDate.now(clock).isBefore(ComunicacionBaja.GRATUITAS_POR_AFECTACION_DESDE);
    }

    /**
     * #338: un RC con gratuitas generado hasta el 2026-12-31 que no llegó a SUNAT (caída y reintento por ERROR_ENVIO) lleva el código 05, que SUNAT desactivó el
     * 2027-01-01: reenviarlo es un rechazo seguro, y su fecha no se puede cambiar (la del ID es la de generación, 2346). Se descarta y se genera otro con la
     * fecha, el correlativo y el formato de hoy; la fila del outbox pasa a apuntar al nuevo (el worker que la tiene tomada la reprograma o la completa por su id).
     * <p>
     * Una baja que ya no está en plazo (admitida el 7.º día, regenerada el 8.º) no se regenera: SUNAT la rechazaría con 2957, que mide al recibirla. Queda
     * RECHAZADA con ese código sin enviarse, y sale de la cola. Un alta no vence: regenerada pasado el 7.º día sale fuera de plazo (4443, #331).
     * <p>
     * Todo con el comprobante bloqueado y la baja releída bajo ese lock: el worker y un reintento manual pueden llegar a la vez con la misma copia; el segundo
     * la encuentra descartada y devuelve, sin enviarla, la que la reemplazó. Nunca quedan dos RC vivos para el mismo comprobante.
     */
    private Renovacion renovarSiCaduco(Tenant tenant, ComunicacionBaja leida) {
        LocalDate hoy = LocalDate.now(clock);
        return uow.ejecutar(() -> {
            Comprobante c = comprobantes.bloquear(leida.tenantId(), leida.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            ComunicacionBaja b = obtener(leida.tenantId(), leida.id());
            if (b.estado() == EstadoBaja.DESCARTADA) return new Renovacion(reemplazoDe(b), false);   // otro ya la reemplazó
            if (!b.formatoCaducado(c, hoy)) return new Renovacion(b, b.pendiente());
            if (b.plazoVencido(hoy)) {
                b.rechazarPorPlazo(hoy);
                bajas.guardar(b);
                outbox.completarPorAgregado(b.id(), ACCION_BAJA);
                return new Renovacion(b, false);
            }
            ComunicacionBaja nueva = b.regenerar(bajas.siguienteCorrelativo(b.tenantId(), hoy), clock);
            firmarYGuardar(tenant, c, nueva);
            b.reemplazarPor(nueva);
            bajas.guardar(b);
            bajas.guardar(nueva);
            outbox.reasignar(b.id(), ACCION_BAJA, nueva.id());
            return new Renovacion(nueva, true);
        });
    }

    private ComunicacionBaja enviarYConsultar(Tenant tenant, ComunicacionBaja b) {
        try {
            if (b.estado() != EstadoBaja.ENVIADA) {
                String ticket = gateway.sendSummary(tenant, b.nombreArchivo(tenant.ruc()), storage.leer(b.xmlKey()));
                b.marcarEnviada(ticket);
            }
            SunatBillingGateway.EstadoTicket estado = gateway.getStatus(tenant, b.ticket());
            if (estado.procesado()) {
                Cdr cdr = cdrParser.parsear(estado.cdrZip());
                String cdrKey = b.xmlKey().substring(0, b.xmlKey().lastIndexOf('/') + 1) + "R-" + b.nombreArchivo(tenant.ruc()) + ".zip";
                storage.guardar(cdrKey, estado.cdrZip());
                b.aplicarCdr(cdr, cdrKey);
            } else {
                b.registrarConsultaPendiente("98 - SUNAT sigue procesando el ticket " + b.ticket());
            }
        } catch (SunatTransientException e) {
            if (b.estado() == EstadoBaja.ENVIADA) b.registrarConsultaPendiente(e.codigo() + " - " + e.getMessage());
            else b.marcarErrorEnvio(e.codigo() + " - " + e.getMessage());
        } catch (SunatRechazoException e) {
            b.rechazarPorFault(e.codigo(), e.descripcion());
        } catch (IllegalStateException e) {   // storage, ZIP o CDR ilegible: infraestructura, reintentable
            if (b.estado() == EstadoBaja.ENVIADA) b.registrarConsultaPendiente("INFRA - " + e.getMessage()); else b.marcarErrorEnvio("INFRA - " + e.getMessage());
        } catch (RuntimeException e) {
            // Cualquier otra falla (red hacia S3, SDK, NPE de un parser): sin este catch la excepción salía antes de la transacción de
            // abajo y la baja quedaba GENERADA sin fila de outbox, es decir «en curso» para siempre y sin nadie que la reintentara.
            String motivo = "INFRA - " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            if (b.estado() == EstadoBaja.ENVIADA) b.registrarConsultaPendiente(motivo); else b.marcarErrorEnvio(motivo);
        }
        // Estado de la baja, anulación del comprobante y reprogramación en una sola transacción (mismo criterio que EnviarDocumentoService).
        return uow.ejecutar(() -> {
            // Con el comprobante bloqueado: el GET del usuario y el outbox pueden recoger el mismo CDR a la vez, y el segundo se encuentra el comprobante ya
            // resuelto. Es también el lock con que renovarSiCaduco descarta un RC (#338): releída bajo él, una baja que otro proceso descartó y reemplazó
            // mientras este envío estaba en vuelo no se pisa ni vuelve a la cola (quedarían dos RC vivos); sigue el reemplazo.
            Comprobante c = comprobantes.bloquear(b.tenantId(), b.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            ComunicacionBaja actual = obtener(b.tenantId(), b.id());
            if (actual.estado() == EstadoBaja.DESCARTADA) {
                if (b.ticket() != null) {
                    actual.registrarEnvioTardio(b.ticket());
                    bajas.guardar(actual);
                }
                return reemplazoDe(actual);
            }
            bajas.guardar(b);
            if (b.alta() && !b.pendiente()) {
                // Un resumen de alta resuelto resuelve la boleta: su CDR la acepta o la rechaza, como un envío más.
                if (c.estado() == EstadoDocumento.ENVIADO) {
                    if (b.cdrKey() != null) c.aplicarCdr(b.cdr(), b.cdrKey()); else c.rechazarPorFault(b.cdr().codigo(), b.cdr().descripcion());
                    comprobantes.guardar(c);
                }
            } else if (b.estado() == EstadoBaja.ACEPTADA && c.estado() != EstadoDocumento.ANULADO) {
                c.anular();
                comprobantes.guardar(c);
            }
            if (b.estado() == EstadoBaja.ENVIADA) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), clock.instant().plus(REINTENTO_CONSULTA));
            if (b.estado() == EstadoBaja.ERROR_ENVIO) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), Backoff.siguiente(b.intentos(), clock.instant()));
            return b;
        });
    }

    /** El que reemplazó a un resumen descartado: el último del comprobante (o uno posterior, si el reemplazo ya se resolvió y se pidió otro). */
    private ComunicacionBaja reemplazoDe(ComunicacionBaja descartada) {
        return bajas.deComprobante(descartada.tenantId(), descartada.comprobanteId()).stream()
                .max(Comparator.comparing(ComunicacionBaja::fechaGeneracion).thenComparingInt(ComunicacionBaja::correlativo)).orElse(descartada);
    }

    @Override public ComunicacionBaja obtener(UUID tenantId, UUID bajaId) {
        return bajas.buscar(tenantId, bajaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comunicación de baja no encontrada"));
    }
    @Override public List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId) { return bajas.deComprobante(tenantId, comprobanteId); }
}
