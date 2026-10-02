package pe.factura.application.port.out;

import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.util.List;

/** Consultas del backoffice sobre todas las cuentas de clientes. Solo lectura. */
public interface CuentasAdminRepository {
    /** Las cuentas que cumplen el filtro, de la más reciente a la más antigua; {@code pagina} desde 1. */
    List<CuentaResumen> listar(Filtro filtro, int pagina, int porPagina);
    long contar(Filtro filtro);
}
