package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.GestionarWebhooksUseCase.Creado;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.DestinoDeWebhook;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.WebhookRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EntregaWebhook.Estado;
import pe.factura.domain.webhook.FirmaWebhook;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gestión y entrega de webhooks (#19) con repositorio y destino en memoria. */
class WebhooksServiceTest {
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final UUID TENANT = UUID.randomUUID();
    static final UUID OTRO = UUID.randomUUID();

    static class Repo implements WebhookRepository {
        final Map<UUID, EndpointWebhook> endpoints = new LinkedHashMap<>();
        final Map<UUID, byte[]> secretos = new LinkedHashMap<>();
        final Map<UUID, EntregaWebhook> entregas = new LinkedHashMap<>();
        public void crear(EndpointWebhook e, byte[] s) { endpoints.put(e.id(), e); secretos.put(e.id(), s); }
        public Optional<EndpointWebhook> buscar(UUID t, UUID id) { return Optional.ofNullable(endpoints.get(id)).filter(e -> e.tenantId().equals(t)); }
        public List<EndpointWebhook> listar(UUID t) { return endpoints.values().stream().filter(e -> e.tenantId().equals(t)).toList(); }
        public void actualizar(EndpointWebhook e) { endpoints.put(e.id(), e); }
        public void guardarSecreto(UUID t, UUID id, byte[] s) { secretos.put(id, s); }
        public Optional<byte[]> secreto(UUID t, UUID id) { return Optional.ofNullable(secretos.get(id)); }
        public boolean eliminar(UUID t, UUID id) { return buscar(t, id).isPresent() && endpoints.remove(id) != null; }
        public UUID encolar(UUID t, UUID ep, String evento, UUID doc, String payload) {
            UUID id = UUID.randomUUID();
            entregas.put(id, new EntregaWebhook(id, ep, t, evento, doc, payload, Estado.PENDIENTE, 0, AHORA, null, null, null, null, AHORA));
            return id;
        }
        public List<EntregaWebhook> entregas(UUID t, UUID ep, Estado estado, int pagina, int porPagina) {
            return entregas.values().stream().filter(e -> e.tenantId().equals(t) && e.endpointId().equals(ep) && (estado == null || e.estado() == estado)).toList();
        }
        public long contarEntregas(UUID t, UUID ep, Estado estado) { return entregas(t, ep, estado, 1, 100).size(); }
        public Optional<EntregaWebhook> buscarEntrega(UUID t, UUID id) { return Optional.ofNullable(entregas.get(id)).filter(e -> e.tenantId().equals(t)); }
        public void reencolar(UUID t, UUID id) { entregas.compute(id, (k, e) -> con(e, Estado.PENDIENTE, 0, e.siguienteIntento())); }
        public List<Pendiente> tomarVencidas(int limite, Duration bloqueo) {
            return entregas.values().stream().filter(e -> e.estado() == Estado.PENDIENTE && !e.siguienteIntento().isAfter(AHORA) && endpoints.get(e.endpointId()).activo())
                    .limit(limite).map(e -> new Pendiente(e, endpoints.get(e.endpointId()).url(), secretos.get(e.endpointId()))).toList();
        }
        public void registrarExito(UUID entrega, UUID ep, int http, Instant ahora) {
            entregas.compute(entrega, (k, e) -> con(e, Estado.EXITOSA, e.intentos() + 1, e.siguienteIntento()));
            endpoints.compute(ep, (k, e) -> new EndpointWebhook(e.id(), e.tenantId(), e.url(), e.descripcion(), e.eventos(), e.activo(), e.pausadoMotivo(), 0, e.creadoEn()));
        }
        public int registrarFallo(UUID entrega, UUID ep, Integer http, String error, Instant ahora, Instant siguiente) {
            entregas.compute(entrega, (k, e) -> new EntregaWebhook(e.id(), e.endpointId(), e.tenantId(), e.evento(), e.documentoId(), e.payload(), siguiente == null ? Estado.FALLIDA : Estado.PENDIENTE,
                    e.intentos() + 1, siguiente == null ? e.siguienteIntento() : siguiente, http, error, ahora, null, e.creadoEn()));
            endpoints.compute(ep, (k, e) -> new EndpointWebhook(e.id(), e.tenantId(), e.url(), e.descripcion(), e.eventos(), e.activo(), e.pausadoMotivo(), e.fallosConsecutivos() + 1, e.creadoEn()));
            return endpoints.get(ep).fallosConsecutivos();
        }
        public void pausar(UUID ep, String motivo) {
            endpoints.compute(ep, (k, e) -> new EndpointWebhook(e.id(), e.tenantId(), e.url(), e.descripcion(), e.eventos(), false, motivo, e.fallosConsecutivos(), e.creadoEn()));
        }
        public long limpiar(Instant antesDe) { return 0; }

        static EntregaWebhook con(EntregaWebhook e, Estado estado, int intentos, Instant siguiente) {
            return new EntregaWebhook(e.id(), e.endpointId(), e.tenantId(), e.evento(), e.documentoId(), e.payload(), estado, intentos, siguiente, e.ultimoHttp(), e.ultimoError(), e.ultimoIntentoEn(), e.entregadaEn(), e.creadoEn());
        }
    }

    static class Destino implements DestinoDeWebhook {
        final List<String> verificadas = new ArrayList<>();
        final List<Map<String, String>> cabeceras = new ArrayList<>();
        final List<String> cuerpos = new ArrayList<>();
        Respuesta respuesta = new Respuesta(200, null);
        boolean interna;
        public void exigirDestinoPublico(String url) {
            verificadas.add(url);
            if (interna) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook apunta a una dirección interna");
        }
        public Respuesta entregar(String url, Map<String, String> c, String cuerpo) { cabeceras.add(c); cuerpos.add(cuerpo); return respuesta; }
    }

    /** «Cifrado» reversible y distinto del texto, para comprobar que el servicio guarda el secreto cifrado y lo descifra al firmar. */
    static class Cifrador implements SecretCipher {
        public byte[] cifrar(byte[] plano) { byte[] r = new byte[plano.length]; for (int i = 0; i < r.length; i++) r[i] = (byte) (plano[i] ^ 0x5A); return r; }
        public byte[] descifrar(byte[] cifrado) { return cifrar(cifrado); }
    }

    static class Directo implements UnitOfWork {
        public <T> T ejecutar(Supplier<T> t) { return t.get(); }
        public void ejecutar(Runnable t) { t.run(); }
    }

    static class Correo implements CorreoSender {
        final List<String[]> enviados = new ArrayList<>();
        public void enviar(String para, String asunto, String cuerpo) { enviados.add(new String[]{para, asunto, cuerpo}); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
    }

    static class Avisos implements AvisosRepository {
        String email = "dueno@negocio.pe";
        public List<FilaCertificado> certificados(LocalDate hoy, int pagina, int porPagina) { throw new UnsupportedOperationException(); }
        public long contarCertificados(LocalDate hoy) { throw new UnsupportedOperationException(); }
        public List<FilaSol> credencialesSol(int pagina, int porPagina) { throw new UnsupportedOperationException(); }
        public long contarCredencialesSol() { throw new UnsupportedOperationException(); }
        public List<FilaBoletas> boletasSinInformar(LocalDate hoy) { throw new UnsupportedOperationException(); }
        public Optional<Situacion> situacionDe(UUID empresaId, LocalDate hoy) {
            return Optional.of(new Situacion(empresaId, "20100066603", "EMPRESA", email == null ? null : new Destino(UUID.randomUUID(), "Dueño", email), null, null, null, 0));
        }
        public Map<UUID, AvisoRegistrado> ultimosAvisos(Collection<UUID> empresas, MotivoDeAviso motivo) { throw new UnsupportedOperationException(); }
        public Optional<AvisoRegistrado> reservar(AvisoRegistrado aviso, Instant desde) { throw new UnsupportedOperationException(); }
        public void anular(UUID avisoId) { throw new UnsupportedOperationException(); }
    }

    Repo repo = new Repo();
    Destino destino = new Destino();
    Cifrador cifrador = new Cifrador();
    Correo correo = new Correo();
    Avisos avisos = new Avisos();
    Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    GestionarWebhooksService gestion = new GestionarWebhooksService(repo, destino, cifrador, new Directo(), reloj, false);
    EntregarWebhooksService entrega = new EntregarWebhooksService(repo, destino, cifrador, new Directo(), reloj, avisos, correo);

    Creado crear() { return gestion.crear(TENANT, "https://mi-sistema.pe/khipu", "ERP", List.of("comprobante.aceptado")); }

    // --- gestión -----------------------------------------------------------------------------------------------------------------------

    @Test void crearGuardaElSecretoCifradoYLoDevuelveUnaSolaVez() {
        Creado c = crear();

        assertThat(c.secreto()).startsWith("whsec_").hasSizeGreaterThan(40);
        assertThat(repo.secretos.get(c.endpoint().id())).isNotEqualTo(c.secreto().getBytes(StandardCharsets.UTF_8));
        assertThat(new String(cifrador.descifrar(repo.secretos.get(c.endpoint().id())), StandardCharsets.UTF_8)).isEqualTo(c.secreto());
        assertThat(c.endpoint().activo()).isTrue();
        assertThat(destino.verificadas).containsExactly("https://mi-sistema.pe/khipu");
        assertThat(gestion.listar(TENANT)).singleElement().isEqualTo(c.endpoint());
    }

    @Test void cadaEndpointTieneUnSecretoDistinto() {
        assertThat(crear().secreto()).isNotEqualTo(crear().secreto());
    }

    @Test void unaUrlInternaOInvalidaNoCreaNada() {
        destino.interna = true;
        assertThatThrownBy(this::crear).hasMessageContaining("dirección interna");
        destino.interna = false;
        assertThatThrownBy(() -> gestion.crear(TENANT, "http://mi-sistema.pe/h", null, List.of("*"))).hasMessageContaining("https://");
        assertThatThrownBy(() -> gestion.crear(TENANT, "https://mi-sistema.pe/h", null, List.of())).hasMessageContaining("al menos un evento");
        assertThatThrownBy(() -> gestion.crear(TENANT, "https://mi-sistema.pe/h", "x".repeat(101), List.of("*"))).hasMessageContaining("100");
        assertThat(repo.endpoints).isEmpty();
    }

    @Test void unaEmpresaTieneUnTopeDeEndpointsYLasDemasNoSeVenAfectadas() {
        for (int i = 0; i < 5; i++) crear();

        assertThatThrownBy(this::crear).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("WEBHOOK_LIMITE");
        assertThat(gestion.crear(OTRO, "https://otro.pe/h", null, List.of("*")).endpoint().tenantId()).isEqualTo(OTRO);
    }

    @Test void unEndpointAjenoNoSeVeNiSeToca() {
        UUID id = crear().endpoint().id();

        assertThat(gestion.listar(OTRO)).isEmpty();
        assertThatThrownBy(() -> gestion.actualizar(OTRO, id, null, "x", null, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> gestion.rotarSecreto(OTRO, id)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> gestion.eliminar(OTRO, id)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> gestion.probar(OTRO, id)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> gestion.entregas(OTRO, id, null, 1, 20)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(repo.endpoints).containsKey(id);
    }

    @Test void actualizarCambiaSoloLoQueSePideYSoloVerificaLaUrlSiCambio() {
        EndpointWebhook e = crear().endpoint();
        destino.verificadas.clear();

        EndpointWebhook mismaUrl = gestion.actualizar(TENANT, e.id(), e.url(), "Nuevo nombre", null, null);
        assertThat(mismaUrl.descripcion()).isEqualTo("Nuevo nombre");
        assertThat(mismaUrl.eventos()).isEqualTo(e.eventos());
        assertThat(destino.verificadas).isEmpty();

        EndpointWebhook otraUrl = gestion.actualizar(TENANT, e.id(), "https://otro.pe/h", null, List.of("comprobante.rechazado"), null);
        assertThat(otraUrl.url()).isEqualTo("https://otro.pe/h");
        assertThat(otraUrl.eventos()).containsExactly("comprobante.rechazado");
        assertThat(destino.verificadas).containsExactly("https://otro.pe/h");
        assertThat(otraUrl.descripcion()).isEqualTo("Nuevo nombre");
    }

    @Test void apagarloAMano_yReactivarUnoPausadoPorKhipuLoDejaLimpio() {
        EndpointWebhook e = crear().endpoint();
        repo.pausar(e.id(), "Se apagó solo tras 10 entregas fallidas seguidas");
        repo.endpoints.compute(e.id(), (k, v) -> new EndpointWebhook(v.id(), v.tenantId(), v.url(), v.descripcion(), v.eventos(), false, v.pausadoMotivo(), 10, v.creadoEn()));

        EndpointWebhook reactivado = gestion.actualizar(TENANT, e.id(), null, null, null, true);
        assertThat(reactivado.activo()).isTrue();
        assertThat(reactivado.fallosConsecutivos()).isZero();
        assertThat(reactivado.pausadoMotivo()).isNull();

        EndpointWebhook apagado = gestion.actualizar(TENANT, e.id(), null, null, null, false);
        assertThat(apagado.activo()).isFalse();
        assertThat(apagado.pausadoMotivo()).isNull();
    }

    @Test void rotarElSecretoInvalidaElAnterior() {
        Creado c = crear();

        String nuevo = gestion.rotarSecreto(TENANT, c.endpoint().id());

        assertThat(nuevo).startsWith("whsec_").isNotEqualTo(c.secreto());
        assertThat(new String(cifrador.descifrar(repo.secretos.get(c.endpoint().id())), StandardCharsets.UTF_8)).isEqualTo(nuevo);
    }

    @Test void eliminarBorraElEndpoint() {
        UUID id = crear().endpoint().id();
        gestion.eliminar(TENANT, id);
        assertThat(gestion.listar(TENANT)).isEmpty();
    }

    @Test void probarEncolaElEventoDePruebaYUnEndpointApagadoNoLoAcepta() {
        UUID id = crear().endpoint().id();

        UUID entregaId = gestion.probar(TENANT, id);

        EntregaWebhook e = repo.entregas.get(entregaId);
        assertThat(e.evento()).isEqualTo("webhook.prueba");
        assertThat(e.payload()).contains("\"evento\":\"webhook.prueba\"").contains("2026-10-18T15:00:00Z");
        gestion.actualizar(TENANT, id, null, null, null, false);
        assertThatThrownBy(() -> gestion.probar(TENANT, id)).extracting("codigo").isEqualTo("WEBHOOK_PAUSADO");
    }

    @Test void reenviarSoloUnaEntregaCerradaDeUnEndpointActivo() {
        UUID id = crear().endpoint().id();
        UUID pendiente = gestion.probar(TENANT, id);
        assertThatThrownBy(() -> gestion.reenviar(TENANT, pendiente)).extracting("codigo").isEqualTo("ENTREGA_NO_REENVIABLE");

        repo.entregas.compute(pendiente, (k, e) -> Repo.con(e, Estado.FALLIDA, 8, AHORA));
        gestion.reenviar(TENANT, pendiente);
        assertThat(repo.entregas.get(pendiente).estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(repo.entregas.get(pendiente).intentos()).isZero();

        repo.entregas.compute(pendiente, (k, e) -> Repo.con(e, Estado.EXITOSA, 1, AHORA));
        gestion.actualizar(TENANT, id, null, null, null, false);
        assertThatThrownBy(() -> gestion.reenviar(TENANT, pendiente)).extracting("codigo").isEqualTo("WEBHOOK_PAUSADO");
        assertThatThrownBy(() -> gestion.reenviar(OTRO, pendiente)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void laPaginacionDelLogSeValida() {
        UUID id = crear().endpoint().id();
        assertThatThrownBy(() -> gestion.entregas(TENANT, id, null, 0, 20)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
        assertThatThrownBy(() -> gestion.entregas(TENANT, id, null, 1, 101)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
    }

    // --- entrega -----------------------------------------------------------------------------------------------------------------------

    Creado conEntregaPendiente() {
        Creado c = crear();
        repo.encolar(TENANT, c.endpoint().id(), "comprobante.aceptado", UUID.randomUUID(), "{\"evento\":\"comprobante.aceptado\"}");
        return c;
    }

    @Test void unaEntregaExitosaSeFirmaConElSecretoDelEndpointYSeCierra() {
        Creado c = conEntregaPendiente();

        assertThat(entrega.entregar()).isEqualTo(1);

        Map<String, String> h = destino.cabeceras.get(0);
        long t = AHORA.getEpochSecond();
        assertThat(h).containsEntry("X-Khipu-Event", "comprobante.aceptado").containsKey("X-Khipu-Delivery");
        assertThat(h.get("X-Khipu-Signature")).isEqualTo(FirmaWebhook.cabecera(c.secreto(), t, "{\"evento\":\"comprobante.aceptado\"}"));
        EntregaWebhook e = repo.entregas.values().iterator().next();
        assertThat(e.estado()).isEqualTo(Estado.EXITOSA);
        assertThat(h.get("X-Khipu-Delivery")).isEqualTo(e.id().toString());
        assertThat(entrega.entregar()).as("ya no hay nada pendiente").isZero();
    }

    @Test void unaEntregaFallidaSeReprogramaConEsperaCrecienteHastaAgotarLosIntentos() {
        Creado c = conEntregaPendiente();
        destino.respuesta = new DestinoDeWebhook.Respuesta(503, null);
        UUID id = repo.entregas.keySet().iterator().next();
        Duration[] esperadas = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(3), Duration.ofHours(6), Duration.ofHours(12)};

        for (int intento = 1; intento <= 7; intento++) {
            repo.entregas.compute(id, (k, e) -> Repo.con(e, Estado.PENDIENTE, e.intentos(), AHORA));   // le toca ahora
            entrega.entregar();
            EntregaWebhook e = repo.entregas.get(id);
            assertThat(e.estado()).isEqualTo(Estado.PENDIENTE);
            assertThat(e.intentos()).isEqualTo(intento);
            assertThat(e.siguienteIntento()).as("tras el intento %d", intento).isEqualTo(AHORA.plus(esperadas[intento - 1]));
            assertThat(e.ultimoHttp()).isEqualTo(503);
            assertThat(e.ultimoError()).isEqualTo("El servidor respondió HTTP 503");
        }
        repo.entregas.compute(id, (k, e) -> Repo.con(e, Estado.PENDIENTE, e.intentos(), AHORA));
        entrega.entregar();
        assertThat(repo.entregas.get(id).estado()).as("octavo intento: agotada").isEqualTo(Estado.FALLIDA);
        assertThat(repo.endpoints.get(c.endpoint().id()).fallosConsecutivos()).isEqualTo(8);
    }

    @Test void unFalloDeRedGuardaElMensajeSinCodigoHttp() {
        conEntregaPendiente();
        destino.respuesta = new DestinoDeWebhook.Respuesta(0, "No se pudo conectar: Connection refused");

        entrega.entregar();

        EntregaWebhook e = repo.entregas.values().iterator().next();
        assertThat(e.ultimoHttp()).isNull();
        assertThat(e.ultimoError()).isEqualTo("No se pudo conectar: Connection refused");
    }

    @Test void unExitoPoneEnCeroLosFallosSeguidos() {
        Creado c = conEntregaPendiente();
        destino.respuesta = new DestinoDeWebhook.Respuesta(500, null);
        entrega.entregar();
        assertThat(repo.endpoints.get(c.endpoint().id()).fallosConsecutivos()).isEqualTo(1);

        destino.respuesta = new DestinoDeWebhook.Respuesta(204, null);
        UUID id = repo.entregas.keySet().iterator().next();
        repo.entregas.compute(id, (k, e) -> Repo.con(e, Estado.PENDIENTE, e.intentos(), AHORA));
        entrega.entregar();

        assertThat(repo.endpoints.get(c.endpoint().id()).fallosConsecutivos()).isZero();
        assertThat(repo.entregas.get(id).estado()).isEqualTo(Estado.EXITOSA);
    }

    @Test void conDiezFallosSeguidosElEndpointSePausaYSeAvisaPorCorreoALaCuenta() {
        Creado c = crear();
        for (int i = 0; i < 10; i++) repo.encolar(TENANT, c.endpoint().id(), "comprobante.aceptado", UUID.randomUUID(), "{}");
        destino.respuesta = new DestinoDeWebhook.Respuesta(500, null);

        entrega.entregar();

        EndpointWebhook e = repo.endpoints.get(c.endpoint().id());
        assertThat(e.activo()).isFalse();
        assertThat(e.pausadoMotivo()).contains("10 entregas fallidas seguidas").contains("HTTP 500");
        assertThat(correo.enviados).singleElement().satisfies(m -> {
            assertThat(m[0]).isEqualTo("dueno@negocio.pe");
            assertThat(m[1]).contains("webhook");
            assertThat(m[2]).contains("https://mi-sistema.pe/khipu").contains("HTTP 500");
        });
        assertThat(entrega.entregar()).as("un endpoint pausado no recibe más intentos").isZero();
    }

    @Test void sinCuentaQueAvisarLaPausaIgualSeAplica() {
        avisos.email = null;
        Creado c = crear();
        for (int i = 0; i < 10; i++) repo.encolar(TENANT, c.endpoint().id(), "comprobante.aceptado", UUID.randomUUID(), "{}");
        destino.respuesta = new DestinoDeWebhook.Respuesta(500, null);

        entrega.entregar();

        assertThat(repo.endpoints.get(c.endpoint().id()).activo()).isFalse();
        assertThat(correo.enviados).isEmpty();
    }

    @Test void unEndpointApagadoNoRecibeEntregas() {
        Creado c = conEntregaPendiente();
        gestion.actualizar(TENANT, c.endpoint().id(), null, null, null, false);

        assertThat(entrega.entregar()).isZero();
        assertThat(destino.cuerpos).isEmpty();
    }
}
