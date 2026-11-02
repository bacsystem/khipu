package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EmpresaDetalle;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.application.port.out.EmpresasAdminRepository;
import pe.factura.domain.tenant.Entorno;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetalleEmpresaAdminServiceTest {
    /** Las 02:00 UTC del 1 de octubre son todavía las 21:00 del 30 de septiembre en Lima: «hoy» es el de Lima, no el de UTC. */
    Clock reloj = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneId.of("America/Lima"));
    UUID id = UUID.randomUUID();
    EmpresaDetalle detalle = new EmpresaDetalle(id, "20100066603", "COMERCIAL ANDINA SAC", null, Entorno.BETA, Instant.parse("2026-09-01T10:00:00Z"), null, null,
            EstadoCertificado.SIN_CERTIFICADO, null, null, false, null, null, false, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);
    List<LocalDate> hoyPedido = new ArrayList<>();

    EmpresasAdminRepository repo = new EmpresasAdminRepository() {
        public List<EmpresaResumen> listar(Filtro f, LocalDate hoy, int p, int n) { throw new AssertionError("el detalle no lista"); }
        public long contar(Filtro f, LocalDate hoy) { throw new AssertionError("el detalle no cuenta"); }
        public Optional<EmpresaDetalle> detalle(UUID empresa, LocalDate hoy) {
            hoyPedido.add(hoy);
            return id.equals(empresa) ? Optional.of(detalle) : Optional.empty();
        }
    };
    DetalleEmpresaAdminService service = new DetalleEmpresaAdminService(repo, reloj);

    @Test void devuelveElDetalleDeLaEmpresa() {
        assertThat(service.detalle(id)).isSameAs(detalle);
    }

    @Test void pideElDetalleConLaFechaDeHoyEnLima() {
        service.detalle(id);

        assertThat(hoyPedido).containsExactly(LocalDate.of(2026, 9, 30));
    }

    @Test void unaEmpresaQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> service.detalle(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void sinIdentificadorEsNoEncontradoSinConsultar() {
        assertThatThrownBy(() -> service.detalle(null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(hoyPedido).isEmpty();
    }
}
