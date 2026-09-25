package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.plataforma.Administrador;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrearAdministradorServiceTest {
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
    CrearAdministradorService service = new CrearAdministradorService(administradores, hasher);

    @Test void creaConEmailNormalizadoYPasswordHasheada() {
        Administrador a = service.crear("Ana@Khipu.PE", "Segura123");
        assertThat(a.email()).isEqualTo("ana@khipu.pe");
        assertThat(a.passwordHash()).isEqualTo("H(Segura123)");
        assertThat(a.activo()).isTrue();
        assertThat(map).containsKey(a.id());
    }

    @Test void rechazaDuplicadoYPasswordDebil() {
        service.crear("ana@khipu.pe", "Segura123");
        assertThatThrownBy(() -> service.crear("ANA@KHIPU.PE", "Otra12345")).extracting("codigo").isEqualTo("DUPLICADO");
        assertThatThrownBy(() -> service.crear("otro@khipu.pe", "corta")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }
}
