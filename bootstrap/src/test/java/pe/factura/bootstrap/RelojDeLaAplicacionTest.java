package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatConsultaGateway;
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

    @Test void elWarnDiceLaHoraEnLaZonaDelBackendConSuDesfaseYNoUnInstanteEnUtcEtiquetadoComoLima() {
        Duration menosOcho = Duration.ofDays(-8);
        Clock reloj = RelojDeLaAplicacion.crear(LIMA, menosOcho);
        List<String> avisos = new ArrayList<>();

        RelojDeLaAplicacion.alArrancar(menosOcho, reloj, () -> 0, avisos::add);

        // Lima es UTC-5 todo el año: la hora local lleva el desfase -05:00 y el instante UTC («…Z») no aparece.
        assertThat(avisos.get(0)).contains("-05:00").doesNotContainPattern("\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?Z");
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

    /** #355: la protección del reloj no se traga la clave de idempotencia de la nota, ni la deja pasar en producción. */
    @Test void conDesplazamientoLaClaveDeLaNotaLlegaAlServicio() {
        EmisionFalsa real = new EmisionFalsa();
        Idempotencia clave = new Idempotencia("c1a7e5b0-0000-4000-8000-000000000355", "huella");

        RelojDeLaAplicacion.proteger(real, new TenantsFalsos(Entorno.BETA), Duration.ofDays(-8)).emitirNota(TenantsFalsos.ID, null, clave);
        assertThat(real.ultimaClaveDeNota).isSameAs(clave);

        EmisionFalsa enProduccion = new EmisionFalsa();
        assertThatThrownBy(() -> RelojDeLaAplicacion.proteger(enProduccion, new TenantsFalsos(Entorno.PRODUCCION), Duration.ofDays(-8)).emitirNota(TenantsFalsos.ID, null, clave))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThat(enProduccion.llamadas).isEmpty();
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

    // --- el resto de lo que usa el reloj con una empresa: envío, resúmenes y bajas, barrido de plazos, consultas ---

    @Test void sinDesplazamientoTampocoSeInterponeEnElResto() {
        EnviarDocumentoUseCase envio = (tenantId, comprobanteId) -> null;
        TenantRepository tenants = new TenantsFalsos(Entorno.PRODUCCION);
        ControlarPlazoEnvioUseCase plazos = List::of;
        SunatConsultaGateway consultas = new ConsultaFalsa();
        assertThat(RelojDeLaAplicacion.proteger(envio, tenants, Duration.ZERO)).isSameAs(envio);
        assertThat(RelojDeLaAplicacion.proteger(tenants, Duration.ZERO)).isSameAs(tenants);
        assertThat(RelojDeLaAplicacion.proteger(plazos, () -> { throw new AssertionError("no debía consultar la base"); }, a -> { throw new AssertionError(); }, Duration.ZERO)).isSameAs(plazos);
        assertThat(RelojDeLaAplicacion.proteger(consultas, Duration.ZERO)).isSameAs(consultas);
    }

    /** H2: el envío decide por plazo (y lo cierra, estado terminal) con el reloj ANTES de llegar a la pasarela, así que la pasarela sola no basta. */
    @Test void conDesplazamientoNoEnviaNiCierraPorPlazoParaUnaEmpresaEnProduccion() {
        List<String> llamadas = new ArrayList<>();
        EnviarDocumentoUseCase real = (tenantId, comprobanteId) -> { llamadas.add("enviar"); return null; };

        EnviarDocumentoUseCase enProduccion = RelojDeLaAplicacion.proteger(real, new TenantsFalsos(Entorno.PRODUCCION), Duration.ofDays(-8));
        assertThatThrownBy(() -> enProduccion.enviar(TenantsFalsos.ID, UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThat(llamadas).isEmpty();

        RelojDeLaAplicacion.proteger(real, new TenantsFalsos(Entorno.BETA), Duration.ofDays(-8)).enviar(TenantsFalsos.ID, UUID.randomUUID());
        RelojDeLaAplicacion.proteger(real, new TenantsFalsos(null), Duration.ofDays(-8)).enviar(TenantsFalsos.ID, UUID.randomUUID());
        assertThat(llamadas).containsExactly("enviar", "enviar");
    }

    /** H1: las bajas y los resúmenes cargan la empresa lo primero; si es de PRODUCCION con el reloj movido no se sigue (ni se numera ni se firma nada). */
    @Test void conDesplazamientoLaBusquedaDeUnaEmpresaEnProduccionFallaYLasDemasPasan() {
        assertThatThrownBy(() -> RelojDeLaAplicacion.proteger(new TenantsFalsos(Entorno.PRODUCCION), Duration.ofDays(-8)).buscar(TenantsFalsos.ID))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThat(RelojDeLaAplicacion.proteger(new TenantsFalsos(Entorno.BETA), Duration.ofDays(-8)).buscar(TenantsFalsos.ID)).isPresent();
        assertThat(RelojDeLaAplicacion.proteger(new TenantsFalsos(null), Duration.ofDays(-8)).buscar(TenantsFalsos.ID)).isEmpty();
    }

    @Test void conDesplazamientoElRestoDeLosMetodosDelRepositorioDeEmpresasPasaTalCual() {
        TenantRepository real = org.mockito.Mockito.mock(TenantRepository.class);
        UUID cuenta = UUID.randomUUID();
        org.mockito.Mockito.when(real.listarPorCuenta(cuenta)).thenReturn(List.of(tenant(Entorno.PRODUCCION)));
        org.mockito.Mockito.when(real.buscarPorRuc("20123456786")).thenReturn(Optional.of(tenant(Entorno.PRODUCCION)));
        TenantRepository protegido = RelojDeLaAplicacion.proteger(real, Duration.ofDays(-8));

        assertThat(protegido.listarPorCuenta(cuenta)).hasSize(1);
        assertThat(protegido.buscarPorRuc("20123456786")).isPresent();
        protegido.guardar(tenant(Entorno.BETA));
        protegido.asignarCuenta(TenantsFalsos.ID, cuenta);
        protegido.cuentaDe(TenantsFalsos.ID);
        org.mockito.Mockito.verify(real).guardar(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(real).asignarCuenta(TenantsFalsos.ID, cuenta);
        org.mockito.Mockito.verify(real).cuentaDe(TenantsFalsos.ID);
    }

    /** H3: el barrido cierra por plazo lo de todas las empresas con el reloj movido; con una en PRODUCCION no corre y lo dice. */
    @Test void conDesplazamientoElBarridoDePlazosNoCorreSiHayEmpresasEnProduccion() {
        List<String> corridas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        ControlarPlazoEnvioUseCase real = () -> { corridas.add("corrio"); return List.of(); };

        assertThat(RelojDeLaAplicacion.proteger(real, () -> 2, avisos::add, Duration.ofDays(79)).marcarVencidos()).isEmpty();
        assertThat(corridas).isEmpty();
        assertThat(avisos).singleElement().asString().contains("RELOJ_DESPLAZAMIENTO").contains("2").contains("PRODUCCION");

        avisos.clear();
        RelojDeLaAplicacion.proteger(real, () -> 0, avisos::add, Duration.ofDays(79)).marcarVencidos();
        assertThat(corridas).containsExactly("corrio");
        assertThat(avisos).isEmpty();
    }

    /** Las consultas de SUNAT (getStatusCdr, getStatus, validez) solo existen en producción y con las credenciales SOL de la empresa. */
    @Test void conDesplazamientoNoSeConsultaALaSunatDeProduccionPeroSiAlResto() {
        ConsultaFalsa real = new ConsultaFalsa();
        SunatConsultaGateway protegida = RelojDeLaAplicacion.proteger(real, Duration.ofDays(-8));
        Tenant produccion = tenant(Entorno.PRODUCCION);

        assertThatThrownBy(() -> protegida.getStatusCdr(produccion, "20123456786", "01", "F001", 1))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThatThrownBy(() -> protegida.getStatus(produccion, "20123456786", "01", "F001", 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> protegida.validar(produccion, "20123456786", "01", "F001", 1, "6", "20601234565", java.time.LocalDate.of(2026, 10, 1), java.math.BigDecimal.TEN))
                .isInstanceOf(DomainException.class);
        assertThat(real.llamadas).as("ni una consulta salió hacia SUNAT").isEmpty();

        Tenant beta = tenant(Entorno.BETA);
        protegida.getStatusCdr(beta, "20123456786", "01", "F001", 1);
        protegida.getStatus(beta, "20123456786", "01", "F001", 1);
        protegida.validar(beta, "20123456786", "01", "F001", 1, "6", "20601234565", java.time.LocalDate.of(2026, 10, 1), java.math.BigDecimal.TEN);
        assertThat(real.llamadas).containsExactly("getStatusCdr", "getStatus", "validar");
    }

    // --- el tope del desplazamiento ---

    /** Una duración ISO-8601 válida puede ser absurda (P999999999D): el reloj se rompería en el primer uso, lejos de la variable. Se acota al configurar. */
    @Test void unDesplazamientoDemasiadoGrandeImpideArrancarYDiceElTope() {
        for (String absurdo : new String[]{"P999999999D", "-P40000D", "PT999999999999S"})
            assertThatThrownBy(() -> RelojDeLaAplicacion.desplazamiento(absurdo)).as(absurdo)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("RELOJ_DESPLAZAMIENTO").hasMessageContaining("3650");
        assertThat(RelojDeLaAplicacion.desplazamiento("P3650D")).isEqualTo(Duration.ofDays(3650));
        assertThat(RelojDeLaAplicacion.desplazamiento("-P3650D")).isEqualTo(Duration.ofDays(-3650));
    }

    // --- dobles ---

    static class ConsultaFalsa implements SunatConsultaGateway {
        final List<String> llamadas = new ArrayList<>();
        @Override public Consulta getStatusCdr(Tenant t, String ruc, String tipo, String serie, long numero) { llamadas.add("getStatusCdr"); return new Consulta("0001", null, null); }
        @Override public Consulta getStatus(Tenant t, String ruc, String tipo, String serie, long numero) { llamadas.add("getStatus"); return new Consulta("0001", null, null); }
        @Override public Consulta validar(Tenant t, String ruc, String tipo, String serie, long numero, String tipoDoc, String numDoc, java.time.LocalDate fecha, java.math.BigDecimal importe) {
            llamadas.add("validar"); return new Consulta("0001", null, null);
        }
    }

    static Tenant tenant(Entorno entorno) { return new Tenant(TenantsFalsos.ID, "20123456786", "Empresa SAC", entorno, null, null); }

    static class EmisionFalsa implements EmitirComprobanteUseCase {
        final List<String> llamadas = new ArrayList<>();
        @Override public Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia idempotencia) { llamadas.add("emitirFactura"); return null; }
        Idempotencia ultimaClaveDeNota;
        @Override public Emision emitirNota(UUID tenantId, EmitirNotaCommand cmd, Idempotencia idempotencia) { llamadas.add("emitirNota"); ultimaClaveDeNota = idempotencia; return new Emision(null, false); }
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
