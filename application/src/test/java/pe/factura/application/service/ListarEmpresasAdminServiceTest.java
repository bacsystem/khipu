package pe.factura.application.service;

import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

class ListarEmpresasAdminServiceTest {
    /** Las 02:00 UTC del 1 de octubre son todavía las 21:00 del 30 de septiembre en Lima: «hoy» es el de Lima, no el de UTC. */
    Clock reloj = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneId.of("America/Lima"));

    /** Anota con qué «hoy» y qué filtro le preguntaron. */
    static class RepoFalso implements EmpresasAdminRepository {
        final List<Object[]> llamadas = new ArrayList<>();
        public List<EmpresaResumen> listar(Filtro f, LocalDate hoy, int pagina, int porPagina) { llamadas.add(new Object[]{"listar", f, hoy, pagina, porPagina}); return List.of(); }
        public long contar(Filtro f, LocalDate hoy) { llamadas.add(new Object[]{"contar", f, hoy}); return 7; }
    }

    RepoFalso repo = new RepoFalso();
    ListarEmpresasAdminService service = new ListarEmpresasAdminService(repo, reloj);

    @Test void listaConLaFechaDeHoyEnLima() {
        service.listar(new Filtro(Entorno.BETA, EstadoCertificado.VENCIDO), 2, 20);

        assertThat(repo.llamadas.get(0)).containsExactly("listar", new Filtro(Entorno.BETA, EstadoCertificado.VENCIDO), LocalDate.of(2026, 9, 30), 2, 20);
    }

    @Test void cuentaConLaMismaFechaDeHoyQueElListado() {
        assertThat(service.contar(Filtro.NINGUNO)).isEqualTo(7);

        assertThat(repo.llamadas.get(0)).containsExactly("contar", Filtro.NINGUNO, LocalDate.of(2026, 9, 30));
    }

    @Test void sinFiltroSeListaTodo() {
        service.listar(null, 1, 20);
        service.contar(null);

        assertThat(repo.llamadas.get(0)[1]).isEqualTo(Filtro.NINGUNO);
        assertThat(repo.llamadas.get(1)[1]).isEqualTo(Filtro.NINGUNO);
    }
}
