package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.CuentaDetalle;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.out.CuentasAdminRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetalleCuentaAdminServiceTest {
    UUID id = UUID.randomUUID();
    CuentaDetalle detalle = new CuentaDetalle(id, "Mi negocio", "ana@negocio.pe", "987654321", Instant.parse("2026-09-01T10:00:00Z"), null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());
    CuentasAdminRepository repo = new CuentasAdminRepository() {
        public List<CuentaResumen> listar(Filtro f, int p, int n) { throw new AssertionError("el detalle no lista"); }
        public long contar(Filtro f) { throw new AssertionError("el detalle no cuenta"); }
        public Optional<CuentaDetalle> detalle(UUID cuenta) { return id.equals(cuenta) ? Optional.of(detalle) : Optional.empty(); }
    };
    DetalleCuentaAdminService service = new DetalleCuentaAdminService(repo);

    @Test void devuelveElDetalleDeLaCuenta() {
        assertThat(service.detalle(id)).isSameAs(detalle);
    }

    @Test void unaCuentaQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> service.detalle(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void sinIdentificadorEsNoEncontrado() {
        assertThatThrownBy(() -> service.detalle(null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
