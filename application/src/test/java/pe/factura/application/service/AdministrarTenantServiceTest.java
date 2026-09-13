package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AdministrarTenantUseCase.TenantCreado;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Entorno;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AdministrarTenantServiceTest {
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Series series = new Fakes.Series();
    Map<String, ApiKey> keys = new HashMap<>();
    ApiKeyRepository apiKeys = new ApiKeyRepository() {
        public void guardar(ApiKey k) { keys.put(k.hash(), k); }
        public Optional<ApiKey> buscarPorHash(String h) { return Optional.ofNullable(keys.get(h)); }
    };
    AdministrarTenantService service = new AdministrarTenantService(tenants, series, apiKeys, Fakes.UOW, "pepper", Fakes.CLOCK);

    @Test void crearTenantDevuelveApiKeyUnaVez() {
        TenantCreado r = service.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA);
        assertThat(r.apiKeyEnClaro()).startsWith("fk_").hasSize(43);
        assertThat(keys).containsKey(ApiKeyGenerator.hash(r.apiKeyEnClaro(), "pepper"));
        assertThat(tenants.buscarPorRuc("20100066603")).isPresent();
    }

    @Test void rucDuplicadoFalla() {
        service.crearTenant("20100066603", "A", Entorno.BETA);
        assertThatThrownBy(() -> service.crearTenant("20100066603", "B", Entorno.BETA)).extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void credencialesYSerie() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        service.cargarCredencialesSol(id, "MODDATOS", "moddatos");
        assertThat(tenants.buscar(id).get().sol().usernameToken("20100066603")).isEqualTo("20100066603MODDATOS");
        service.crearSerie(id, TipoDocumento.FACTURA, "F001", 10);
        assertThat(series.siguienteNumero(id, TipoDocumento.FACTURA, "F001")).isEqualTo(11);
        assertThatThrownBy(() -> service.crearSerie(id, TipoDocumento.FACTURA, "B001", 0)).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void certificadoInvalidoFalla() {
        UUID id = service.crearTenant("20100066603", "A", Entorno.BETA).tenant().id();
        assertThatThrownBy(() -> service.cargarCertificado(id, new byte[]{1, 2, 3}, "x"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CERTIFICADO_INVALIDO");
    }
}
