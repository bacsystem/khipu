package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.EstadoGuia;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Envío de la guía de remisión por la API REST de SUNAT y consulta de su ticket (#39). El resultado se guarda junto con lo que queda programado en el outbox, en
 * la misma transacción:
 * <ul>
 *   <li>{@value #ACCION_ENVIAR}: reintentar el envío con espera creciente ({@link Backoff}) mientras SUNAT no responda. Si SUNAT rechaza las credenciales o el
 *       archivo, no se reintenta: reintentar no lo cambia, y golpear el token con una clave SOL equivocada puede bloquearla. La guía queda en ERROR_ENVIO con
 *       {@code reintento_automatico: false} hasta que se reenvíe a mano.</li>
 *   <li>{@value #ACCION_TICKET}: consultar el ticket mientras SUNAT diga 98, cada vez más espaciado ({@link DarDeBajaService#consultaTicket}, hasta 5 min) y con
 *       un tope de {@value #TOPE_DE_CONSULTAS} consultas (~24 h). Pasado el tope la guía sigue esperando el ticket, sin reintento automático: se reconsulta a mano.</li>
 * </ul>
 * Una respuesta que llega cuando otra transacción ya resolvió la guía no la pisa ({@code ESTADO_CONFLICTO} del repositorio): se devuelve la guía como quedó.
 */
@RequiredArgsConstructor
public class EnviarGuiaRemisionService implements EnviarGuiaRemisionUseCase {
    public static final String ACCION_ENVIAR = "GRE_ENVIAR";
    public static final String ACCION_TICKET = "GRE_TICKET";
    public static final int TOPE_DE_CONSULTAS = 300;
    /** Los errores de configuración de la empresa: no los arregla reintentar. */
    private static final Set<String> SIN_CONFIGURAR = Set.of("CREDENCIALES_API_SUNAT_NO_CARGADAS", "CREDENCIALES_SOL_NO_CARGADAS", "GRE_NO_DISPONIBLE");

    private final GuiaRemisionRepository guias;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final SunatGreGateway gre;
    private final CdrParser cdrParser;
    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final Clock clock;
    private final RechazoDeCredencialesGreRepository rechazos;

    /** Un envío reservado (ENVIANDO) sin respuesta por más de esto se puede retomar a mano, con {@code forzar} (H2). */
    public static final Duration ENVIO_SIN_RESPUESTA = Duration.ofMinutes(10);

    /** Cuándo volver a consultar un ticket tras {@code consultas} consultas: el mismo ritmo que el de una baja. */
    public static Duration intervaloDeConsulta(int consultas) { return DarDeBajaService.consultaTicket(consultas); }

    @Override public GuiaRemision enviar(UUID tenantId, UUID guiaId) { return enviar(tenantId, guiaId, false); }

    /**
     * Antes de llamar a SUNAT, en una transacción corta, reserva el envío (H2): pasa la guía a ENVIANDO solo si sigue FIRMADO o en ERROR_ENVIO. Si otro proceso
     * ya la reservó, no se llama a SUNAT: el mismo número no se manda dos veces. Una guía que quedó ENVIANDO (el proceso murió a mitad) no se sabe si llegó:
     * {@code forzar} la retoma, pasado {@link #ENVIO_SIN_RESPUESTA}, cuando alguien revisó en SOL que no llegó.
     */
    @Override public GuiaRemision enviar(UUID tenantId, UUID guiaId, boolean forzar) {
        GuiaRemision g = guia(tenantId, guiaId);
        if (g.estado() == EstadoGuia.ENVIANDO && !forzar)
            throw new DomainException("ENVIO_EN_CURSO", "La guía " + g.nombreArchivo() + " tiene un envío en curso sin respuesta. Si pasaron más de " + ENVIO_SIN_RESPUESTA.toMinutes()
                    + " min, el proceso pudo caerse después de que SUNAT la recibiera: revise en SOL si la guía llegó y, solo si no, reenvíela con forzar=true");
        if (!g.estado().esEnviable() && g.estado() != EstadoGuia.ENVIANDO)
            throw new DomainException("ESTADO_NO_ENVIABLE", "La guía " + g.nombreArchivo() + " está " + g.estado() + ": solo se envía una FIRMADO o en ERROR_ENVIO");
        Tenant t = tenant(tenantId);
        // H1 y H3: sin llamar a SUNAT ni pedir token, y antes de reservar.
        if (!gre.disponible(t.entorno())) throw EmitirGuiaRemisionService.noDisponible(t);
        CredencialesDeGre.exigirNoRechazadas(rechazos, t, clock);
        boolean reservada = uow.ejecutar(() -> guias.reservarEnvio(tenantId, guiaId, clock.instant(), forzar, clock.instant().minus(ENVIO_SIN_RESPUESTA)));
        if (!reservada)
            throw new DomainException(forzar ? "ENVIO_EN_CURSO" : "ESTADO_NO_ENVIABLE", "La guía " + g.nombreArchivo() + " ya la está enviando otro proceso o cambió de estado"
                    + (forzar ? " (forzar solo retoma un envío sin respuesta de hace más de " + ENVIO_SIN_RESPUESTA.toMinutes() + " min)" : "") + ": no se manda dos veces");
        g.iniciarEnvio();
        String credencialesRechazadas = null;
        try {
            SunatGreGateway.Recepcion r = gre.enviar(t, g.nombreArchivo(), storage.leer(g.xmlKey()));
            g.marcarEnviada(r.ticket(), r.fechaRecepcion());
        } catch (DomainException e) {
            if (!SIN_CONFIGURAR.contains(e.codigo())) {
                // Un error de khipu antes de llamar a SUNAT: no se esconde, pero la guía no queda reservada para siempre (no salió nada).
                g.marcarErrorEnvio(e.codigo() + " - " + e.getMessage(), false);
                guardarYProgramar(g, () -> outbox.completarPorAgregado(g.id(), ACCION_ENVIAR));
                throw e;
            }
            g.marcarErrorEnvio(e.codigo() + " - " + e.getMessage(), false);
        } catch (SunatCredencialesException e) {
            credencialesRechazadas = e.codigo() + " - " + e.getMessage();
            g.marcarErrorEnvio("SUNAT no aceptó las credenciales API SUNAT o la clave SOL de la empresa (" + credencialesRechazadas
                    + "). Corríjalas y reenvíe la guía: khipu no reintenta solo para no bloquear la clave SOL", false);
        } catch (SunatRechazoException e) {
            g.marcarErrorEnvio("SUNAT no recibió la guía (" + e.codigo() + " - " + e.descripcion() + "): no la registró, y reintentar sin cambios da el mismo error", false);
        } catch (SunatTransientException e) {
            g.marcarErrorEnvio(e.codigo() + " - " + e.getMessage(), true);
        } catch (IllegalStateException e) {   // el XML no se pudo leer del storage: infraestructura, reintentable
            g.marcarErrorEnvio("INFRA - " + e.getMessage(), true);
        }
        String rechazo = credencialesRechazadas;
        GuiaRemision guardada = guardarYProgramar(g, () -> {
            registrarCredenciales(t, rechazo, g.estado().esperaTicket());
            if (g.estado() == EstadoGuia.ERROR_ENVIO && g.reintentoAutomatico()) outbox.programar(tenantId, ACCION_ENVIAR, g.id(), Backoff.siguiente(g.intentos(), clock.instant()));
            else outbox.completarPorAgregado(g.id(), ACCION_ENVIAR);
            if (g.estado().esperaTicket()) outbox.programar(tenantId, ACCION_TICKET, g.id(), clock.instant().plus(intervaloDeConsulta(1)));
        });
        // SUNAT suele procesar en segundos: una consulta en el acto evita que quien emite vea «pendiente» por una guía que ya se aceptó.
        return guardada.estado().esperaTicket() && guardada == g ? consultar(t, g, false) : guardada;
    }

    @Override public GuiaRemision consultarTicket(UUID tenantId, UUID guiaId) {
        GuiaRemision g = guia(tenantId, guiaId);
        if (!g.estado().esperaTicket())
            throw new DomainException("TICKET_NO_PENDIENTE", "La guía " + g.nombreArchivo() + " está " + g.estado() + ": no espera la respuesta de un ticket");
        return consultar(tenant(tenantId), g, true);
    }

    /** {@code manual}: lo pidió alguien; si khipu había dejado de consultar y SUNAT sigue en 98, vuelve a consultar solo. */
    private GuiaRemision consultar(Tenant t, GuiaRemision g, boolean manual) {
        boolean detenida = !g.reintentoAutomatico();
        CredencialesDeGre.exigirNoRechazadas(rechazos, t, clock);   // H3: sin pedir token con las credenciales que SUNAT rechazó
        String credencialesRechazadas = null;
        boolean respondio = false;
        try {
            SunatGreGateway.EstadoDelTicket e = gre.consultar(t, g.ticket());
            respondio = true;
            if (e.enProceso()) {
                g.ticketEnProceso();
                if (manual && detenida) g.reanudarConsultas();
                else if (g.consultasTicket() >= TOPE_DE_CONSULTAS) g.dejarDeConsultar(sinRespuestaTrasElTope());
            } else if (e.conCdr() && GuiaRemision.yaEnSunat(e.numError())) {
                // H7: SUNAT dice que el número ya está registrado: no es un rechazo que se corrige, la guía se revisa en SOL.
                g.errorDelTicket(e.numError(), e.desError());
            } else if (e.conCdr()) {
                Cdr cdr = cdrParser.parsear(e.cdrZip());
                String cdrKey = g.xmlKey().substring(0, g.xmlKey().lastIndexOf('/') + 1) + "R-" + g.nombreArchivo() + ".zip";
                storage.guardar(cdrKey, e.cdrZip());
                if (GuiaRemision.yaEnSunat(cdr.codigo())) g.errorDelTicket(cdr.codigo(), cdr.descripcion());   // H7, con el código en el CDR
                else g.aplicarCdr(cdr, cdrKey, e.enlace());
            } else if (e.correcto()) {
                g.aplicarCdr(new Cdr("0", "Aceptada por SUNAT (la respuesta no trajo el CDR)", List.of()), null, null);
            } else if (e.conError()) {
                g.errorDelTicket(e.numError(), e.desError());
            } else {
                g.ticketSinRespuesta("SUNAT respondió el ticket con un código que khipu no conoce (" + e.codigo() + "): se vuelve a consultar", g.consultasTicket() + 1 < TOPE_DE_CONSULTAS);
            }
        } catch (DomainException e) {
            if (!SIN_CONFIGURAR.contains(e.codigo())) throw e;
            g.ticketSinRespuesta(e.codigo() + " - " + e.getMessage(), false);
        } catch (SunatCredencialesException e) {
            credencialesRechazadas = e.codigo() + " - " + e.getMessage();
            g.ticketSinRespuesta("SUNAT no aceptó las credenciales API SUNAT o la clave SOL al consultar el ticket (" + e.codigo() + " - " + e.getMessage()
                    + "). Corríjalas y reconsulte el ticket", false);
        } catch (SunatRechazoException e) {
            g.ticketSinRespuesta("SUNAT no reconoce el ticket " + g.ticket() + " (" + e.codigo() + " - " + e.descripcion() + ")", false);
        } catch (SunatTransientException e) {
            g.ticketSinRespuesta(e.codigo() + " - " + e.getMessage(), g.consultasTicket() + 1 < TOPE_DE_CONSULTAS);
        } catch (IllegalStateException e) {   // CDR ilegible o storage: se vuelve a consultar
            g.ticketSinRespuesta("INFRA - " + e.getMessage(), g.consultasTicket() + 1 < TOPE_DE_CONSULTAS);
        }
        String rechazo = credencialesRechazadas;
        boolean aceptadas = respondio;
        return guardarYProgramar(g, () -> {
            registrarCredenciales(t, rechazo, aceptadas);
            if (g.estado().esperaTicket() && g.reintentoAutomatico())
                outbox.programar(t.id(), ACCION_TICKET, g.id(), clock.instant().plus(intervaloDeConsulta(g.consultasTicket() + 1)));
            else outbox.completarPorAgregado(g.id(), ACCION_TICKET);
        });
    }

    /** H3: un rechazo de las credenciales marca a la empresa (no se pide otro token con ellas); una respuesta de SUNAT con ellas la desmarca. */
    private void registrarCredenciales(Tenant t, String rechazo, boolean aceptadas) {
        if (rechazo != null) rechazos.marcar(t.id(), CredencialesDeGre.huella(t), rechazo, clock.instant());
        else if (aceptadas) rechazos.levantar(t.id());
    }

    /** H4: el outbox agotó los intentos de envío: la guía queda en error, sin reintento automático y diciendo por qué. */
    @Override public GuiaRemision agotarReintentos(UUID tenantId, UUID guiaId, int intentos) {
        GuiaRemision g = guia(tenantId, guiaId);
        if (g.estado() != EstadoGuia.ERROR_ENVIO) return g;
        g.agotarReintentos("khipu agotó " + intentos + " intentos de envío (" + g.ultimoError() + "): reenvíela con POST /v1/guias-remision/{id}/enviar cuando SUNAT responda");
        return guardarYProgramar(g, () -> outbox.completarPorAgregado(g.id(), ACCION_ENVIAR));
    }

    private static String sinRespuestaTrasElTope() {
        return "SUNAT sigue procesando el ticket después de " + TOPE_DE_CONSULTAS + " consultas (~24 h): khipu dejó de consultarlo solo; reconsúltelo con POST /v1/guias-remision/{id}/ticket";
    }

    /** Guarda y programa en una transacción; si otra ya resolvió la guía, devuelve la guía como quedó (sin programar nada). */
    private GuiaRemision guardarYProgramar(GuiaRemision g, Runnable programar) {
        try {
            uow.ejecutar(() -> { guias.guardar(g); programar.run(); });
            return g;
        } catch (DomainException e) {
            if (!"ESTADO_CONFLICTO".equals(e.codigo())) throw e;
            return guia(g.tenantId(), g.id());
        }
    }

    private GuiaRemision guia(UUID tenantId, UUID id) {
        return guias.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Guía de remisión no encontrada"));
    }

    private Tenant tenant(UUID tenantId) { return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Empresa no encontrada")); }
}
