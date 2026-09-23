package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Alta de empresa desde el portal (onboarding) y por API. La certificación del flujo de registro encontró que ni el módulo 11
 * del RUC ni la razón social tenían un test que los atara acá: el DTO solo exige `\d{11}`, así que sin la validación del
 * servicio se creaban empresas con un RUC que SUNAT no reconoce, y sin la del dominio, razones sociales con tabuladores que
 * hacen rechazar todos los comprobantes de esa empresa (4338).
 */
class GestionarEmpresasServiceTest {
    UUID cuentaId = UUID.randomUUID();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return cuentasMap.values().stream().filter(c -> c.email().equals(e)).findFirst(); }
    };
    GestionarEmpresasService service;

    @BeforeEach void setUp() {
        cuentas.guardar(new Cuenta(cuentaId, "Mi cuenta", "due@ejemplo.pe"));
        service = new GestionarEmpresasService(tenants, cuentas, Fakes.UOW);
    }

    @Test void elRucSeValidaConModulo11YPrefijo() {
        // Formato correcto (11 dígitos, lo único que exige el DTO) pero dígito verificador equivocado.
        assertThatThrownBy(() -> service.crear(cuentaId, "20123456789", "Comercial Andina SAC", Entorno.BETA))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RUC_INVALIDO");
        // Prefijo inexistente con dígito verificador correcto.
        assertThatThrownBy(() -> service.crear(cuentaId, "30100066609", "Comercial Andina SAC", Entorno.BETA))
                .extracting("codigo").isEqualTo("RUC_INVALIDO");
        assertThat(service.crear(cuentaId, "20100066603", "Comercial Andina SAC", Entorno.BETA).ruc()).isEqualTo("20100066603");
    }

    @Test void laRazonSocialNoViajaConTabuladores() {
        assertThatThrownBy(() -> service.crear(cuentaId, "20100066603", "ACME SAC\tEIRL", Entorno.BETA))
                .isInstanceOf(DomainException.class).hasMessageContaining("4338");
    }

    @Test void unRucYaRegistradoNoSePuedeTomarDeNuevo() {
        service.crear(cuentaId, "20100066603", "Comercial Andina SAC", Entorno.BETA);
        assertThatThrownBy(() -> service.crear(cuentaId, "20100066603", "Otra SAC", Entorno.BETA))
                .extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void sinCuentaNoSeCreaEmpresa() {
        assertThatThrownBy(() -> service.crear(UUID.randomUUID(), "20100066603", "Comercial Andina SAC", Entorno.BETA))
                .extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void laEmpresaQuedaAsignadaALaCuentaYNoAOtra() {
        Tenant t = service.crear(cuentaId, "20100066603", "Comercial Andina SAC", Entorno.BETA);
        service.exigirPertenencia(cuentaId, t.id());
        assertThatThrownBy(() -> service.exigirPertenencia(UUID.randomUUID(), t.id()))
                .isInstanceOf(DomainException.class);
    }
}
