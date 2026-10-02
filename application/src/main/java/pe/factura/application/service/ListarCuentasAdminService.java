package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.out.CuentasAdminRepository;

import java.util.List;

@RequiredArgsConstructor
public class ListarCuentasAdminService implements ListarCuentasAdminUseCase {
    private final CuentasAdminRepository cuentas;

    @Override public List<CuentaResumen> listar(Filtro filtro, int pagina, int porPagina) {
        return cuentas.listar(filtro == null ? Filtro.NINGUNO : filtro, pagina, porPagina);
    }

    @Override public long contar(Filtro filtro) {
        return cuentas.contar(filtro == null ? Filtro.NINGUNO : filtro);
    }
}
