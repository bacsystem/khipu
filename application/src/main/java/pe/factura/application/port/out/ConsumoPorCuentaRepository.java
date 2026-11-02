package pe.factura.application.port.out;

import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * El consumo de documentos de **todas** las cuentas en un mes, contra el límite de su plan (#193). Cuenta como el contador de #192 (solo comprobantes aceptados,
 * por fecha de emisión, en el mes calendario de Lima) y compara con el plan **de hoy** de cada cuenta, con los límites que mandan hoy. Las cuentas dadas de baja no
 * salen (#201: salen del cálculo de consumo y cobro); las suspendidas sí.
 */
public interface ConsumoPorCuentaRepository {
    /** {@code ahora} fija «hoy» para el plan vigente y su vencimiento; {@code umbralDeAlerta} es el porcentaje desde el que una cuenta está cerca de su límite. */
    record Consulta(YearMonth mes, Instant ahora, FiltroDeConsumo filtro, OrdenDeConsumo orden, int umbralDeAlerta) {}

    /** Una cuenta con su plan de hoy y lo que consumió en el mes. {@code limite} nulo es un plan sin tope de documentos. */
    record Registro(UUID cuentaId, String nombre, String email, UUID planId, String planNombre, long documentos, Integer limite, Instant venceEn, int diasDeGracia) {}

    /** Una página, desde 1. Si dos cuentas empatan, la de nombre menor primero, y a igualdad de nombre, la de id menor: el orden es estable entre páginas. */
    List<Registro> listar(Consulta consulta, int pagina, int porPagina);

    long contar(Consulta consulta);

    /** Todas, en el mismo orden que {@link #listar} (para exportar). */
    List<Registro> todas(Consulta consulta);
}
