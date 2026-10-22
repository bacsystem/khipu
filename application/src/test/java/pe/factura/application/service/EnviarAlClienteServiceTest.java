package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EnviarAlClienteUseCase.Preferencia;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoNoEnviadoException;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.EnvioAlClienteRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EnvioAlCliente;
import pe.factura.domain.documento.EnvioAlCliente.Estado;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El envío automático del comprobante aceptado a su cliente (#325) con fakes: qué sale, qué se anota, los reintentos, la falta de SMTP, los adjuntos demasiado grandes y el
 * comprobante que se anuló antes de salir. El alta del envío (en la transacción del paso a aceptado) y el bloqueo entre réplicas los prueba la persistencia.
 */
class EnviarAlClienteServiceTest {
    record Correo(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) {}

    /**
     * El repositorio en memoria, con las mismas reglas que la tabla: una fila por comprobante, y tomar, renovar y anotar solo con la marca vigente. El bloqueo vence según
     * {@link #reloj}, que la prueba puede adelantar (lo que en la tabla hace {@code now()}).
     */
    static final class Envios implements EnvioAlClienteRepository {
        final Set<UUID> automaticos = new HashSet<>();
        final Map<UUID, EnvioAlCliente> filas = new java.util.LinkedHashMap<>();
        final Map<UUID, UUID> tenantDe = new HashMap<>();
        final Map<UUID, Instant> bloqueos = new HashMap<>();
        final java.util.concurrent.atomic.AtomicReference<Instant> reloj = new java.util.concurrent.atomic.AtomicReference<>(Fakes.CLOCK.instant());
        private long secuencia;

        void anotar(UUID tenant, UUID documento, String destinatario) {
            if (filas.containsKey(documento)) return;
            filas.put(documento, new EnvioAlCliente(documento, destinatario, Estado.PENDIENTE, 0, null, Instant.EPOCH, null, Instant.EPOCH));
            tenantDe.put(documento, tenant);
        }
        /** Una marca distinta cada vez, como la de la tabla (que lleva microsegundos). */
        private Instant marca(Duration bloqueo) { return reloj.get().plus(bloqueo).plusNanos(++secuencia * 1000); }
        private boolean bloqueado(UUID d) { return bloqueos.containsKey(d) && bloqueos.get(d).isAfter(reloj.get()); }
        public boolean automatico(UUID t) { return automaticos.contains(t); }
        public void fijarAutomatico(UUID t, boolean a) { if (a) automaticos.add(t); else automaticos.remove(t); }
        public List<Tomado> tomarVencidos(int limite, Duration bloqueo) {
            List<Tomado> r = new ArrayList<>();
            for (EnvioAlCliente e : filas.values()) {
                if (r.size() >= limite) break;
                if (e.estado() != Estado.PENDIENTE || bloqueado(e.documentoId()) || e.siguienteIntento().isAfter(reloj.get())) continue;
                Instant m = marca(bloqueo);
                bloqueos.put(e.documentoId(), m);
                r.add(new Tomado(tenantDe.get(e.documentoId()), e.documentoId(), e.destinatario(), e.intentos(), m));
            }
            return r;
        }
        private boolean tomado(Tomado t) { return t.bloqueo().equals(bloqueos.get(t.documentoId())) && filas.get(t.documentoId()).estado() == Estado.PENDIENTE; }
        public Optional<Tomado> renovar(Tomado t, Duration bloqueo) {
            if (!tomado(t)) return Optional.empty();
            Instant m = marca(bloqueo);
            bloqueos.put(t.documentoId(), m);
            return Optional.of(new Tomado(t.tenantId(), t.documentoId(), t.destinatario(), t.intentos(), m));
        }
        public boolean registrarEnvio(Tomado t, Instant ahora) {
            if (!tomado(t)) return false;
            EnvioAlCliente e = filas.get(t.documentoId());
            filas.put(t.documentoId(), new EnvioAlCliente(e.documentoId(), e.destinatario(), Estado.ENVIADO, e.intentos() + 1, null, e.siguienteIntento(), ahora, e.creadoEn()));
            bloqueos.remove(t.documentoId());
            return true;
        }
        public boolean registrarFallo(Tomado t, String error, Instant ahora, Instant siguiente) {
            if (!tomado(t)) return false;
            EnvioAlCliente e = filas.get(t.documentoId());
            filas.put(t.documentoId(), new EnvioAlCliente(e.documentoId(), e.destinatario(), siguiente == null ? Estado.FALLIDO : Estado.PENDIENTE, e.intentos() + 1, error,
                    siguiente == null ? e.siguienteIntento() : siguiente, null, e.creadoEn()));
            bloqueos.remove(t.documentoId());
            return true;
        }
        public boolean omitir(Tomado t, String motivo, Instant ahora) {
            if (!tomado(t)) return false;
            EnvioAlCliente e = filas.get(t.documentoId());
            filas.put(t.documentoId(), new EnvioAlCliente(e.documentoId(), e.destinatario(), Estado.OMITIDO, e.intentos(), motivo, e.siguienteIntento(), null, e.creadoEn()));
            bloqueos.remove(t.documentoId());
            return true;
        }
        public Optional<EnvioAlCliente> deComprobante(UUID t, UUID d) { return Optional.ofNullable(filas.get(d)).filter(e -> t.equals(tenantDe.get(d))); }
    }

    private final Fakes.Comprobantes repo = new Fakes.Comprobantes();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Tenants tenants = new Fakes.Tenants();
    private final Envios envios = new Envios();
    private final List<Correo> enviados = new ArrayList<>();
    private final AtomicBoolean smtp = new AtomicBoolean(true);
    private final AtomicInteger fallos = new AtomicInteger();
    private java.util.function.Supplier<RuntimeException> falla = () -> new IllegalStateException("SMTP caído en smtp.interno.khipu:587");
    /** Lo que hace el correo de mentira antes de entregar (la prueba de dos réplicas lo usa para que el SMTP «tarde»). */
    private java.util.function.Consumer<String> alEnviar = para -> {};
    private final Fakes.Bajas bajas = new Fakes.Bajas();
    /** Lo que pasa mientras se arma el PDF de un comprobante (la prueba de dos réplicas lo usa para que armar los adjuntos «tarde»). */
    private java.util.function.Consumer<UUID> alGenerarPdf = id -> {};
    private byte[] pdf = "%PDF".getBytes();
    private final CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) { enviar(para, asunto, cuerpo, List.of()); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) {
            alEnviar.accept(para);
            if (fallos.getAndDecrement() > 0) throw falla.get();
            enviados.add(new Correo(para, asunto, cuerpo, adjuntos));
        }
        @Override public boolean entregaDeVerdad() { return smtp.get(); }
    };
    private final ConsultarComprobanteService consultar = new ConsultarComprobanteService(repo, tenants, storage, (c, t, qr, logo) -> { alGenerarPdf.accept(c.id()); return pdf; }, new Fakes.Establecimientos(new Fakes.Series()),
            xml -> Optional.empty());
    private final EnviarAlClienteService service = new EnviarAlClienteService(envios, consultar, tenants, correo, Fakes.UOW, Fakes.CLOCK, bajas);
    private final UUID tenant = UUID.randomUUID();

    private Comprobante aceptadaConEnvio() {
        tenants.guardar(Fakes.tenantListo(tenant));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        c.marcarEnviado();
        storage.guardar("cdr/" + c.id(), new byte[]{0x50, 0x4b});
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + c.id());
        repo.guardar(c);
        envios.fijarAutomatico(tenant, true);
        envios.anotar(tenant, c.id(), "compras@cliente.pe");
        return c;
    }

    @Test void mandaElPdfElXmlYElCdrAlCorreoDelClienteYLoAnotaEnviado() {
        Comprobante c = aceptadaConEnvio();

        assertThat(service.enviarPendientes()).isEqualTo(1);

        assertThat(enviados).hasSize(1);
        Correo e = enviados.get(0);
        assertThat(e.para()).isEqualTo("compras@cliente.pe");
        assertThat(e.asunto()).isEqualTo("Factura F001-" + c.numero() + " - EMPRESA SAC");
        assertThat(e.cuerpo()).as("el mismo texto que el envío a mano, sin mensaje del emisor").startsWith("Adjuntamos la factura electrónica F001-" + c.numero());
        assertThat(e.adjuntos()).extracting(Adjunto::nombre).containsExactly(c.nombreArchivo() + ".pdf", c.nombreArchivo() + ".xml", "R-" + c.nombreArchivo() + ".zip");
        EnvioAlCliente anotado = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(anotado.estado()).isEqualTo(Estado.ENVIADO);
        assertThat(anotado.intentos()).isEqualTo(1);
        assertThat(anotado.enviadoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void loYaEnviadoNoSeVuelveAMandar() {
        aceptadaConEnvio();
        service.enviarPendientes();

        assertThat(service.enviarPendientes()).isZero();
        assertThat(enviados).hasSize(1);
    }

    @Test void unFalloDelCorreoSeReintentaMasTardeConElMotivo() {
        Comprobante c = aceptadaConEnvio();
        fallos.set(1);

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(e.intentos()).isEqualTo(1);
        assertThat(e.ultimoError()).as("la clase de fallo, no el mensaje crudo con el host").isEqualTo("El servidor de correo no aceptó el mensaje");
        assertThat(e.siguienteIntento()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofMinutes(1)));
        assertThat(enviados).isEmpty();
    }

    @Test void alAgotarLosIntentosQuedaFallido() {
        Comprobante c = aceptadaConEnvio();
        fallos.set(100);
        EnvioAlCliente e = envios.filas.get(c.id());
        envios.filas.put(c.id(), new EnvioAlCliente(c.id(), e.destinatario(), Estado.PENDIENTE, EnvioAlCliente.MAXIMO_INTENTOS - 1, "antes", Instant.EPOCH, null, Instant.EPOCH));

        service.enviarPendientes();

        EnvioAlCliente fallido = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(fallido.estado()).isEqualTo(Estado.FALLIDO);
        assertThat(fallido.intentos()).isEqualTo(EnvioAlCliente.MAXIMO_INTENTOS);
        assertThat(service.enviarPendientes()).as("un fallido no se vuelve a tomar").isZero();
    }

    /** Sin SMTP no se da por enviado ni se deja esperando: al configurarse el correo saldrían de golpe comprobantes viejos (#218). */
    @Test void sinSmtpLoPendienteSeOmiteConElMotivoYNoSeIntenta() {
        Comprobante c = aceptadaConEnvio();
        smtp.set(false);

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.OMITIDO);
        assertThat(e.intentos()).as("no se intentó").isZero();
        assertThat(e.ultimoError()).contains("no tiene correo configurado");
        assertThat(enviados).isEmpty();
    }

    @Test void unComprobanteAnuladoAntesDeSalirSeOmite() {
        Comprobante c = aceptadaConEnvio();
        c.anular();
        repo.guardar(c);

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.OMITIDO);
        assertThat(e.ultimoError()).contains("ANULADO");
        assertThat(enviados).isEmpty();
    }

    @Test void conAdjuntosDemasiadoGrandesQuedaFallidoSinReintentos() {
        Comprobante c = aceptadaConEnvio();
        pdf = new byte[(int) EnvioAlCliente.TOPE_ADJUNTOS + 1];

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.FALLIDO);
        assertThat(e.ultimoError()).contains("tope es 7.0 MB");
        assertThat(enviados).isEmpty();
    }

    @Test void unArchivoQueNoSePuedeLeerSeReintentaComoUnFallo() {
        Comprobante c = aceptadaConEnvio();
        storage.datos.remove(c.xmlKey());

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(e.ultimoError()).isEqualTo("No se pudieron leer los archivos del comprobante para adjuntarlos").doesNotContain(c.xmlKey());
    }

    /** #325-H3: el motivo que ve el cliente es la clase de fallo; el host interno del SMTP de la plataforma queda solo en el log. */
    @Test void elMotivoGuardadoNoLlevaElHostInternoDelSmtp() {
        Comprobante c = aceptadaConEnvio();
        fallos.set(1);
        falla = () -> new CorreoNoEnviadoException(CorreoNoEnviadoException.Motivo.CONEXION, new RuntimeException("Couldn't connect to host, port: smtp.interno.khipu:587"));

        service.enviarPendientes();

        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.ultimoError()).isEqualTo("No se pudo conectar con el servidor de correo").doesNotContain("smtp.interno");
    }

    /** #325-H4: desactivar la preferencia corta también lo que ya estaba pendiente. */
    @Test void alDesactivarLaPreferenciaLoPendienteSeOmite() {
        Comprobante c = aceptadaConEnvio();
        service.fijarPreferencia(tenant, false);

        service.enviarPendientes();

        assertThat(enviados).isEmpty();
        EnvioAlCliente e = service.envioDe(tenant, c.id()).orElseThrow();
        assertThat(e.estado()).isEqualTo(Estado.OMITIDO);
        assertThat(e.ultimoError()).contains("desactivó el envío automático");
    }

    /** Si SUNAT acepta la baja en curso, el cliente habría recibido un comprobante anulado: no se manda. */
    @Test void unComprobanteConUnaBajaEnCursoSeOmite() {
        Comprobante c = aceptadaConEnvio();
        bajas.guardar(pe.factura.domain.documento.ComunicacionBaja.crear(c, 1, "Error en el monto", Fakes.CLOCK));

        service.enviarPendientes();

        assertThat(enviados).isEmpty();
        assertThat(service.envioDe(tenant, c.id()).orElseThrow().ultimoError()).contains("baja en curso");
    }

    /**
     * #325-H1: dos réplicas. Esta toma un lote de dos (bloqueo hasta t0 + 5 min). Armar los adjuntos del primero tarda 3 min y mandarlo, 2 min más (renovó su bloqueo justo antes
     * de mandar: no vence). A mitad de ese envío venció el bloqueo del segundo y la otra réplica lo retoma y lo manda. Al volver, esta réplica no lo manda otra vez: al renovar ve
     * que la marca ya no es la suya.
     */
    @Test void unEnvioQueOtraReplicaRetomoMientrasElSmtpTardabaNoSeMandaDosVeces() {
        Comprobante primero = aceptadaConEnvio();
        Comprobante segundo = Fakes.facturaFirmada(tenant, storage, 2);
        segundo.marcarEnviado();
        storage.guardar("cdr/" + segundo.id(), new byte[]{0x50, 0x4b});
        segundo.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + segundo.id());
        repo.guardar(segundo);
        envios.anotar(tenant, segundo.id(), "segundo@cliente.pe");
        EnviarAlClienteService otraReplica = new EnviarAlClienteService(envios, consultar, tenants, correo, Fakes.UOW, Fakes.CLOCK, bajas);
        java.util.concurrent.atomic.AtomicBoolean armando = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean mandando = new java.util.concurrent.atomic.AtomicBoolean();
        alGenerarPdf = id -> {
            if (id.equals(primero.id()) && armando.compareAndSet(false, true)) envios.reloj.set(envios.reloj.get().plus(Duration.ofMinutes(3)));
        };
        alEnviar = para -> {
            if (para.equals("compras@cliente.pe") && mandando.compareAndSet(false, true)) {
                envios.reloj.set(envios.reloj.get().plus(Duration.ofMinutes(2)).plusSeconds(1));
                assertThat(otraReplica.enviarPendientes()).as("la otra réplica retoma solo el segundo: el primero sigue bloqueado").isEqualTo(1);
            }
        };

        service.enviarPendientes();

        assertThat(enviados).extracting(Correo::para).containsExactly("segundo@cliente.pe", "compras@cliente.pe");
        assertThat(service.envioDe(tenant, primero.id()).orElseThrow().estado()).isEqualTo(Estado.ENVIADO);
        assertThat(service.envioDe(tenant, segundo.id()).orElseThrow().estado()).isEqualTo(Estado.ENVIADO);
    }

    @Test void laPreferenciaSeActivaSoloConCorreoConfiguradoYSiempreSeDesactiva() {
        assertThat(service.preferencia(tenant)).isEqualTo(new Preferencia(false, true));
        assertThat(service.fijarPreferencia(tenant, true)).isEqualTo(new Preferencia(true, true));

        smtp.set(false);
        assertThat(service.preferencia(tenant)).as("dice que no hay correo aunque siga activada").isEqualTo(new Preferencia(true, false));
        assertThat(service.fijarPreferencia(tenant, false)).isEqualTo(new Preferencia(false, false));
        assertThatThrownBy(() -> service.fijarPreferencia(tenant, true)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CORREO_NO_CONFIGURADO");
        assertThat(envios.automatico(tenant)).isFalse();
    }

    @Test void elEnvioDeOtraEmpresaNoSeVe() {
        Comprobante c = aceptadaConEnvio();
        assertThat(service.envioDe(UUID.randomUUID(), c.id())).isEmpty();
    }
}
