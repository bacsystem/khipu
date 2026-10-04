package pe.factura.application.port.out;

import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;

import java.time.LocalDate;
import java.util.List;

/** Consultas del backoffice sobre las empresas de todas las cuentas. Solo lectura. */
public interface EmpresasAdminRepository {
    /**
     * Las empresas que cumplen el filtro, de la más reciente a la más antigua; {@code pagina} desde 1. {@code hoy} fija el estado del
     * certificado y el mes de «comprobantes del mes»: lo decide quien llama (con su reloj), no la base.
     */
    List<EmpresaResumen> listar(Filtro filtro, LocalDate hoy, int pagina, int porPagina);
    long contar(Filtro filtro, LocalDate hoy);
}
