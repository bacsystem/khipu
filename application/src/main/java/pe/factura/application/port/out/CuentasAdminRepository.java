package pe.factura.application.port.out;

import pe.factura.application.port.in.DetalleCuentaAdminUseCase.CuentaDetalle;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas del backoffice sobre todas las cuentas de clientes. Solo lectura. */
public interface CuentasAdminRepository {
    /** Las cuentas que cumplen el filtro, de la más reciente a la más antigua; {@code pagina} desde 1. */
    List<CuentaResumen> listar(Filtro filtro, int pagina, int porPagina);
    long contar(Filtro filtro);

    /** La cuenta con sus usuarios, empresas y actividad reciente (#181); vacío si no existe. */
    Optional<CuentaDetalle> detalle(UUID cuentaId);
}
