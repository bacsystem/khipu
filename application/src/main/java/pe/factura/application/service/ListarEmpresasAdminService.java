package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.out.EmpresasAdminRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
public class ListarEmpresasAdminService implements ListarEmpresasAdminUseCase {
    private final EmpresasAdminRepository empresas;
    private final Clock clock;

    @Override public List<EmpresaResumen> listar(Filtro filtro, int pagina, int porPagina) {
        return empresas.listar(filtro == null ? Filtro.NINGUNO : filtro, LocalDate.now(clock), pagina, porPagina);
    }

    @Override public long contar(Filtro filtro) {
        return empresas.contar(filtro == null ? Filtro.NINGUNO : filtro, LocalDate.now(clock));
    }
}
