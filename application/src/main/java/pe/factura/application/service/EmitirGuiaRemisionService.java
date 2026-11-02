package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EmitirGuiaRemisionCommand;
import pe.factura.application.port.in.EmitirGuiaRemisionUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.DatosDeGuia;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.PuntoDeTraslado;
import pe.factura.domain.documento.Traslado;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Emisión de guías de remisión (#39). Todo lo que la guía puede rechazar se rechaza antes de numerar: las reglas del dominio, los establecimientos de la
 * empresa (#80), el certificado y, si se envía en el acto, las credenciales API SUNAT y SOL y que haya API GRE para el entorno. La numeración toma la serie con
 * bloqueo de fila ({@code siguienteNumero}) y la retiene hasta guardar la guía firmada, como la factura: un fallo después de numerar no deja huecos.
 */
@RequiredArgsConstructor
public class EmitirGuiaRemisionService implements EmitirGuiaRemisionUseCase {
    private final GuiaRemisionRepository guias;
    private final SerieRepository series;
    private final TenantRepository tenants;
    private final EstablecimientoRepository establecimientos;
    private final DocumentStorage storage;
    private final GuiaUblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final SunatGreGateway gre;
    private final EnviarGuiaRemisionUseCase enviar;
    private final IdempotenciaRepository idempotencia;
    private final UnitOfWork uow;
    private final Clock clock;
    private final RechazoDeCredencialesGreRepository rechazos;

    /** Espacio de claves de idempotencia de las guías: la misma clave en facturas o notas es otra clave. */
    static String alcance(UUID tenantId) { return "guia:" + tenantId; }

    @Override public Emision emitir(UUID tenantId, EmitirGuiaRemisionCommand cmd, Idempotencia clave) {
        // Un reintento con la misma clave se contesta antes de validar: la guía ya existe (ver EmitirComprobanteService.yaEmitidaSiLaHay).
        if (clave != null) {
            Optional<IdempotenciaRepository.Registro> previo = idempotencia.buscar(alcance(tenantId), clave.clave());
            if (previo.isPresent()) return yaEmitida(tenantId, clave, previo.get());
        }
        Tenant t = tenant(tenantId);
        t.exigirListoParaEmitir(LocalDate.now(clock));
        if (cmd.enviarAutomatico()) exigirListoParaEnviar(t);
        GuiaRemision g = armar(t, cmd);
        Emision emision = uow.ejecutar(() -> {
            if (clave != null) {
                Optional<IdempotenciaRepository.Registro> previo = idempotencia.reservar(alcance(tenantId), clave.clave(), clave.huella());
                if (previo.isPresent()) return yaEmitida(tenantId, clave, previo.get());
            }
            long numero = series.siguienteNumero(tenantId, g.tipo(), g.serie());
            g.asignarNumero(numero, t.ruc());
            String xml = ubl.generar(g, t);
            FirmaResultado firma = signer.firmar(xml, t.certificado());
            xsd.validar(firma.xmlFirmado(), g.tipo());
            String key = tenantId + "/" + g.fechaEmision().getYear() + "/" + String.format("%02d", g.fechaEmision().getMonthValue()) + "/guias/" + g.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            g.firmar(firma.hash(), key);
            guias.guardar(g);
            if (clave != null) idempotencia.completar(alcance(tenantId), clave.clave(), g.id());
            return new Emision(g, false);
        });
        if (emision.repetida() || !cmd.enviarAutomatico()) return emision;
        return new Emision(enviar.enviar(tenantId, emision.guia().id()), false);
    }

    @Override public GuiaRemision validar(UUID tenantId, EmitirGuiaRemisionCommand cmd) { return armar(tenant(tenantId), cmd); }

    private Tenant tenant(UUID tenantId) { return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Empresa no encontrada")); }

    /** Antes de numerar: sin estas credenciales la guía quedaría firmada y sin poder enviarse. */
    private void exigirListoParaEnviar(Tenant t) {
        if (t.apiSunat() == null)
            throw new DomainException("CREDENCIALES_API_SUNAT_NO_CARGADAS", "La guía de remisión se envía por la API REST de SUNAT: cargue las credenciales API SUNAT (client_id y client_secret) "
                    + "en PUT /v1/empresa/credenciales-api-sunat, o emítala con enviar_automatico: false");
        t.exigirCredencialesSol();
        if (!gre.disponible(t.entorno())) throw noDisponible(t);
        CredencialesDeGre.exigirNoRechazadas(rechazos, t, clock);
    }

    /** H1: el envío de la GRE está apagado en el servidor (por defecto) o no hay API para el entorno de la empresa. Lo comparte el reenvío. */
    static DomainException noDisponible(Tenant t) {
        return new DomainException("GRE_NO_DISPONIBLE", "El envío de guías de remisión a SUNAT no está disponible en este servidor para una empresa en " + t.entorno()
                + ": está apagado mientras el contrato con SUNAT no se verifique (SUNAT_API_GRE_HABILITADA), o no hay una API GRE de pruebas para BETA "
                + "(SUNAT_API_GRE_BETA_URL). Emítala con enviar_automatico: false para revisar el XML");
    }

    private Emision yaEmitida(UUID tenantId, Idempotencia clave, IdempotenciaRepository.Registro previo) {
        if (!previo.huella().equals(clave.huella()))
            throw new DomainException("IDEMPOTENCIA_INVALIDA", "La clave de idempotencia " + clave.clave() + " ya se usó con otro contenido: genere una clave nueva para otra guía");
        GuiaRemision g = guias.buscar(tenantId, previo.recursoId())
                .orElseThrow(() -> new IllegalStateException("La clave " + clave.clave() + " apunta a una guía que no existe"));
        return new Emision(g, true);
    }

    /** La guía del pedido, con los establecimientos propios resueltos (#80). */
    private GuiaRemision armar(Tenant t, EmitirGuiaRemisionCommand cmd) {
        Traslado tr = cmd.traslado();
        if (tr != null && cmd.datos() instanceof DatosDeGuia.DeRemitente)
            tr = new Traslado(tr.fechaInicio(), tr.pesoBruto(), tr.unidadPeso(), tr.bultos(), resolver(t, tr.partida(), true), resolver(t, tr.llegada(), false),
                    tr.vehiculos(), tr.conductores(), tr.indicadores());
        return GuiaRemision.crear(t.id(), t.ruc(), cmd.serie(), cmd.fechaEmision(), cmd.destinatario(), cmd.datos(), tr, cmd.items(), cmd.documentos(), cmd.observaciones(), clock);
    }

    /**
     * Un punto que es un establecimiento de la propia empresa (sin RUC o con el suyo): debe existir y estar activo (3366/3370) y, si trae ubigeo, ser el del
     * establecimiento (3367/3371). Solo con el código, khipu pone el ubigeo y la dirección registrados. {@code 0000} es el domicilio fiscal. Los establecimientos
     * de otro RUC (el de un cliente, el de un depósito) no se pueden comprobar aquí: van con su ubigeo y dirección.
     */
    private PuntoDeTraslado resolver(Tenant t, PuntoDeTraslado p, boolean partida) {
        if (p == null || !p.esEstablecimiento() || !p.codigoEstablecimiento().matches("\\d{4}")) return p;
        if (p.rucEstablecimiento() != null && !p.rucEstablecimiento().equals(t.ruc())) return p;
        String cual = partida ? "punto de partida" : "punto de llegada";
        Domicilio d;
        if (Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(p.codigoEstablecimiento())) {
            d = t.domicilio();
            if (d == null) throw new DomainException("TRASLADO_INVALIDO", (partida ? "3366" : "3370") + " - El " + cual + " es el domicilio fiscal (0000), pero la empresa no lo tiene configurado: "
                    + "cárguelo en PUT /v1/empresa/datos-fiscales o indique el ubigeo y la dirección");
        } else {
            Establecimiento e = establecimientos.buscar(t.id(), p.codigoEstablecimiento())
                    .orElseThrow(() -> new DomainException("TRASLADO_INVALIDO", (partida ? "3366" : "3370") + " - El establecimiento " + p.codigoEstablecimiento() + " del " + cual
                            + " no está registrado en la empresa (GET /v1/empresa/establecimientos)"));
            if (!e.activo()) throw new DomainException("TRASLADO_INVALIDO", (partida ? "3366" : "3370") + " - El establecimiento " + p.codigoEstablecimiento() + " (" + e.nombre() + ") del " + cual + " está dado de baja");
            d = e.domicilio();
        }
        if (p.ubigeo() == null && p.direccion() == null) return new PuntoDeTraslado(d.ubigeo(), d.direccion(), p.codigoEstablecimiento(), t.ruc());
        if (p.ubigeo() != null && !p.ubigeo().equals(d.ubigeo()))
            throw new DomainException("TRASLADO_INVALIDO", (partida ? "3367" : "3371") + " - El establecimiento " + p.codigoEstablecimiento() + " está en el ubigeo " + d.ubigeo()
                    + ", no en el " + p.ubigeo() + " del " + cual);
        return p;
    }
}
