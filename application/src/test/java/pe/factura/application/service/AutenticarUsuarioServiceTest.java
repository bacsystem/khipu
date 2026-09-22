package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AutenticarUsuarioUseCase.Tokens;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AutenticarUsuarioServiceTest {
    // Fakes locales de auth
    Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return cuentasMap.values().stream().filter(c -> c.email().equals(e)).findFirst(); }
    };
    Map<UUID, Usuario> usuariosMap = new HashMap<>();
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { usuariosMap.put(u.id(), u); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(usuariosMap.get(id)); }
        public Optional<Usuario> buscarPorEmail(String e) { return usuariosMap.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
    };
    Map<String, SesionRepository.Sesion> sesionesMap = new HashMap<>();
    Map<String, SesionRepository.TokenRecuperacion> recMap = new HashMap<>();
    SesionRepository sesiones = new SesionRepository() {
        public void crear(Sesion s) { sesionesMap.put(s.refreshHash(), s); }
        public Optional<Sesion> buscarPorRefreshHash(String h) { return Optional.ofNullable(sesionesMap.get(h)); }
        public void revocar(UUID id) { sesionesMap.replaceAll((k, s) -> s.id().equals(id) ? new Sesion(s.id(), s.usuarioId(), s.refreshHash(), s.expiraEn(), true) : s); }
        public void revocarTodas(UUID u) { sesionesMap.replaceAll((k, s) -> s.usuarioId().equals(u) ? new Sesion(s.id(), s.usuarioId(), s.refreshHash(), s.expiraEn(), true) : s); }
        public void crearRecuperacion(TokenRecuperacion t) { recMap.put(t.tokenHash(), t); }
        public Optional<TokenRecuperacion> buscarRecuperacion(String h) { return Optional.ofNullable(recMap.get(h)); }
        public void marcarRecuperacionUsada(String h) { recMap.computeIfPresent(h, (k, t) -> new TokenRecuperacion(t.tokenHash(), t.usuarioId(), t.expiraEn(), true)); }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    TokenEmisor tokens = new TokenEmisor() {
        public String emitir(Claims c) { return "jwt:" + c.usuarioId() + ":" + c.cuentaId() + ":" + c.rol(); }
        public Optional<Claims> verificar(String t) { return Optional.empty(); }
    };
    List<String> correos = new ArrayList<>();
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) { correos.add(para + "|" + cuerpo); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
    };
    Clock clock = Clock.fixed(java.time.Instant.parse("2026-09-14T12:00:00Z"), ZoneId.of("America/Lima"));
    AutenticarUsuarioService service = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW, clock);

    @Test void registroCreaCuentaUsuarioAdminYTokens() {
        Tokens t = service.registrar("Mi negocio", "Ana@Negocio.pe", "Segura123", "987654321");
        assertThat(cuentasMap).hasSize(1);
        assertThat(t.usuario().rol()).isEqualTo(Rol.ADMIN);
        assertThat(t.usuario().email()).isEqualTo("ana@negocio.pe");
        assertThat(t.access()).startsWith("jwt:" + t.usuario().id());
        assertThat(sesionesMap).containsKey(TokenOpaco.hash(t.refresh()));
        assertThat(cuentasMap.get(t.usuario().cuentaId()).telefono()).isEqualTo("987654321");
    }

    /** Celular de contacto en Perú (#onboarding): 9 dígitos que empiezan con 9; admite +51/51 y espacios, se normaliza sin ellos. */
    @Test void telefonoDeContactoSeNormalizaYSeValida() {
        Tokens t = service.registrar("Con prefijo", "prefijo@b.pe", "Segura123", "+51 987 654 321");
        assertThat(cuentasMap.get(t.usuario().cuentaId()).telefono()).isEqualTo("987654321");
        assertThatThrownBy(() -> service.registrar("Fijo", "fijo@b.pe", "Segura123", "123456789"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
        assertThatThrownBy(() -> service.registrar("Corto", "corto@b.pe", "Segura123", "98765432"))
                .extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
    }

    @Test void registroDuplicadoYPasswordDebil() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321");
        assertThatThrownBy(() -> service.registrar("B", "A@B.PE", "Segura123", "987654321")).extracting("codigo").isEqualTo("DUPLICADO");
        assertThatThrownBy(() -> service.registrar("C", "c@d.pe", "corta", "987654321")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @Test void loginCorrectoEIncorrecto() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321");
        assertThat(service.login("A@B.PE", "Segura123").access()).isNotBlank();
        assertThatThrownBy(() -> service.login("a@b.pe", "otra")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("nadie@b.pe", "Segura123")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test void refreshRotaYElAnteriorDejaDeServir() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321");
        Tokens t2 = service.refrescar(t1.refresh());
        assertThat(t2.refresh()).isNotEqualTo(t1.refresh());
        assertThatThrownBy(() -> service.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
        service.logout(t2.refresh());
        assertThatThrownBy(() -> service.refrescar(t2.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    @Test void refreshExpiradoFalla() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321");
        AutenticarUsuarioService tarde = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW,
                Clock.offset(clock, Duration.ofDays(31)));
        assertThatThrownBy(() -> tarde.refrescar(t.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    @Test void recuperacionEnviaCorreoYRestablece() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321");
        service.solicitarRecuperacion("nadie@b.pe", "https://portal");   // silencioso
        assertThat(correos).isEmpty();
        service.solicitarRecuperacion("a@b.pe", "https://portal");
        assertThat(correos).hasSize(1);
        String token = correos.get(0).substring(correos.get(0).lastIndexOf("/restablecer/") + "/restablecer/".length());
        service.restablecer(token, "Nueva1234");
        assertThat(service.login("a@b.pe", "Nueva1234").access()).isNotBlank();
        assertThatThrownBy(() -> service.login("a@b.pe", "Segura123")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.refrescar(t.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");   // sesiones revocadas
        assertThatThrownBy(() -> service.restablecer(token, "Otra12345")).extracting("codigo").isEqualTo("TOKEN_INVALIDO"); // un solo uso
    }

    @Test void empresasDeLaCuenta() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321");
        Fakes.Tenants tenants = new Fakes.Tenants();
        GestionarEmpresasService empresas = new GestionarEmpresasService(tenants, cuentas, Fakes.UOW);
        Tenant e = empresas.crear(t.usuario().cuentaId(), "20100066603", "EMPRESA SAC", Entorno.BETA);
        assertThat(empresas.listar(t.usuario().cuentaId())).extracting(Tenant::id).containsExactly(e.id());
        assertThatCode(() -> empresas.exigirPertenencia(t.usuario().cuentaId(), e.id())).doesNotThrowAnyException();
        assertThatThrownBy(() -> empresas.exigirPertenencia(UUID.randomUUID(), e.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("EMPRESA_AJENA");
        assertThatThrownBy(() -> empresas.crear(t.usuario().cuentaId(), "20100066603", "OTRA", Entorno.BETA)).extracting("codigo").isEqualTo("DUPLICADO");
    }
}
