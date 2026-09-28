package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.AdministradorTokenEmisor;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.plataforma.Administrador;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutenticarAdministradorServiceTest {
    Map<UUID, Administrador> map = new HashMap<>();
    AdministradorRepository administradores = new AdministradorRepository() {
        public void guardar(Administrador a) { map.put(a.id(), a); }
        public Optional<Administrador> buscar(UUID id) { return Optional.ofNullable(map.get(id)); }
        public Optional<Administrador> buscarPorEmail(String e) { return map.values().stream().filter(a -> a.email().equals(e)).findFirst(); }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    AdministradorTokenEmisor tokens = new AdministradorTokenEmisor() {
        public String emitir(Claims c) { return "jwt:" + c.administradorId() + ":" + c.email(); }
        public Optional<Claims> verificar(String t) { return Optional.empty(); }
    };
    AutenticarAdministradorService service = new AutenticarAdministradorService(administradores, hasher, tokens);

    @Test void loginCorrectoEmiteToken() {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", hasher.hash("Segura123"), true);
        map.put(a.id(), a);

        var sesion = service.login("ANA@khipu.pe", "Segura123");
        assertThat(sesion.accessToken()).isEqualTo("jwt:" + a.id() + ":ana@khipu.pe");
        assertThat(sesion.administrador().id()).isEqualTo(a.id());
    }

    @Test void loginConCredencialesInvalidas() {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", hasher.hash("Segura123"), true);
        map.put(a.id(), a);

        assertThatThrownBy(() -> service.login("ana@khipu.pe", "otra")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("nadie@khipu.pe", "Segura123")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test void loginDeAdministradorInactivoFalla() {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", hasher.hash("Segura123"), false);
        map.put(a.id(), a);

        assertThatThrownBy(() -> service.login("ana@khipu.pe", "Segura123")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test void meDevuelveElAdministradorOFalla() {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", "hash", true);
        map.put(a.id(), a);

        assertThat(service.me(a.id())).isEqualTo(a);
        assertThatThrownBy(() -> service.me(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
