package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.EnvioAlClienteRepository.Tomado;
import pe.factura.domain.documento.*;
import pe.factura.domain.documento.EnvioAlCliente.Estado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El envío automático del comprobante aceptado a su cliente (#325) con Postgres real: que el paso a aceptado deje el envío en la misma transacción solo si la empresa lo tiene
 * activado y el comprobante trae correo, que nunca haya dos por comprobante, y que la toma con su marca de bloqueo no deje a dos instancias con el mismo envío.
 */
class JdbcEnvioAlClienteRepositoryTest extends PersistenciaTestBase {
    static final Duration BLOQUEO = Duration.ofMinutes(10);
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");

    JdbcEnvioAlClienteRepository repo = new JdbcEnvioAlClienteRepository(jdbc);
    JdbcComprobanteRepository comprobantes = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    private Comprobante firmada(UUID t, long numero, String correo) {
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
        c.dirigirA(correo);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k.xml");
        comprobantes.guardar(c);
        return c;
    }

    /** Lo que pasa al recibir el CDR: ENVIADO y luego ACEPTADO, guardado como lo guarda el envío. */
    private Comprobante aceptar(Comprobante c) {
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + c.id());
        uow.ejecutar(() -> comprobantes.guardar(c));
        return c;
    }

    private int filas() { return jdbc.queryForObject("SELECT count(*) FROM envio_al_cliente", Integer.class); }

    private List<Tomado> tomar(int limite) { return uow.ejecutar(() -> repo.tomarVencidos(limite, BLOQUEO)); }

    @Test void elCorreoDelClienteSeGuardaConElComprobante() {
        UUID t = tenantDePrueba();
        Comprobante c = firmada(t, 1, "Compras@Cliente.pe");
        Comprobante sin = firmada(t, 2, null);

        assertThat(comprobantes.buscar(t, c.id()).orElseThrow().correoCliente()).isEqualTo("compras@cliente.pe");
        assertThat(comprobantes.buscar(t, sin.id()).orElseThrow().correoCliente()).isNull();
        assertThat(comprobantes.listar(t, pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro.NINGUNO, 1, 10))
                .extracting(Comprobante::correoCliente).containsExactlyInAnyOrder("compras@cliente.pe", null);
    }

    @Test void laPreferenciaEstaApagadaPorDefectoYSeGuarda() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        assertThat(repo.automatico(t)).isFalse();

        repo.fijarAutomatico(t, true);

        assertThat(repo.automatico(t)).isTrue();
        assertThat(repo.automatico(otra)).as("de una empresa a otra no se mezcla").isFalse();
        repo.fijarAutomatico(t, false);
        assertThat(repo.automatico(t)).isFalse();
        assertThat(repo.automatico(UUID.randomUUID())).as("una empresa que no existe").isFalse();
    }

    @Test void alAceptarseConLaPreferenciaActivadaYCorreoSeAnotaUnEnvioPendiente() {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        Comprobante c = firmada(t, 1, "compras@cliente.pe");
        assertThat(filas()).as("firmado y enviado no anotan nada").isZero();

        aceptar(c);

        EnvioAlCliente e = repo.deComprobante(t, c.id()).orElseThrow();
        assertThat(e.destinatario()).isEqualTo("compras@cliente.pe");
        assertThat(e.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(e.intentos()).isZero();
        assertThat(repo.deComprobante(tenantDePrueba(), c.id())).as("otra empresa no lo ve").isEmpty();
    }

    @Test void sinPreferenciaOSinCorreoNoSeAnotaNada() {
        UUID apagada = tenantDePrueba(), activada = tenantDePrueba();
        repo.fijarAutomatico(activada, true);

        aceptar(firmada(apagada, 1, "compras@cliente.pe"));
        aceptar(firmada(activada, 1, null));

        assertThat(filas()).isZero();
    }

    @Test void nuncaHayDosEnviosPorComprobante() {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        Comprobante c = aceptar(firmada(t, 1, "compras@cliente.pe"));

        // Una segunda aceptación del mismo comprobante (otra réplica que guardó el mismo paso) choca con la clave primaria y no anota otro.
        jdbc.update("INSERT INTO envio_al_cliente (documento_id, tenant_id, destinatario) VALUES (?, ?, 'otro@cliente.pe') ON CONFLICT (documento_id) DO NOTHING", c.id(), t);
        c.anular();
        uow.ejecutar(() -> comprobantes.guardar(c));

        assertThat(filas()).isEqualTo(1);
        assertThat(repo.deComprobante(t, c.id()).orElseThrow().destinatario()).isEqualTo("compras@cliente.pe");
    }

    @Test void siElCambioDeEstadoNoSeGuardaTampocoElEnvio() {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        Comprobante c = firmada(t, 1, "compras@cliente.pe");
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + c.id());

        try {
            uow.ejecutar(() -> {
                comprobantes.guardar(c);
                throw new IllegalStateException("se cae la transacción");
            });
        } catch (IllegalStateException esperado) {
            // el cambio de estado se deshizo
        }

        assertThat(filas()).isZero();
    }

    @Test void tomarEnviarYFallarSoloValenConLaMarcaVigente() {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        Comprobante c = aceptar(firmada(t, 1, "compras@cliente.pe"));

        Tomado tomado = tomar(10).get(0);
        assertThat(tomado.documentoId()).isEqualTo(c.id());
        assertThat(tomado.tenantId()).isEqualTo(t);
        assertThat(tomar(10)).as("tomado: otra pasada no lo ve").isEmpty();

        Tomado viejo = new Tomado(t, c.id(), tomado.destinatario(), 0, tomado.bloqueo().minusSeconds(1));
        assertThat(uow.ejecutar(() -> repo.registrarEnvio(viejo, AHORA))).as("con otra marca no se anota").isFalse();

        Instant reintento = Instant.now().plus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertThat(uow.ejecutar(() -> repo.registrarFallo(tomado, "SMTP caído", AHORA, reintento))).isTrue();
        EnvioAlCliente fallo = repo.deComprobante(t, c.id()).orElseThrow();
        assertThat(fallo.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(fallo.intentos()).isEqualTo(1);
        assertThat(fallo.ultimoError()).isEqualTo("SMTP caído");
        assertThat(fallo.siguienteIntento()).isEqualTo(reintento);
        assertThat(tomar(10)).as("su reintento todavía no llega").isEmpty();

        jdbc.update("UPDATE envio_al_cliente SET siguiente_intento = now() - interval '1 second'");
        Tomado otraVez = tomar(10).get(0);
        assertThat(otraVez.intentos()).isEqualTo(1);
        assertThat(uow.ejecutar(() -> repo.registrarEnvio(otraVez, AHORA))).isTrue();
        EnvioAlCliente enviado = repo.deComprobante(t, c.id()).orElseThrow();
        assertThat(enviado.estado()).isEqualTo(Estado.ENVIADO);
        assertThat(enviado.intentos()).isEqualTo(2);
        assertThat(enviado.ultimoError()).isNull();
        assertThat(enviado.enviadoEn()).isEqualTo(AHORA);
        assertThat(uow.ejecutar(() -> repo.registrarFallo(otraVez, "tarde", AHORA, null))).as("lo enviado ya no cambia").isFalse();
        jdbc.update("UPDATE envio_al_cliente SET siguiente_intento = now() - interval '1 second'");
        assertThat(tomar(10)).as("lo enviado no se vuelve a tomar").isEmpty();
    }

    @Test void sinSiguienteIntentoQuedaFallidoYOmitirNoCuentaComoIntento() {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        Comprobante a = aceptar(firmada(t, 1, "a@cliente.pe"));
        Comprobante b = aceptar(firmada(t, 2, "b@cliente.pe"));
        List<Tomado> tomados = tomar(10);
        Tomado ta = tomados.stream().filter(x -> x.documentoId().equals(a.id())).findFirst().orElseThrow();
        Tomado tb = tomados.stream().filter(x -> x.documentoId().equals(b.id())).findFirst().orElseThrow();

        uow.ejecutar(() -> repo.registrarFallo(ta, "x".repeat(400), AHORA, null));
        uow.ejecutar(() -> repo.omitir(tb, "sin SMTP", AHORA));

        EnvioAlCliente fa = repo.deComprobante(t, a.id()).orElseThrow();
        assertThat(fa.estado()).isEqualTo(Estado.FALLIDO);
        assertThat(fa.ultimoError()).as("el error se recorta a la columna").hasSize(300);
        EnvioAlCliente fb = repo.deComprobante(t, b.id()).orElseThrow();
        assertThat(fb.estado()).isEqualTo(Estado.OMITIDO);
        assertThat(fb.intentos()).isZero();
        assertThat(fb.ultimoError()).isEqualTo("sin SMTP");
    }

    /** Dos réplicas que toman a la vez: cada envío lo toma una sola (FOR UPDATE SKIP LOCKED más la marca), nunca las dos. */
    @Test void dosInstanciasQueTomanALaVezNoSeRepartenElMismoEnvio() throws Exception {
        UUID t = tenantDePrueba();
        repo.fijarAutomatico(t, true);
        for (int n = 1; n <= 30; n++) aceptar(firmada(t, n, "c" + n + "@cliente.pe"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<List<Tomado>>> tareas = new ArrayList<>();
            for (int i = 0; i < 2; i++) tareas.add(pool.submit(() -> { salida.await(); return tomar(20); }));
            salida.countDown();
            List<UUID> todos = new ArrayList<>();
            for (Future<List<Tomado>> f : tareas) f.get(30, TimeUnit.SECONDS).forEach(x -> todos.add(x.documentoId()));
            assertThat(todos).doesNotHaveDuplicates().hasSize(30);
        } finally {
            pool.shutdownNow();
        }
    }
}
