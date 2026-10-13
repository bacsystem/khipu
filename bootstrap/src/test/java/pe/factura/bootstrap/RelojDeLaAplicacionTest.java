package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #363: el reloj del backend se puede mover solo para probar plazos que dependen de la fecha (4443, cruce al 2027), y no puede quedar movido en
 * producción sin que se note: ni arranca con empresas en PRODUCCION ni emite ni envía a SUNAT para ellas.
 */
class RelojDeLaAplicacionTest {
    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final Duration TOLERANCIA = Duration.ofSeconds(5);

    // --- el valor configurado ---

    @Test void sinConfigurarNoDesplazaNada() {
        for (String vacio : new String[]{null, "", "   "})
            assertThat(RelojDeLaAplicacion.desplazamiento(vacio)).as("«%s»", vacio).isEqualTo(Duration.ZERO);
    }

    @Test void leeUnaDuracionIso8601ConSigno() {
        assertThat(RelojDeLaAplicacion.desplazamiento("-P8D")).isEqualTo(Duration.ofDays(-8));
        assertThat(RelojDeLaAplicacion.desplazamiento(" P79DT3H ")).isEqualTo(Duration.ofDays(79).plusHours(3));
        assertThat(RelojDeLaAplicacion.desplazamiento("PT6912345S")).isEqualTo(Duration.ofSeconds(6_912_345));
        assertThat(RelojDeLaAplicacion.desplazamiento("PT0S")).isEqualTo(Duration.ZERO);
    }

    @Test void unValorQueNoEsUnaDuracionImpideArrancarYDiceCuales() {
        // «P1Y» es ISO-8601 pero no una Duration (los años no tienen largo fijo); «8 dias» y «-8» son errores de dedo corrientes.
        for (String invalido : new String[]{"8 dias", "-8", "P1Y", "menos ocho dias", "P"})
            assertThatThrownBy(() -> RelojDeLaAplicacion.desplazamiento(invalido)).as(invalido)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RELOJ_DESPLAZAMIENTO").hasMessageContaining(invalido).hasMessageContaining("-P8D");
    }

    // --- el reloj ---

    @Test void sinDesplazamientoEsElRelojDelSistemaEnLaZonaConfigurada() {
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, Duration.ZERO);
        assertThat(reloj).isEqualTo(Clock.system(LIMA));
        assertThat(reloj.getZone()).isEqualTo(LIMA);
        assertThat(RelojDeLaAplicacion.desplazado(Duration.ZERO)).isFalse();
    }

    @Test void conMenosOchoDiasMarcaLaHoraDeHaceOchoDias() {
        Duration menosOcho = Duration.ofDays(-8);
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, menosOcho);
        assertThat(Duration.between(Instant.now().minus(Duration.ofDays(8)), reloj.instant()).abs()).isLessThan(TOLERANCIA);
        assertThat(reloj.getZone()).isEqualTo(LIMA);
        assertThat(RelojDeLaAplicacion.desplazado(menosOcho)).isTrue();
    }

    @Test void conDesplazamientoPositivoAdelantaElReloj() {
        Duration masMes = Duration.ofDays(79);
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, masMes);
        assertThat(Duration.between(Instant.now().plus(masMes), reloj.instant()).abs()).isLessThan(TOLERANCIA);
        assertThat(RelojDeLaAplicacion.desplazado(masMes)).isTrue();
    }

    @Test void elRelojDesplazadoSigueAndandoNoSeCongela() throws InterruptedException {
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, Duration.ofDays(-8));
        Instant antes = reloj.instant();
        Thread.sleep(20);
        assertThat(reloj.instant()).isAfter(antes);
    }

    // --- la salvaguarda al arrancar ---

    @Test void conDesplazamientoNoArrancaSiHayEmpresasEnProduccion() {
        for (Duration d : new Duration[]{Duration.ofDays(-8), Duration.ofDays(79), Duration.ofSeconds(1)})
            assertThatThrownBy(() -> RelojDeLaAplicacion.exigirSinEmpresasEnProduccion(d, 2)).as(d.toString())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RELOJ_DESPLAZAMIENTO").hasMessageContaining("PRODUCCION").hasMessageContaining("2");
    }

    @Test void conDesplazamientoArrancaSiTodasLasEmpresasSonDeBeta() {
        assertThatCode(() -> RelojDeLaAplicacion.exigirSinEmpresasEnProduccion(Duration.ofDays(-8), 0)).doesNotThrowAnyException();
    }

    @Test void sinDesplazamientoLasEmpresasEnProduccionNoImportan() {
        assertThatCode(() -> RelojDeLaAplicacion.exigirSinEmpresasEnProduccion(Duration.ZERO, 40)).doesNotThrowAnyException();
    }

    // --- el arranque: comprobar y avisar ---

    @Test void sinDesplazamientoElArranqueNoConsultaLaBaseNiAvisa() {
        List<String> avisos = new ArrayList<>();
        RelojDeLaAplicacion.alArrancar(Duration.ZERO, Clock.systemUTC(), () -> { throw new AssertionError("no debía consultar la base"); }, avisos::add);
        assertThat(avisos).isEmpty();
    }

    @Test void conDesplazamientoYSinEmpresasEnProduccionArrancaConUnWarnQueDiceLaHoraFalsa() {
        Duration menosOcho = Duration.ofDays(-8);
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, menosOcho);
        List<String> avisos = new ArrayList<>();

        RelojDeLaAplicacion.alArrancar(menosOcho, reloj, () -> 0, avisos::add);

        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).contains("RELOJ DESPLAZADO").contains("RELOJ_DESPLAZAMIENTO=" + menosOcho).contains("America/Lima")
                .contains("-8 d 0 h 0 min 0 s").contains("SOLO PARA PRUEBAS").contains(String.valueOf(reloj.instant().atZone(LIMA).getYear()));
    }

    @Test void conDesplazamientoYEmpresasEnProduccionElArranqueAbortaSinAvisarComoSiTodoEstuvieraBien() {
        Duration menosOcho = Duration.ofDays(-8);
        List<String> avisos = new ArrayList<>();
        assertThatThrownBy(() -> RelojDeLaAplicacion.alArrancar(menosOcho, RelojDeLaAplicacion.crear(LIMA, menosOcho), () -> 1, avisos::add))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRODUCCION");
        assertThat(avisos).isEmpty();
    }

    // --- la salvaguarda en marcha: una empresa pasada a PRODUCCION después de arrancar ---

    @Test void sinDesplazamientoNoSeInterponeNada() {
        EmitirComprobanteUseCase emision = new EmisionFalsa();
        SunatBillingGateway sunat = new SunatFalsa();
        assertThat(RelojDeLaAplicacion.proteger(emision, new TenantsFalsos(Entorno.PRODUCCION), Duration.ZERO)).isSameAs(emision);
        assertThat(RelojDeLaAplicacion.proteger(sunat, Duration.ZERO)).isSameAs(sunat);
    }

    @Test void conDesplazamientoNoEmiteParaUnaEmpresaEnProduccion() {
        EmisionFalsa real = new EmisionFalsa();
        EmitirComprobanteUseCase protegida = RelojDeLaAplicacion.proteger(real, new TenantsFalsos(Entorno.PRODUCCION), Duration.ofDays(-8));

        assertThatThrownBy(() -> protegida.emitirFactura(TenantsFalsos.ID, (EmitirFacturaCommand) null, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"))
                .hasMessageContaining("RELOJ_DESPLAZAMIENTO");
        assertThatThrownBy(() -> protegida.emitirNota(TenantsFalsos.ID, (EmitirNotaCommand) null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThat(real.llamadas).as("no llegó a numerar ni firmar nada").isEmpty();
    }

    @Test void conDesplazamientoEmiteNormalParaUnaEmpresaDeBeta() {
        EmisionFalsa real = new EmisionFalsa();
        EmitirComprobanteUseCase protegida = RelojDeLaAplicacion.proteger(real, new TenantsFalsos(Entorno.BETA), Duration.ofDays(-8));

        protegida.emitirFactura(TenantsFalsos.ID, (EmitirFacturaCommand) null, null);
        protegida.emitirNota(TenantsFalsos.ID, (EmitirNotaCommand) null);
        assertThat(real.llamadas).containsExactly("emitirFactura", "emitirNota");
    }

    @Test void conDesplazamientoUnaEmpresaQueNoExisteLaResuelveElServicioNoLaSalvaguarda() {
        EmisionFalsa real = new EmisionFalsa();
        EmitirComprobanteUseCase protegida = RelojDeLaAplicacion.proteger(real, new TenantsFalsos(null), Duration.ofDays(-8));

        protegida.emitirNota(TenantsFalsos.ID, (EmitirNotaCommand) null);
        assertThat(real.llamadas).containsExactly("emitirNota");
    }

    @Test void conDesplazamientoNoSeEnvianALaSunatDeProduccionPeroSiALaDeBeta() {
        SunatFalsa real = new SunatFalsa();
        SunatBillingGateway protegida = RelojDeLaAplicacion.proteger(real, Duration.ofDays(-8));
        Tenant produccion = tenant(Entorno.PRODUCCION);
        Tenant beta = tenant(Entorno.BETA);

        assertThatThrownBy(() -> protegida.sendBill(produccion, "F001-1", new byte[0]))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThatThrownBy(() -> protegida.sendSummary(produccion, "RC-1", new byte[0])).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> protegida.getStatus(produccion, "123")).isInstanceOf(DomainException.class);
        assertThat(real.llamadas).as("ni un byte salió hacia SUNAT").isEmpty();

        protegida.sendBill(beta, "B001-1", new byte[0]);
        protegida.sendSummary(beta, "RC-1", new byte[0]);
        protegida.getStatus(beta, "123");
        assertThat(real.llamadas).containsExactly("sendBill", "sendSummary", "getStatus");
    }

    // --- dobles ---

    static Tenant tenant(Entorno entorno) { return new Tenant(TenantsFalsos.ID, "20123456786", "Empresa SAC", entorno, null, null); }

    static class EmisionFalsa implements EmitirComprobanteUseCase {
        final List<String> llamadas = new ArrayList<>();
        @Override public Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia idempotencia) { llamadas.add("emitirFactura"); return null; }
        @Override public Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd) { llamadas.add("emitirNota"); return null; }
    }

    static class SunatFalsa implements SunatBillingGateway {
        final List<String> llamadas = new ArrayList<>();
        @Override public byte[] sendBill(Tenant t, String nombre, byte[] xml) { llamadas.add("sendBill"); return new byte[0]; }
        @Override public String sendSummary(Tenant t, String nombre, byte[] xml) { llamadas.add("sendSummary"); return "ticket"; }
        @Override public EstadoTicket getStatus(Tenant t, String ticket) { llamadas.add("getStatus"); return new EstadoTicket("98", null); }
    }

    /** Un repositorio con a lo sumo una empresa, de ese entorno ({@code null}: ninguna). */
    static class TenantsFalsos implements TenantRepository {
        static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000363");
        private final Entorno entorno;
        TenantsFalsos(Entorno entorno) { this.entorno = entorno; }
        @Override public Optional<Tenant> buscar(UUID id) { return entorno == null ? Optional.empty() : Optional.of(tenant(entorno)); }
        @Override public void guardar(Tenant t) { throw new AssertionError(); }
        @Override public Optional<Tenant> buscarPorRuc(String ruc) { throw new AssertionError(); }
        @Override public List<Tenant> listarPorCuenta(UUID cuentaId) { throw new AssertionError(); }
        @Override public void asignarCuenta(UUID tenantId, UUID cuentaId) { throw new AssertionError(); }
        @Override public Optional<UUID> cuentaDe(UUID tenantId) { throw new AssertionError(); }
    }
}
