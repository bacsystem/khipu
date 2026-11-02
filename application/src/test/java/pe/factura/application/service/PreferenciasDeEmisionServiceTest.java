package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase.PreferenciasDeEmision;
import pe.factura.application.port.out.PreferenciasDeEmisionRepository;
import pe.factura.domain.DomainException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenciasDeEmisionServiceTest {
    /** Como la tabla: solo las empresas que existen, y apagado por defecto. */
    static class EnMemoria implements PreferenciasDeEmisionRepository {
        final Map<UUID, Boolean> tenants = new HashMap<>();
        @Override public boolean opcionesAvanzadas(UUID tenantId) { return tenants.getOrDefault(tenantId, false); }
        @Override public boolean fijarOpcionesAvanzadas(UUID tenantId, boolean valor) {
            if (!tenants.containsKey(tenantId)) return false;
            tenants.put(tenantId, valor);
            return true;
        }
    }

    final EnMemoria repo = new EnMemoria();
    final PreferenciasDeEmisionService service = new PreferenciasDeEmisionService(repo);
    final UUID tenant = UUID.randomUUID();

    @Test void unaEmpresaNuevaNoMuestraLasOpcionesAvanzadas() {
        repo.tenants.put(tenant, false);
        assertThat(service.obtener(tenant)).isEqualTo(new PreferenciasDeEmision(false));
    }

    @Test void guardarLasEnciendeYLasApagaSoloEnEsaEmpresa() {
        UUID otra = UUID.randomUUID();
        repo.tenants.put(tenant, false);
        repo.tenants.put(otra, false);

        assertThat(service.guardar(tenant, new PreferenciasDeEmision(true))).isEqualTo(new PreferenciasDeEmision(true));
        assertThat(service.obtener(tenant).opcionesAvanzadas()).isTrue();
        assertThat(service.obtener(otra).opcionesAvanzadas()).isFalse();

        service.guardar(tenant, new PreferenciasDeEmision(false));
        assertThat(service.obtener(tenant).opcionesAvanzadas()).isFalse();
    }

    @Test void guardarEnUnaEmpresaQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> service.guardar(tenant, new PreferenciasDeEmision(true)))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("codigo", "NO_ENCONTRADO");
    }
}
