package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Paso;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.AdministradorTokenEmisor;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.SegundoFactor;
import pe.factura.application.port.out.SegundoFactorRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.Administrador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Login del backoffice en dos pasos (#177): sin segundo factor no hay sesión. */
class AutenticarAdministradorServiceTest {
    // --- Fakes ---------------------------------------------------------------------------------------------------------------
    final Map<UUID, Administrador> admins = new HashMap<>();
    final AdministradorRepository administradores = new AdministradorRepository() {
        public void guardar(Administrador a) { admins.put(a.id(), a); }
        public Optional<Administrador> buscar(UUID id) { return Optional.ofNullable(admins.get(id)); }
        public Optional<Administrador> buscarPorEmail(String e) { return admins.values().stream().filter(a -> a.email().equals(e)).findFirst(); }
    };
    final PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    final AdministradorTokenEmisor tokens = new AdministradorTokenEmisor() {
        public String emitir(Claims c) { return "jwt:" + c.administradorId() + ":" + c.email(); }
        public Optional<Claims> verificar(String t) { return Optional.empty(); }
        public long vidaSesionSegundos() { return 900; }
        public String emitirDesafio(UUID id) { return "desafio:" + id; }
        public Optional<UUID> verificarDesafio(String t) {
            return t != null && t.startsWith("desafio:") ? Optional.of(UUID.fromString(t.substring(8))) : Optional.empty();
        }
    };
    /** «Cifra» invirtiendo los bytes: basta para comprobar que no se guarda el secreto en claro. */
    final SecretCipher cifrador = new SecretCipher() {
        public byte[] cifrar(byte[] p) { return invertir(p); }
        public byte[] descifrar(byte[] c) { return invertir(c); }
    };
    /** TOTP de mentira pero con las mismas reglas: un código por paso de 30 s, y se acepta un paso de reloj hacia cada lado. */
    int secretosEmitidos;
    final SegundoFactor totp = new SegundoFactor() {
        public String nuevoSecreto() { return "SECRETO" + (++secretosEmitidos); }
        public String uri(String s, String cuenta) { return "otpauth://totp/khipu:" + cuenta + "?secret=" + s; }
        public OptionalLong paso(String s, String codigo, Instant ahora) {
            long p = ahora.getEpochSecond() / 30;
            for (long q = p - 1; q <= p + 1; q++) if (codigoDe(s, q).equals(codigo)) return OptionalLong.of(q);
            return OptionalLong.empty();
        }
    };
    final Factores factores = new Factores();
    final Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    final Fakes.Auditoria auditoria = new Fakes.Auditoria();
    final RelojMovil reloj = new RelojMovil(Instant.parse("2026-10-03T15:00:00Z"));
    final Fakes.Intentos intentos = new Fakes.Intentos();
    final LimiteDeIntentos limite = new LimiteDeIntentos(intentos, reloj);
    final AutenticarAdministradorService service = new AutenticarAdministradorService(
            administradores, hasher, tokens, factores, totp, cifrador, uri -> ("QR:" + uri).getBytes(StandardCharsets.UTF_8), uow, auditoria, reloj, limite);

    { auditoria.uow = uow; }

    final Administrador ana = admin("ana@khipu.pe", true);

    // --- Login ---------------------------------------------------------------------------------------------------------------

    @Test void laContrasenaSolaNoDaSesionSinoUnDesafio() {
        var d = service.login("ANA@khipu.pe", "Segura123", null);
        assertThat(d.token()).isEqualTo("desafio:" + ana.id());
        assertThat(d.paso()).as("sin segundo factor, lo primero es configurarlo").isEqualTo(Paso.CONFIGURAR_SEGUNDO_FACTOR);
    }

    @Test void conElSegundoFactorConfiguradoElDesafioPideVerificarlo() {
        configurado(ana);
        assertThat(service.login("ana@khipu.pe", "Segura123", null).paso()).isEqualTo(Paso.VERIFICAR_SEGUNDO_FACTOR);
    }

    @Test void unSecretoPendienteSinConfirmarSigueSiendoConfigurar() {
        service.configurarSegundoFactor(service.login("ana@khipu.pe", "Segura123", null).token());
        assertThat(service.login("ana@khipu.pe", "Segura123", null).paso()).isEqualTo(Paso.CONFIGURAR_SEGUNDO_FACTOR);
    }

    @Test void credencialesInvalidasOInactivoFallanIgual() {
        admin("baja@khipu.pe", false);
        assertThatThrownBy(() -> service.login("ana@khipu.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("nadie@khipu.pe", "Segura123", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("baja@khipu.pe", "Segura123", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    /** H1 (revisión de la PR #262): un carácter de control delante del correo no abre otro contador para la contraseña del administrador. */
    @Test void unCaracterDeControlDelanteDelCorreoNoAbreOtroContador() {
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login("ana@khipu.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("\u0001ana@khipu.pe", "Segura123", null)).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");
    }

    /** #261: la contraseña del backoffice tampoco se puede probar sin freno, aunque detrás haya segundo factor. */
    @Test void trasCincoContrasenasErroneasNiLaCorrectaDaDesafio() {
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login("ana@khipu.pe", "otra", "203.0.113.9")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("ana@khipu.pe", "Segura123", "203.0.113.9")).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");

        reloj.avanzar(java.time.Duration.ofMinutes(15));
        assertThat(service.login("ana@khipu.pe", "Segura123", "203.0.113.9").token()).isEqualTo("desafio:" + ana.id());
    }

    // --- Configurar ----------------------------------------------------------------------------------------------------------

    @Test void configurarDevuelveSecretoUriYQrYGuardaElSecretoCifrado() {
        var c = service.configurarSegundoFactor(desafio(ana));

        assertThat(c.secreto()).isEqualTo("SECRETO1");
        assertThat(c.uri()).isEqualTo("otpauth://totp/khipu:ana@khipu.pe?secret=SECRETO1");
        assertThat(new String(c.qrPng(), StandardCharsets.UTF_8)).isEqualTo("QR:" + c.uri());
        var e = factores.estados.get(ana.id());
        assertThat(e.confirmado()).isFalse();
        assertThat(e.secretoCifrado()).as("nunca en claro").isNotEqualTo("SECRETO1".getBytes(StandardCharsets.UTF_8))
                .isEqualTo(invertir("SECRETO1".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void confirmarConElCodigoCorrectoDaSesionYCodigosDeRecuperacion() {
        String d = desafio(ana);
        var c = service.configurarSegundoFactor(d);

        var nueva = service.confirmarSegundoFactor(d, codigoDe(c.secreto(), pasoActual()), "203.0.113.7");

        assertThat(nueva.sesion().accessToken()).isEqualTo("jwt:" + ana.id() + ":ana@khipu.pe");
        assertThat(nueva.sesion().expiraEnSegundos()).isEqualTo(900);
        assertThat(nueva.codigosRecuperacion()).hasSize(10).doesNotHaveDuplicates()
                .allMatch(x -> x.matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"), "formato XXXXX-XXXXX sin 0/O ni 1/I");
        assertThat(factores.estados.get(ana.id()).confirmado()).isTrue();
        assertThat(factores.codigos.get(ana.id()).keySet()).as("solo los hashes")
                .containsExactlyInAnyOrderElementsOf(nueva.codigosRecuperacion().stream().map(AutenticarAdministradorServiceTest::sha256).toList());
    }

    @Test void confirmarDejaUnRegistroEnLaBitacoraDentroDeLaTransaccion() {
        String d = desafio(ana);
        var c = service.configurarSegundoFactor(d);
        service.confirmarSegundoFactor(d, codigoDe(c.secreto(), pasoActual()), "203.0.113.7");

        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo(AccionAdmin.CONFIGURAR_SEGUNDO_FACTOR);
            assertThat(r.actor()).isEqualTo(ActorAdmin.administrador(ana.id(), "203.0.113.7"));
            assertThat(r.detalle()).doesNotContain(c.secreto());
        });
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
        assertThat(factores.confirmadoDentro).containsExactly(true);
    }

    @Test void confirmarConUnCodigoEquivocadoNoConfirma() {
        String d = desafio(ana);
        service.configurarSegundoFactor(d);
        assertThatThrownBy(() -> service.confirmarSegundoFactor(d, "000000", "203.0.113.7")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
        assertThat(factores.estados.get(ana.id()).confirmado()).isFalse();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void confirmarSinHaberPedidoUnSecretoFalla() {
        assertThatThrownBy(() -> service.confirmarSegundoFactor(desafio(ana), "123456", "203.0.113.7"))
                .extracting("codigo").isEqualTo("SEGUNDO_FACTOR_NO_CONFIGURADO");
    }

    /** Con el segundo factor ya confirmado, la contraseña sola no puede reemplazarlo por uno del atacante. */
    @Test void conElSegundoFactorYaConfiguradoNoSePuedeReconfigurar() {
        configurado(ana);
        String d = desafio(ana);
        assertThatThrownBy(() -> service.configurarSegundoFactor(d)).extracting("codigo").isEqualTo("SEGUNDO_FACTOR_YA_CONFIGURADO");
        assertThatThrownBy(() -> service.confirmarSegundoFactor(d, "123456", "203.0.113.7")).extracting("codigo").isEqualTo("SEGUNDO_FACTOR_YA_CONFIGURADO");
    }

    @Test void pedirOtroSecretoAntesDeConfirmarReemplazaAlAnterior() {
        String d = desafio(ana);
        var primero = service.configurarSegundoFactor(d);
        var segundo = service.configurarSegundoFactor(d);
        assertThat(segundo.secreto()).isNotEqualTo(primero.secreto());
        assertThatThrownBy(() -> service.confirmarSegundoFactor(d, codigoDe(primero.secreto(), pasoActual()), "203.0.113.7"))
                .extracting("codigo").isEqualTo("CODIGO_INVALIDO");
        assertThat(service.confirmarSegundoFactor(d, codigoDe(segundo.secreto(), pasoActual()), "203.0.113.7").sesion()).isNotNull();
    }

    // --- Verificar -----------------------------------------------------------------------------------------------------------

    @Test void verificarConElCodigoDeLaAppDaSesionYLaRegistra() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));

        var s = service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4");

        assertThat(s.accessToken()).isEqualTo("jwt:" + ana.id() + ":ana@khipu.pe");
        assertThat(s.expiraEnSegundos()).isEqualTo(900);
        assertThat(s.administrador().id()).isEqualTo(ana.id());
        assertThat(auditoria.registros).last().satisfies(r -> {
            assertThat(r.accion()).isEqualTo(AccionAdmin.INICIAR_SESION);
            assertThat(r.actor()).isEqualTo(ActorAdmin.administrador(ana.id(), "198.51.100.4"));
            assertThat(r.detalle()).isEqualTo("segundo_factor=app");
        });
        assertThat(auditoria.dentroAlRegistrar).last().isEqualTo(true);
    }

    @Test void seAceptaUnPasoDeRelojHaciaCadaLado() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual() - 1), "198.51.100.4")).isNotNull();
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual() + 1), "198.51.100.4")).isNotNull();
    }

    /** Un código visto por encima del hombro (o interceptado) no sirve para un segundo login en la misma ventana. */
    @Test void unCodigoYaUsadoNoSeAceptaDosVeces() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        String codigo = codigoDe(secreto, pasoActual());
        service.verificarSegundoFactor(desafio(ana), codigo, "198.51.100.4");

        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), codigo, "198.51.100.4")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
    }

    @Test void elCodigoConElQueSeConfiguroTampocoSirveParaElLoginSiguiente() {
        String d = desafio(ana);
        var c = service.configurarSegundoFactor(d);
        String codigo = codigoDe(c.secreto(), pasoActual());
        service.confirmarSegundoFactor(d, codigo, "203.0.113.7");

        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), codigo, "203.0.113.7")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
    }

    @Test void verificarSinSegundoFactorConfiguradoFalla() {
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), "123456", "198.51.100.4"))
                .extracting("codigo").isEqualTo("SEGUNDO_FACTOR_NO_CONFIGURADO");
        service.configurarSegundoFactor(desafio(ana));
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), "123456", "198.51.100.4"))
                .as("un secreto pendiente sin confirmar no cuenta").extracting("codigo").isEqualTo("SEGUNDO_FACTOR_NO_CONFIGURADO");
    }

    @Test void unCodigoDeRecuperacionSirveUnaSolaVez() {
        List<String> recuperacion = configuradoConCodigos(ana);
        String codigo = recuperacion.get(3);

        var s = service.verificarSegundoFactor(desafio(ana), codigo.toLowerCase().replace("-", " "), "198.51.100.4");
        assertThat(s.accessToken()).isNotBlank();
        assertThat(auditoria.registros).last().extracting("detalle").isEqualTo("segundo_factor=codigo_recuperacion");

        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), codigo, "198.51.100.4")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
        assertThat(service.verificarSegundoFactor(desafio(ana), recuperacion.get(4), "198.51.100.4")).as("los demás siguen valiendo").isNotNull();
    }

    @Test void unCodigoInventadoNoPasa() {
        configurado(ana);
        for (String malo : List.of("", "12345", "abcdef", "ZZZZZ-ZZZZZ"))
            assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), malo, "198.51.100.4"))
                    .as("«%s»", malo).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), null, "198.51.100.4")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
    }

    // --- Bloqueo -------------------------------------------------------------------------------------------------------------

    /** Seis dígitos son un millón de combinaciones: sin tope, quien ya tiene la contraseña podría probarlas todas. */
    @Test void cincoCodigosFallidosBloqueanQuinceMinutosAunConElCodigoCorrecto() {
        String secreto = configurado(ana);
        factores.intentoDentro.clear();
        reloj.avanzar(Duration.ofMinutes(5));
        for (int i = 0; i < 4; i++) fallar(ana);
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), "000000", "198.51.100.4")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");

        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4"))
                .extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS");

        assertThat(factores.intentoDentro).as("reservado fuera de la transacción: dentro, se revertiría con el error").hasSize(6).containsOnly(false);

        reloj.avanzar(Duration.ofMinutes(14));
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4"))
                .as("a los 14 minutos sigue bloqueado").extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS");
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4")).isNotNull();
    }

    @Test void unAccesoCorrectoReiniciaLaCuentaDeFallos() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        for (int i = 0; i < 4; i++) fallar(ana);
        service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4");
        reloj.avanzar(Duration.ofMinutes(1));
        for (int i = 0; i < 4; i++) fallar(ana);
        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4")).isNotNull();
    }

    @Test void losFallosAlConfigurarTambienCuentan() {
        String d = desafio(ana);
        var c = service.configurarSegundoFactor(d);
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> service.confirmarSegundoFactor(d, "000000", "203.0.113.7")).extracting("codigo").isEqualTo("CODIGO_INVALIDO");
        assertThatThrownBy(() -> service.confirmarSegundoFactor(d, codigoDe(c.secreto(), pasoActual()), "203.0.113.7"))
                .extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS");
        assertThat(factores.intentoDentro).as("también al configurar, fuera de la transacción").hasSize(6).containsOnly(false);
    }

    /**
     * El tope se reserva atómicamente, no se lee al inicio: 20 peticiones que ya leyeron «sin bloqueo» —la barrera lo fuerza, como lo hace
     * la latencia real a Postgres— consiguen cinco intentos, y solo esos cinco llegan a comprobar un código (#177, revisión H1).
     */
    @Test void peticionesSimultaneasNoPruebanMasDeCincoCodigos() throws Exception {
        configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        factores.intentoDentro.clear();
        int n = 20;
        CyclicBarrier leyeronElEstado = new CyclicBarrier(n);
        SegundoFactorRepository leeYEspera = new SegundoFactorRepository() {
            public Optional<Estado> buscar(UUID id) {
                Optional<Estado> e = factores.buscar(id);
                try { leyeronElEstado.await(10, TimeUnit.SECONDS); } catch (Exception ex) { throw new IllegalStateException(ex); }
                return e;
            }
            public void guardarPendiente(UUID id, byte[] s) { factores.guardarPendiente(id, s); }
            public void confirmar(UUID id, long p, List<String> h) { factores.confirmar(id, p, h); }
            public boolean registrarAcceso(UUID id, long p) { return factores.registrarAcceso(id, p); }
            public boolean reservarIntento(UUID id, int m, Instant a, Instant h) { return factores.reservarIntento(id, m, a, h); }
            public boolean consumirCodigoRecuperacion(UUID id, String h) { return factores.consumirCodigoRecuperacion(id, h); }
        };
        AtomicInteger codigosComprobados = new AtomicInteger();
        SegundoFactor totpQueCuenta = new SegundoFactor() {
            public String nuevoSecreto() { return totp.nuevoSecreto(); }
            public String uri(String s, String c) { return totp.uri(s, c); }
            public OptionalLong paso(String s, String codigo, Instant ahora) { codigosComprobados.incrementAndGet(); return totp.paso(s, codigo, ahora); }
        };
        AutenticarAdministradorService simultaneo = new AutenticarAdministradorService(administradores, hasher, tokens, leeYEspera, totpQueCuenta, cifrador,
                uri -> uri.getBytes(StandardCharsets.UTF_8), uow, auditoria, reloj, limite);
        String desafio = desafio(ana);

        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String distinto = String.format("%06d", 100000 + i);
            resultados.add(pool.submit(() -> {
                try { simultaneo.verificarSegundoFactor(desafio, distinto, "198.51.100.4"); return "ENTRO"; }
                catch (DomainException e) { return e.codigo(); }
            }));
        }
        Map<String, Integer> porCodigo = new TreeMap<>();
        for (Future<String> f : resultados) porCodigo.merge(f.get(30, TimeUnit.SECONDS), 1, Integer::sum);
        pool.shutdown();

        assertThat(porCodigo).as("de 20 peticiones, cinco comprueban y quince se bloquean").containsExactly(Map.entry("CODIGO_INVALIDO", 5), Map.entry("DEMASIADOS_INTENTOS", 15));
        assertThat(codigosComprobados).as("los intentos negados no llegan a mirar el código").hasValue(5);
    }

    /** El quinto intento deja el bloqueo puesto antes de saber si el código era bueno: si lo era, el acceso lo levanta y no queda nada. */
    @Test void unAciertoEnElQuintoIntentoEntraYNoDejaLaCuentaBloqueada() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        for (int i = 0; i < 4; i++) fallar(ana);

        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4")).as("el quinto intento, correcto").isNotNull();

        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(service.verificarSegundoFactor(desafio(ana), codigoDe(secreto, pasoActual()), "198.51.100.4")).as("el siguiente no está bloqueado").isNotNull();
    }

    // --- Desafío -------------------------------------------------------------------------------------------------------------

    @Test void sinDesafioValidoNingunPasoAvanza() {
        configurado(ana);
        for (String malo : Arrays.asList(null, "", "jwt:" + ana.id() + ":ana@khipu.pe", "desafio:" + UUID.randomUUID())) {
            assertThatThrownBy(() -> service.configurarSegundoFactor(malo)).extracting("codigo").isEqualTo("SESION_INVALIDA");
            assertThatThrownBy(() -> service.confirmarSegundoFactor(malo, "123456", "1.1.1.1")).extracting("codigo").isEqualTo("SESION_INVALIDA");
            assertThatThrownBy(() -> service.verificarSegundoFactor(malo, "123456", "1.1.1.1")).extracting("codigo").isEqualTo("SESION_INVALIDA");
        }
    }

    @Test void unAdministradorDesactivadoDespuesDelLoginNoTerminaDeEntrar() {
        String secreto = configurado(ana);
        reloj.avanzar(Duration.ofMinutes(5));
        String d = desafio(ana);
        administradores.guardar(new Administrador(ana.id(), ana.email(), ana.passwordHash(), false));
        assertThatThrownBy(() -> service.verificarSegundoFactor(d, codigoDe(secreto, pasoActual()), "1.1.1.1")).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    @Test void meDevuelveElAdministradorOFalla() {
        assertThat(service.me(ana.id())).isEqualTo(ana);
        assertThatThrownBy(() -> service.me(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    // --- Apoyo ---------------------------------------------------------------------------------------------------------------

    private Administrador admin(String email, boolean activo) {
        Administrador a = new Administrador(UUID.randomUUID(), email, hasher.hash("Segura123"), activo);
        admins.put(a.id(), a);
        return a;
    }

    private String desafio(Administrador a) { return service.login(a.email(), "Segura123", null).token(); }

    private String configurado(Administrador a) {
        String d = desafio(a);
        var c = service.configurarSegundoFactor(d);
        service.confirmarSegundoFactor(d, codigoDe(c.secreto(), pasoActual()), "203.0.113.7");
        return c.secreto();
    }

    private List<String> configuradoConCodigos(Administrador a) {
        String d = desafio(a);
        var c = service.configurarSegundoFactor(d);
        return service.confirmarSegundoFactor(d, codigoDe(c.secreto(), pasoActual()), "203.0.113.7").codigosRecuperacion();
    }

    private void fallar(Administrador a) {
        assertThatThrownBy(() -> service.verificarSegundoFactor(desafio(a), "000000", "198.51.100.4")).isInstanceOf(DomainException.class);
    }

    private long pasoActual() { return reloj.instant().getEpochSecond() / 30; }

    static String codigoDe(String secreto, long paso) { return String.format("%06d", Math.floorMod((secreto + "@" + paso).hashCode(), 1_000_000)); }

    static byte[] invertir(byte[] b) {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++) r[i] = b[b.length - 1 - i];
        return r;
    }

    static String sha256(String codigo) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(codigo.replace("-", "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Segundo factor en memoria, con las mismas reglas que la tabla (anti-reuso, intento reservado de forma atómica y códigos de un
     * solo uso). Sus operaciones son {@code synchronized} como cada sentencia del UPDATE real: los tests de concurrencia lo comparten.
     */
    final class Factores implements SegundoFactorRepository {
        final Map<UUID, Estado> estados = new HashMap<>();
        final Map<UUID, Map<String, Boolean>> codigos = new HashMap<>();
        final List<Boolean> confirmadoDentro = new ArrayList<>();
        final List<Boolean> intentoDentro = new ArrayList<>();

        public synchronized Optional<Estado> buscar(UUID id) { return Optional.ofNullable(estados.get(id)); }
        public synchronized void guardarPendiente(UUID id, byte[] s) { estados.put(id, new Estado(s, false, 0, 0, null)); codigos.remove(id); }
        public synchronized void confirmar(UUID id, long paso, List<String> hashes) {
            confirmadoDentro.add(uow.dentro);
            Estado e = estados.get(id);
            estados.put(id, new Estado(e.secretoCifrado(), true, paso, 0, null));
            Map<String, Boolean> m = new HashMap<>();
            hashes.forEach(h -> m.put(h, false));
            codigos.put(id, m);
        }
        public synchronized boolean registrarAcceso(UUID id, long paso) {
            Estado e = estados.get(id);
            if (paso <= e.ultimoPaso()) return false;
            estados.put(id, new Estado(e.secretoCifrado(), e.confirmado(), paso, 0, null));
            return true;
        }
        public synchronized boolean reservarIntento(UUID id, int max, Instant ahora, Instant hasta) {
            intentoDentro.add(uow.dentro);
            Estado e = estados.get(id);
            if (e.bloqueadoHasta() != null && ahora.isBefore(e.bloqueadoHasta())) return false;
            int fallos = e.fallos() + 1;
            estados.put(id, fallos >= max ? new Estado(e.secretoCifrado(), e.confirmado(), e.ultimoPaso(), 0, hasta)
                    : new Estado(e.secretoCifrado(), e.confirmado(), e.ultimoPaso(), fallos, e.bloqueadoHasta()));
            return true;
        }
        public synchronized boolean consumirCodigoRecuperacion(UUID id, String hash) {
            Map<String, Boolean> m = codigos.getOrDefault(id, Map.of());
            if (!Boolean.FALSE.equals(m.get(hash))) return false;
            m.put(hash, true);
            Estado e = estados.get(id);
            estados.put(id, new Estado(e.secretoCifrado(), e.confirmado(), e.ultimoPaso(), 0, null));
            return true;
        }
    }

    static final class RelojMovil extends Clock {
        private Instant ahora;
        RelojMovil(Instant inicio) { this.ahora = inicio; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("America/Lima"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
