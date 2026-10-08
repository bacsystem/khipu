package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.in.ResolverErroresUseCase.Descarte;
import pe.factura.application.port.in.ResolverErroresUseCase.Reintento;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que un administrador hace con un comprobante de la cola de errores (#196): reintentar su envío o descartarlo. Las reglas de estado son las del dominio, cada acción
 * queda en la bitácora a nombre del administrador y un descarte saca el envío del outbox en la misma transacción.
 */
class ResolverErroresServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");

    UUID empresaId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();

    /** Qué hace el envío: lo que se le diga, o lo que ya traiga el comprobante. */
    static class Envios implements EnviarDocumentoUseCase {
        final List<UUID[]> llamadas = new java.util.ArrayList<>();
        java.util.function.Function<Comprobante, Comprobante> hace = c -> c;
        RuntimeException falla;
        Fakes.Comprobantes comprobantes;
        public Comprobante enviar(UUID tenantId, UUID id) {
            llamadas.add(new UUID[]{tenantId, id});
            if (falla != null) throw falla;
            return hace.apply(comprobantes.datos.get(id));
        }
    }

    Envios envios = new Envios();

    /** La consulta del CDR a SUNAT: devuelve el comprobante como lo deje {@code hace} (por defecto, sin cambios: SUNAT no lo tiene). */
    static class Cdrs implements RecuperarCdrUseCase {
        final List<UUID> llamadas = new java.util.ArrayList<>();
        java.util.function.UnaryOperator<Comprobante> hace = c -> c;
        RuntimeException falla;
        Fakes.Comprobantes comprobantes;
        public Comprobante recuperar(UUID tenantId, UUID id) {
            llamadas.add(id);
            if (falla != null) throw falla;
            Comprobante c = hace.apply(comprobantes.datos.get(id));
            comprobantes.datos.put(id, c);
            return c;
        }
        public List<Comprobante> recuperarPendientes() { throw new AssertionError("el descarte consulta un comprobante"); }
    }

    Cdrs cdrs = new Cdrs();

    Tenant tenantEn(Entorno entorno) {
        Tenant t = Fakes.tenantListo(empresaId);
        return new Tenant(t.id(), t.ruc(), t.razonSocial(), entorno, t.sol(), t.certificado());
    }
    ColaDeErroresRepository cola = new ColaDeErroresRepository() {
        public List<Fila> listar(Filtro f, int p, int pp) { throw new AssertionError("las acciones no listan"); }
        public long contar(Filtro f) { throw new AssertionError("las acciones no cuentan"); }
        public Optional<Ubicacion> ubicar(UUID id) { return Optional.ofNullable(comprobantes.datos.get(id)).map(c -> new Ubicacion(c.tenantId(), c.nombreArchivo())); }
    };
    ResolverErroresService service;

    {
        envios.comprobantes = comprobantes;
        cdrs.comprobantes = comprobantes;
        tenants.asignarCuenta(empresaId, cuentaId);
        auditoria.uow = uow;
        service = new ResolverErroresService(cola, envios, cdrs, comprobantes, outbox, tenants, auditoria, uow, Fakes.CLOCK);
    }

    /** Un comprobante firmado de la empresa que ya falló {@code veces} veces al enviarse, con su tarea en el outbox. */
    Comprobante enError(int veces) {
        Comprobante c = Fakes.facturaFirmada(empresaId, storage);
        for (int i = 0; i < veces; i++) { if (i > 0) c.marcarEnviado(); c.marcarErrorEnvio("0109 - El sistema no puede responder"); }
        c.eventosGuardados();
        comprobantes.datos.put(c.id(), c);
        outbox.programar(empresaId, EnviarDocumentoService.ACCION_ENVIAR, c.id(), Fakes.CLOCK.instant());
        return c;
    }

    // --- reintentar -------------------------------------------------------------------------------------------------------------------------

    @Test void reintentarEnviaElComprobanteDeSuEmpresaYDiceComoQuedo() {
        Comprobante c = enError(2);
        envios.hace = x -> { x.marcarEnviado(); x.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/R.zip"); return x; };

        Reintento r = service.reintentar(ACTOR, c.id());

        assertThat(envios.llamadas).hasSize(1);
        assertThat(envios.llamadas.get(0)).containsExactly(empresaId, c.id());
        assertThat(r.comprobanteId()).isEqualTo(c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void siSunatVuelveAFallarNoEsUnErrorYDiceElFaultYLosIntentos() {
        Comprobante c = enError(2);
        envios.hace = x -> { x.marcarEnviado(); x.marcarErrorEnvio("0000 - SUNAT respondió HTTP 503"); return x; };

        Reintento r = service.reintentar(ACTOR, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.intentos()).isEqualTo(3);
        assertThat(r.fault()).isEqualTo(new FaultSunat("0000", "SUNAT respondió HTTP 503"));
    }

    @Test void siSunatRechazaConUnFaultElFaultEsElDelCdr() {
        Comprobante c = enError(1);
        envios.hace = x -> { x.rechazarPorFault("1033", "ya registrado"); return x; };

        Reintento r = service.reintentar(ACTOR, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.fault()).isEqualTo(new FaultSunat("1033", "ya registrado"));
    }

    @Test void elReintentoQuedaEnLaBitacoraConElComprobanteYComoTermino() {
        Comprobante c = enError(1);
        envios.hace = x -> { x.marcarEnviado(); x.marcarErrorEnvio("0000 - HTTP 503"); return x; };

        service.reintentar(ACTOR, c.id());

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.REINTENTAR_ENVIO_COMPROBANTE);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isEqualTo(empresaId);
        assertThat(r.detalle()).isEqualTo("comprobante=" + c.nombreArchivo() + " resultado=ERROR_ENVIO");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void siElDominioNoLoPermiteSeAnotaIgualYElErrorSube() {
        Comprobante c = enError(1);
        envios.falla = new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado ACEPTADO");

        assertThatThrownBy(() -> service.reintentar(ACTOR, c.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ESTADO_NO_ENVIABLE");

        assertThat(auditoria.registros).hasSize(1);
        assertThat(auditoria.registros.get(0).accion()).isEqualTo(AccionAdmin.REINTENTAR_ENVIO_COMPROBANTE);
        assertThat(auditoria.registros.get(0).detalle()).isEqualTo("comprobante=" + c.nombreArchivo() + " no se pudo=ESTADO_NO_ENVIABLE");
    }

    @Test void unComprobanteQueNoExisteNoSeIntentaNiSeAnota() {
        assertThatThrownBy(() -> service.reintentar(ACTOR, UUID.randomUUID())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        assertThat(envios.llamadas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unaEmpresaDeIntegracionSinCuentaTambienSeAnotaSinCuenta() {
        UUID otra = UUID.randomUUID();
        Comprobante c = Fakes.facturaFirmada(otra, storage);
        c.marcarErrorEnvio("0109 - x");
        comprobantes.datos.put(c.id(), c);

        service.reintentar(ACTOR, c.id());

        assertThat(auditoria.registros.get(0).cuentaId()).isNull();
        assertThat(auditoria.registros.get(0).tenantId()).isEqualTo(otra);
    }

    // --- descartar: antes, SUNAT ------------------------------------------------------------------------------------------------------------

    /**
     * Un error de envío puede ser un corte después de que SUNAT lo recibió. Descartarlo así haría reemitir la venta y dejaría dos comprobantes válidos en SUNAT: en
     * producción, antes de descartar se le pregunta a SUNAT (getStatusCdr) y, si lo tiene, se aplica su CDR y no se descarta.
     */
    @Test void enProduccionSiSunatYaLoTieneSeAplicaSuCdrYNoSeDescarta() {
        Comprobante c = enError(2);
        tenants.guardar(tenantEn(Entorno.PRODUCCION));
        cdrs.hace = x -> { x.marcarEnviado(); x.aplicarCdr(new Cdr("0", "La Factura numero F001-1, ha sido aceptada", List.of()), "k/R-x.zip"); return x; };

        assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), "No sale")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).codigo()).isEqualTo("SUNAT_YA_LO_TIENE");

        assertThat(cdrs.llamadas).containsExactly(c.id());
        assertThat(comprobantes.datos.get(c.id()).estado()).isNotEqualTo(EstadoDocumento.DESCARTADO);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void enProduccionSiSunatNoLoTieneSeDescarta() {
        Comprobante c = enError(2);
        tenants.guardar(tenantEn(Entorno.PRODUCCION));

        assertThat(service.descartar(ACTOR, c.id(), "No sale").estado()).isEqualTo(EstadoDocumento.DESCARTADO);
        assertThat(cdrs.llamadas).containsExactly(c.id());
    }

    /** Si no se puede preguntar, no se sabe si SUNAT lo tiene: no se descarta a ciegas. */
    @Test void enProduccionSiNoSePuedePreguntarASunatNoSeDescarta() {
        Comprobante c = enError(2);
        tenants.guardar(tenantEn(Entorno.PRODUCCION));
        cdrs.falla = new pe.factura.application.port.out.SunatTransientException("0109", "El sistema no puede responder");

        assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), "No sale")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).codigo()).isEqualTo("SUNAT_NO_DISPONIBLE");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
    }

    /** En beta no hay consulta de CDR (e-beta no la publica) ni efecto fiscal: se descarta sin preguntar. Lo que ya no está en error de envío tampoco se pregunta. */
    @Test void enBetaOSiYaNoEstaEnErrorDeEnvioNoSePreguntaASunat() {
        Comprobante c = enError(2);
        tenants.guardar(tenantEn(Entorno.BETA));
        service.descartar(ACTOR, c.id(), "No sale");

        tenants.guardar(tenantEn(Entorno.PRODUCCION));
        assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), "otra vez")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).codigo()).isEqualTo("ESTADO_NO_DESCARTABLE");
        assertThat(cdrs.llamadas).isEmpty();
    }

    // --- descartar --------------------------------------------------------------------------------------------------------------------------

    @Test void descartarDejaElComprobanteTerminalYLoSacaDelOutbox() {
        Comprobante c = enError(3);

        Descarte d = service.descartar(ACTOR, c.id(), "El cliente lo reemitió con otra serie");

        assertThat(d.comprobanteId()).isEqualTo(c.id());
        assertThat(d.estado()).isEqualTo(EstadoDocumento.DESCARTADO);
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.DESCARTADO);
        assertThat(outbox.filas).as("ya no se reintenta").isEmpty();
        assertThat(comprobantes.eventosDe(empresaId, c.id())).extracting(e -> e.estadoNuevo() + ": " + e.detalle())
                .containsExactly("DESCARTADO: Descartado por un administrador: El cliente lo reemitió con otra serie");
    }

    /** Dos administradores que descartan a la vez: la fila se toma con bloqueo para que uno espere al otro, y el guardado condicional es la segunda defensa. */
    @Test void descartarTomaLaFilaConBloqueo() {
        Comprobante c = enError(1);

        service.descartar(ACTOR, c.id(), "x");

        assertThat(comprobantes.bloqueos).isEqualTo(1);
    }

    @Test void descartarSoloSacaElEnvioDeEseComprobante() {
        Comprobante a = enError(1);
        Comprobante b = enError(1);

        service.descartar(ACTOR, a.id(), "ya no se emite");

        assertThat(outbox.filas).extracting(Fakes.Outbox.Fila::agregadoId).containsExactly(b.id());
    }

    @Test void elDescarteQuedaEnLaBitacoraConElMotivoEnLaMismaTransaccion() {
        Comprobante c = enError(1);

        service.descartar(ACTOR, c.id(), "ya no se emite");

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.DESCARTAR_COMPROBANTE);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isEqualTo(empresaId);
        assertThat(r.detalle()).isEqualTo("comprobante=" + c.nombreArchivo() + " motivo=ya no se emite");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void elMotivoSeRecortaAntesDeGuardarse() {
        Comprobante c = enError(1);

        service.descartar(ACTOR, c.id(), "   ya no se emite \n");

        assertThat(auditoria.registros.get(0).detalle()).endsWith("motivo=ya no se emite");
        assertThat(comprobantes.eventosDe(empresaId, c.id()).get(0).detalle()).isEqualTo("Descartado por un administrador: ya no se emite");
    }

    @Test void sinMotivoNoSeDescartaNada() {
        Comprobante c = enError(1);
        for (String malo : new String[]{null, "", "   ", "\n\t"}) {
            assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), malo)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("MOTIVO_REQUERIDO");
        }

        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void elMotivoTieneUnTopeDe200CaracteresContadosDespuesDeRecortar() {
        Comprobante c = enError(1);
        String justo = "x".repeat(200);

        assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), justo + "y")).extracting("codigo").isEqualTo("MOTIVO_LARGO");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);

        service.descartar(ACTOR, c.id(), "  " + justo + "  ");

        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.DESCARTADO);
    }

    @Test void soloSeDescartaUnErrorDeEnvio() {
        Comprobante firmado = Fakes.facturaFirmada(empresaId, storage);
        comprobantes.datos.put(firmado.id(), firmado);
        outbox.programar(empresaId, EnviarDocumentoService.ACCION_ENVIAR, firmado.id(), Fakes.CLOCK.instant());

        assertThatThrownBy(() -> service.descartar(ACTOR, firmado.id(), "x")).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ESTADO_NO_DESCARTABLE");

        assertThat(firmado.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(outbox.filas).as("lo que no se descartó sigue en el outbox").hasSize(1);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unComprobanteQueNoExisteNoSeDescarta() {
        assertThatThrownBy(() -> service.descartar(ACTOR, UUID.randomUUID(), "x")).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        assertThat(auditoria.registros).isEmpty();
    }

    @Test void siLaBitacoraFallaElErrorSubeYElDescarteNoSeDaPorHecho() {
        Comprobante c = enError(1);
        auditoria.falla = new IllegalStateException("la bitácora no escribe");

        assertThatThrownBy(() -> service.descartar(ACTOR, c.id(), "x")).isInstanceOf(IllegalStateException.class);
    }
}
