package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.CambioDeEntorno;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.ResultadoDeConexion;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.Resultado;
import pe.factura.application.port.out.AccionesDeEmpresaRepository;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las acciones del administrador sobre una empresa (#187): cambiar el entorno, revocar una API key y probar la conexión con SUNAT. Cada una queda
 * en la bitácora, y la bitácora nunca lleva secretos.
 */
class AccionesDeEmpresaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");

    UUID empresaId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    Tenant tenant = new Tenant(empresaId, "20100066603", "COMERCIAL ANDINA SAC", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"), null);

    /** En memoria, con las mismas reglas atómicas del real: cada cambio solo ocurre si el estado era el esperado. */
    static class Acciones implements AccionesDeEmpresaRepository {
        final Map<UUID, Entorno> entornos = new HashMap<>();
        final Map<UUID, Map<UUID, ApiKeyDeEmpresa>> keys = new HashMap<>();
        final Map<UUID, Long> pendientes = new HashMap<>();
        final List<UUID> revocadas = new ArrayList<>();
        public Optional<Entorno> entornoDe(UUID t) { return Optional.ofNullable(entornos.get(t)); }
        public boolean cambiarEntorno(UUID t, Entorno desde, Entorno hacia) {
            if (entornos.get(t) != desde) return false;
            entornos.put(t, hacia);
            return true;
        }
        public long enviosPendientes(UUID t) { return pendientes.getOrDefault(t, 0L); }
        public Optional<ApiKeyDeEmpresa> apiKey(UUID t, UUID k) { return Optional.ofNullable(keys.getOrDefault(t, Map.of()).get(k)); }
        public boolean revocarApiKey(UUID t, UUID k, Instant cuando) {
            ApiKeyDeEmpresa a = keys.getOrDefault(t, Map.of()).get(k);
            if (a == null || !a.activa()) return false;
            keys.get(t).put(k, new ApiKeyDeEmpresa(a.prefijo(), false));
            revocadas.add(k);
            return true;
        }
    }

    /** Qué responde SUNAT a la consulta de prueba: un valor, o una excepción. */
    static class Sunat implements SunatBillingGateway {
        RuntimeException falla;
        Tenant llamadoCon;
        String ticket;
        public byte[] sendBill(Tenant t, String n, byte[] x) { throw new AssertionError("la prueba no envía comprobantes"); }
        public String sendSummary(Tenant t, String n, byte[] x) { throw new AssertionError("la prueba no envía resúmenes"); }
        public EstadoTicket getStatus(Tenant t, String ticket) {
            llamadoCon = t;
            this.ticket = ticket;
            if (falla != null) throw falla;
            return new EstadoTicket("0", null);
        }
    }

    Map<UUID, Tenant> tenantsGuardados = new HashMap<>();
    TenantRepository tenants = new TenantRepository() {
        public void guardar(Tenant t) { throw new AssertionError("las acciones no reescriben la empresa"); }
        public Optional<Tenant> buscar(UUID id) { return Optional.ofNullable(tenantsGuardados.get(id)); }
        public Optional<Tenant> buscarPorRuc(String ruc) { throw new AssertionError("no se busca por RUC"); }
        public List<Tenant> listarPorCuenta(UUID c) { throw new AssertionError("no se listan las de una cuenta"); }
        public void asignarCuenta(UUID t, UUID c) { throw new AssertionError("no se reasigna la cuenta"); }
        public Optional<UUID> cuentaDe(UUID t) { return t.equals(empresaId) ? Optional.of(cuentaId) : Optional.empty(); }
    };

    Acciones acciones = new Acciones();
    Sunat sunat = new Sunat();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    AccionesDeEmpresaService service;

    {
        tenantsGuardados.put(empresaId, tenant);
        acciones.entornos.put(empresaId, Entorno.BETA);
        acciones.keys.put(empresaId, new HashMap<>(Map.of(keyId, new AccionesDeEmpresaRepository.ApiKeyDeEmpresa("fk_ab12", true))));
        auditoria.uow = uow;
        service = new AccionesDeEmpresaService(tenants, acciones, sunat, auditoria, uow, Fakes.CLOCK);
    }

    // --- cambiar el entorno ---------------------------------------------------------------------------------------------------------

    @Test void cambiarElEntornoLoCambiaYDiceDeCuantoAcuanto() {
        CambioDeEntorno c = service.cambiarEntorno(ACTOR, empresaId, Entorno.PRODUCCION);

        assertThat(c.empresaId()).isEqualTo(empresaId);
        assertThat(c.desde()).isEqualTo(Entorno.BETA);
        assertThat(c.hacia()).isEqualTo(Entorno.PRODUCCION);
        assertThat(acciones.entornos.get(empresaId)).isEqualTo(Entorno.PRODUCCION);
    }

    @Test void tambienSePuedeVolverABeta() {
        acciones.entornos.put(empresaId, Entorno.PRODUCCION);

        service.cambiarEntorno(ACTOR, empresaId, Entorno.BETA);

        assertThat(acciones.entornos.get(empresaId)).isEqualTo(Entorno.BETA);
    }

    @Test void elCambioDeEntornoQuedaEnLaBitacoraConElOrigenYElDestino() {
        service.cambiarEntorno(ACTOR, empresaId, Entorno.PRODUCCION);

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.CAMBIAR_ENTORNO_EMPRESA);
        assertThat(r.tenantId()).isEqualTo(empresaId);
        assertThat(r.cuentaId()).as("también aparece en la bitácora de su cuenta").isEqualTo(cuentaId);
        assertThat(r.detalle()).isEqualTo("desde=BETA hacia=PRODUCCION");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void elCambioYLaBitacoraVanEnLaMismaTransaccion() {
        service.cambiarEntorno(ACTOR, empresaId, Entorno.PRODUCCION);

        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void siLaBitacoraFallaElCambioFalla() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, empresaId, Entorno.PRODUCCION)).isInstanceOf(IllegalStateException.class);
    }

    @Test void pedirElEntornoQueYaTieneEsConflictoYNoDejaRegistro() {
        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, empresaId, Entorno.BETA)).extracting("codigo").isEqualTo("ENTORNO_SIN_CAMBIOS");
        assertThat(auditoria.registros).isEmpty();
    }

    /** Lo que se emitió en un entorno no debe enviarse al otro: con tareas pendientes en el outbox el cambio se rechaza. */
    @Test void conEnviosPendientesNoSeCambiaElEntornoNiSeDejaRegistro() {
        acciones.pendientes.put(empresaId, 3L);

        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, empresaId, Entorno.PRODUCCION)).extracting("codigo").isEqualTo("EMPRESA_CON_ENVIOS_PENDIENTES");
        assertThat(acciones.entornos.get(empresaId)).isEqualTo(Entorno.BETA);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unaEmpresaQueNoExisteEsNoEncontradaEnLasTresAcciones() {
        UUID otra = UUID.randomUUID();

        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, otra, Entorno.PRODUCCION)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, otra, keyId)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.probarConexion(ACTOR, otra)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, null, Entorno.PRODUCCION)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unEntornoNuloEsInvalido() {
        assertThatThrownBy(() -> service.cambiarEntorno(ACTOR, empresaId, null)).extracting("codigo").isEqualTo("ENTORNO_INVALIDO");
        assertThat(auditoria.registros).isEmpty();
    }

    // --- revocar una API key --------------------------------------------------------------------------------------------------------

    @Test void revocarLaDejaInactivaYDiceCual() {
        var r = service.revocarApiKey(ACTOR, empresaId, keyId);

        assertThat(r.apiKeyId()).isEqualTo(keyId);
        assertThat(r.revocadaEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(acciones.revocadas).containsExactly(keyId);
        assertThat(acciones.keys.get(empresaId).get(keyId).activa()).isFalse();
    }

    @Test void revocarQuedaEnLaBitacoraConSuPrefijoYNuncaConLaClave() {
        service.revocarApiKey(ACTOR, empresaId, keyId);

        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.accion()).isEqualTo(AccionAdmin.REVOCAR_API_KEY_EMPRESA);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.tenantId()).isEqualTo(empresaId);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.detalle()).isEqualTo("prefijo=fk_ab12");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void revocarUnaKeyYaRevocadaEsConflictoYNoDejaOtroRegistro() {
        service.revocarApiKey(ACTOR, empresaId, keyId);

        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, empresaId, keyId)).extracting("codigo").isEqualTo("API_KEY_YA_REVOCADA");
        assertThat(auditoria.registros).hasSize(1);
    }

    /** Una key de otra empresa no existe para esta pantalla: no se revoca ni se confirma que existe. */
    @Test void unaKeyDeOtraEmpresaOInexistenteEsNoEncontrada() {
        UUID otraEmpresa = UUID.randomUUID();
        tenantsGuardados.put(otraEmpresa, tenant);
        acciones.entornos.put(otraEmpresa, Entorno.BETA);

        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, otraEmpresa, keyId)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, empresaId, UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, empresaId, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(acciones.revocadas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void siLaBitacoraFallaLaKeyNoQuedaRevocada() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.revocarApiKey(ACTOR, empresaId, keyId)).isInstanceOf(IllegalStateException.class);
    }

    // --- probar la conexión con SUNAT -----------------------------------------------------------------------------------------------

    @Test void sunatQueContestaEsConectado() {
        ResultadoDeConexion r = service.probarConexion(ACTOR, empresaId);

        assertThat(r.resultado()).isEqualTo(Resultado.CONECTADO);
        assertThat(r.entorno()).isEqualTo(Entorno.BETA);
        assertThat(r.codigo()).isNull();
    }

    @Test void laPruebaUsaLasCredencialesYElEntornoDeLaEmpresaYNoEnviaNada() {
        service.probarConexion(ACTOR, empresaId);

        assertThat(sunat.llamadoCon).isEqualTo(tenant);
        assertThat(sunat.ticket).as("un ticket que no existe: es una consulta de solo lectura").isEqualTo(AccionesDeEmpresaService.TICKET_DE_PRUEBA);
    }

    @Test void unErrorDefinitivoDeSunatSeInformaConSuCodigoYSuMensaje() {
        sunat.falla = new SunatRechazoException("1033", "El ticket no existe");

        ResultadoDeConexion r = service.probarConexion(ACTOR, empresaId);

        assertThat(r.resultado()).isEqualTo(Resultado.RECHAZADO);
        assertThat(r.codigo()).isEqualTo("1033");
        assertThat(r.mensaje()).isEqualTo("El ticket no existe");
    }

    @Test void sinRespuestaUtilDeSunatSeInformaConSuCodigoYSuMensaje() {
        sunat.falla = new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT");

        ResultadoDeConexion r = service.probarConexion(ACTOR, empresaId);

        assertThat(r.resultado()).isEqualTo(Resultado.SIN_RESPUESTA);
        assertThat(r.codigo()).isEqualTo("0109");
        assertThat(r.mensaje()).isEqualTo("Tiempo de espera agotado llamando a SUNAT");
    }

    @Test void sinCredencialesSolNoSeLlamaASunat() {
        UUID sinSol = UUID.randomUUID();
        tenantsGuardados.put(sinSol, new Tenant(sinSol, "20100066611", "SIN SOL SAC", Entorno.BETA, null, null));
        acciones.entornos.put(sinSol, Entorno.BETA);

        assertThatThrownBy(() -> service.probarConexion(ACTOR, sinSol)).extracting("codigo").isEqualTo("SOL_NO_CARGADAS");
        assertThat(sunat.llamadoCon).isNull();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void laPruebaQuedaEnLaBitacoraConElResultadoYNuncaConLasCredenciales() {
        sunat.falla = new SunatRechazoException("1033", "El ticket no existe de MODDATOS");

        service.probarConexion(ACTOR, empresaId);

        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.accion()).isEqualTo(AccionAdmin.PROBAR_CONEXION_EMPRESA);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.tenantId()).isEqualTo(empresaId);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.detalle()).isEqualTo("entorno=BETA resultado=RECHAZADO codigo=1033");
        assertThat(r.detalle()).doesNotContain("MODDATOS").doesNotContain("moddatos");
    }

    @Test void tambienQuedaEnLaBitacoraCuandoSunatContesta() {
        service.probarConexion(ACTOR, empresaId);

        assertThat(auditoria.registros.get(0).detalle()).isEqualTo("entorno=BETA resultado=CONECTADO");
    }

    /** La llamada a SUNAT es lenta y externa: no se hace con una transacción abierta. */
    @Test void sunatSeConsultaFueraDeLaTransaccion() {
        var dentro = new boolean[1];
        sunat = new Sunat() {
            @Override public EstadoTicket getStatus(Tenant t, String ticket) { dentro[0] = uow.dentro; return super.getStatus(t, ticket); }
        };
        service = new AccionesDeEmpresaService(tenants, acciones, sunat, auditoria, uow, Fakes.CLOCK);

        service.probarConexion(ACTOR, empresaId);

        assertThat(dentro[0]).isFalse();
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }
}
