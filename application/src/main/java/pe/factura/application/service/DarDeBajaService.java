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
import java.util.ArrayList;
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

    /**
     * Ver {@link InformarEnResumenUseCase}: el resumen diario que informa la boleta con su línea en estado 1. Las demás boletas (y notas de boleta) de su misma fecha de emisión que
     * también esperan ir en el resumen diario viajan con ella en <b>un</b> solo RC, hasta 500 líneas (#301): un cliente cuyo envío individual se cayó varios días no manda
     * un resumen por boleta. Una boleta cuyo resumen de varias líneas ya fue rechazado va sola, para aislar la línea que lo rompió.
     */
    @Override
    public Comprobante informar(UUID tenantId, UUID comprobanteId) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        LocalDate hoy = LocalDate.now(clock);
        tenant.exigirListoParaEmitir(hoy);
        tenant.exigirCredencialesSol();
        ComunicacionBaja cabeza = uow.ejecutar(() -> {
            Comprobante c = comprobantes.bloquear(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            List<ComunicacionBaja> previas = bajas.deComprobante(tenantId, c.id());
            if (previas.stream().anyMatch(ComunicacionBaja::pendiente))
                throw new DomainException("RESUMEN_INVALIDO", "Ya hay un resumen diario en curso para " + c.serie() + "-" + c.numero());
            List<Comprobante> lote = new ArrayList<>(List.of(c));
            if (previas.stream().noneMatch(ComunicacionBaja::aislaSuComprobante) && c.soloPorResumen(hoy))
                comprobantes.bloquearPorInformarDeLaFecha(tenantId, c.fechaEmision(), c.id(), ComunicacionBaja.MAX_LINEAS_RESUMEN - 1).stream().filter(o -> o.soloPorResumen(hoy)).forEach(lote::add);
            int correlativo = bajas.siguienteCorrelativo(tenantId, hoy);
            List<ComunicacionBaja> filas = armarResumen(tenant, lote, correlativo);
            List<Comprobante> incluidos = lote.subList(0, filas.size());
            for (int i = 0; i < incluidos.size(); i++) {
                Comprobante o = incluidos.get(i);
                o.informarEnResumen(filas.get(i).identificador(), filas.get(i).fueraDePlazo());
                comprobantes.guardar(o);
                bajas.guardar(filas.get(i));
            }
            return filas.get(0);
        });
        continuar(tenant, cabeza);
        return comprobantes.buscar(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }

    /**
     * Las filas del resumen del {@code lote}, con su XML ya firmado, validado y guardado. Si el resumen de varias líneas no se puede producir (la plantilla o el esquema fallan con
     * alguna de las líneas) se arma solo con la primera, la que se pidió informar: el defecto de una hermana no puede bloquear a las demás boletas de la fecha, que sin esto
     * volverían a agruparla en cada intento y fallarían todas para siempre. La hermana defectuosa sigue esperando, y falla sola cuando le toque.
     */
    private List<ComunicacionBaja> armarResumen(Tenant tenant, List<Comprobante> lote, int correlativo) {
        try {
            List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(lote, correlativo, clock);
            firmarYGuardar(tenant, lote, filas);
            return filas;
        } catch (RuntimeException e) {
            if (lote.size() == 1) throw e;
            List<ComunicacionBaja> sola = ComunicacionBaja.altasEnResumen(lote.subList(0, 1), correlativo, clock);
            firmarYGuardar(tenant, lote.subList(0, 1), sola);
            return sola;
        }
    }

    /** Una boleta va en un resumen diario (RC) y los demás en una comunicación de baja (RA): mismo ciclo, otro XML (#20). Lo deja firmado y guardado. */
    private void firmarYGuardar(Tenant tenant, Comprobante c, ComunicacionBaja b) {
        String xml = ubl.generarBaja(b, c, tenant);
        guardarFirmado(tenant, xml, List.of(b));
    }

    /** El resumen diario de varias líneas (#301): un solo XML, firmado y guardado una vez, con la misma clave en todas las filas. */
    private void firmarYGuardar(Tenant tenant, List<Comprobante> lote, List<ComunicacionBaja> filas) {
        guardarFirmado(tenant, ubl.generarResumen(filas, lote, tenant), filas);
    }

    private void guardarFirmado(Tenant tenant, String xml, List<ComunicacionBaja> filas) {
        ComunicacionBaja b = filas.get(0);
        FirmaResultado firma = signer.firmar(xml, tenant.certificado());
        xsd.validarBaja(firma.xmlFirmado(), b);
        String key = tenant.id() + "/" + b.fechaGeneracion().getYear() + "/" + String.format("%02d", b.fechaGeneracion().getMonthValue()) + "/" + b.nombreArchivo(tenant.ruc()) + ".xml";
        storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
        filas.forEach(f -> f.firmar(key));
    }

    @Override
    public ComunicacionBaja continuar(UUID tenantId, UUID bajaId) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        return continuar(tenant, obtener(tenantId, bajaId));
    }

    /**
     * Las filas de un resumen diario de varias líneas comparten el ciclo (#301): el envío, el ticket y el CDR son uno solo. Se avanza la primera y las demás copian su estado;
     * un resumen de una línea y una comunicación de baja se tratan tal cual llegan.
     */
    private ComunicacionBaja continuar(Tenant tenant, ComunicacionBaja dada) {
        List<ComunicacionBaja> filas = dada.deVariasLineas()
                ? bajas.delResumen(dada.tenantId(), dada.fechaGeneracion(), dada.correlativo()) : List.of(dada);
        ComunicacionBaja b = filas.get(0);
        if (!b.pendiente()) return filas.stream().filter(f -> f.id().equals(dada.id())).findFirst().orElse(dada);
        avanzar(tenant, b);
        filas.stream().skip(1).forEach(f -> f.copiarCicloDe(b));
        resolver(filas, b);
        return filas.stream().filter(f -> f.id().equals(dada.id())).findFirst().orElse(dada);
    }

    /** El envío del resumen o de la baja y la consulta de su ticket; deja el resultado en {@code b}. */
    private void avanzar(Tenant tenant, ComunicacionBaja b) {
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
    }

    /**
     * Estado de la baja (o de todas las filas del resumen), el resultado en los comprobantes y la reprogramación, en una sola transacción (mismo criterio que
     * EnviarDocumentoService). El outbox lleva una sola fila por resumen —la de la primera línea—: el ticket es uno.
     */
    private void resolver(List<ComunicacionBaja> filas, ComunicacionBaja b) {
        uow.ejecutar(() -> {
            filas.forEach(bajas::guardar);
            if (b.alta() && !b.pendiente()) {
                // Un resumen de alta resuelto resuelve la boleta: su CDR la acepta o la rechaza, como un envío más.
                // #362 B3, decisión sin cambio de lógica: si SUNAT rechaza el alta de una boleta que ya tenía (2282 «ya informada»; el 2987 es el rechazo del resumen por una línea con error), esta línea la deja RECHAZADA
                // aunque SUNAT ya la tenga. En e-beta no se reproduce (el alta sobre una boleta aceptada por sendBill se aceptó con código 0, y un sendBill repetido con otro contenido tampoco da 1033) y
                // producción no se pudo verificar; endurecerlo (mapear 2282 en el alta a «ya informada», no a RECHAZADO) sin evidencia sería adivinar. Se revisa con el primer caso real.
                for (ComunicacionBaja fila : filas) {
                    Comprobante c = comprobantes.bloquear(fila.tenantId(), fila.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
                    if (c.estado() != EstadoDocumento.ENVIADO) continue;
                    // Dos procesos pueden resolver el mismo resumen a la vez (el worker y un GET de cualquiera de sus filas): si el comprobante ya fue reinformado en un resumen más
                    // nuevo, el resultado de este, viejo, no le toca; lo resolverá el nuevo. Sin esto, un rechazo repetido lo devolvía al envío con su resumen nuevo en curso.
                    if (!esElUltimoResumenDe(fila)) continue;
                    if (b.aislaSuComprobante()) devolverAlEnvio(c, b, fila.linea());
                    else {
                        if (b.cdrKey() != null) c.aplicarCdr(b.cdr(), b.cdrKey()); else c.rechazarPorFault(b.cdr().codigo(), b.cdr().descripcion());
                        comprobantes.guardar(c);
                    }
                }
            } else if (b.estado() == EstadoBaja.ACEPTADA) {
                // Con lock de fila: el GET del usuario y el outbox pueden recoger el mismo CDR a la vez, y el segundo se encuentra el comprobante ya ANULADO.
                Comprobante c = comprobantes.bloquear(b.tenantId(), b.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
                if (c.estado() != EstadoDocumento.ANULADO) {
                    c.anular();
                    comprobantes.guardar(c);
                }
            }
            if (b.estado() == EstadoBaja.ENVIADA) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), clock.instant().plus(REINTENTO_CONSULTA));
            if (b.estado() == EstadoBaja.ERROR_ENVIO) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), Backoff.siguiente(b.intentos(), clock.instant()));
        });
    }

    /**
     * #301: SUNAT rechaza un resumen entero si una línea falla (2987) y el CDR no dice cuál. Marcar RECHAZADAS a todas castigaría a boletas buenas por una mala: cada una vuelve a
     * su envío y se informa sola ({@link #informar} no agrupa una boleta cuyo resumen de varias líneas ya fue rechazado), así solo la defectuosa termina RECHAZADA.
     */
    private void devolverAlEnvio(Comprobante c, ComunicacionBaja resumen, int linea) {
        c.marcarErrorEnvio("SUNAT rechazó el resumen diario " + resumen.identificador() + " de " + resumen.lineas() + " líneas (" + resumen.cdr().codigo() + " - " + resumen.cdr().descripcion()
                + "): se vuelve a informar sola para aislar la línea que lo causó");
        comprobantes.guardar(c);
        outbox.completarPorAgregado(c.id(), EnviarDocumentoService.ACCION_ENVIAR);
        // Un milisegundo por línea: el outbox toma lo vencido por orden de programación, y así cada una se informa en el orden del resumen (una nota detrás de la boleta que modifica).
        outbox.programar(c.tenantId(), EnviarDocumentoService.ACCION_ENVIAR, c.id(), clock.instant().plusMillis(linea));
    }

    /** Si esta fila es el resumen de alta más reciente de su comprobante: ninguno posterior, ni en curso ni resuelto. */
    private boolean esElUltimoResumenDe(ComunicacionBaja fila) {
        return bajas.deComprobante(fila.tenantId(), fila.comprobanteId()).stream().filter(ComunicacionBaja::alta).noneMatch(o -> o.esPosteriorA(fila));
    }

    @Override public ComunicacionBaja obtener(UUID tenantId, UUID bajaId) {
        return bajas.buscar(tenantId, bajaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comunicación de baja no encontrada"));
    }
    @Override public List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId) { return bajas.deComprobante(tenantId, comprobanteId); }
}
