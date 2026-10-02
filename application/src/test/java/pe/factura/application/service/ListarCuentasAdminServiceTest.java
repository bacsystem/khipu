package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.out.CuentasAdminRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListarCuentasAdminServiceTest {
    record Llamada(String operacion, Filtro filtro, int pagina, int porPagina) {}

    final List<Llamada> llamadas = new ArrayList<>();
    final List<CuentaResumen> datos = List.of(
            new CuentaResumen(UUID.randomUUID(), "Mi negocio", "ana@negocio.pe", "987654321", Instant.parse("2026-09-01T10:00:00Z"), 2, Instant.parse("2026-10-01T09:00:00Z")));
    final CuentasAdminRepository repo = new CuentasAdminRepository() {
        public List<CuentaResumen> listar(Filtro f, int pagina, int porPagina) { llamadas.add(new Llamada("listar", f, pagina, porPagina)); return datos; }
        public long contar(Filtro f) { llamadas.add(new Llamada("contar", f, 0, 0)); return 42; }
    };
    final ListarCuentasAdminService service = new ListarCuentasAdminService(repo);

    @Test void listaConElFiltroYLaPaginaPedidos() {
        Filtro filtro = new Filtro("ana");

        assertThat(service.listar(filtro, 3, 50)).isEqualTo(datos);

        assertThat(llamadas).containsExactly(new Llamada("listar", filtro, 3, 50));
    }

    @Test void cuentaConElMismoFiltro() {
        Filtro filtro = new Filtro("20100066603");

        assertThat(service.contar(filtro)).isEqualTo(42);

        assertThat(llamadas).containsExactly(new Llamada("contar", filtro, 0, 0));
    }

    @Test void sinFiltroSeListaTodo() {
        service.listar(null, 1, 20);
        service.contar(null);

        assertThat(llamadas).extracting(Llamada::filtro).containsOnly(Filtro.NINGUNO);
    }

    @Test void laBusquedaSeRecortaYLaVaciaEsNinguna() {
        assertThat(new Filtro("  ana  ").q()).isEqualTo("ana");
        assertThat(new Filtro("   ").q()).isNull();
        assertThat(new Filtro("").q()).isNull();
        assertThat(new Filtro(null).q()).isNull();
        assertThat(new Filtro(null)).isEqualTo(Filtro.NINGUNO);
    }
}
