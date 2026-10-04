package pe.factura.application.port.in;

import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * El consumo de **todas** las cuentas en un mes contra el límite de su plan, y a quiénes se les vence el plan (#193), para avisar antes de que el cliente choque contra
 * el límite o se quede sin servicio. Lectura pura: no se audita. Cuenta como el contador de #192 y compara con el plan de hoy de cada cuenta; las cuentas dadas de baja
 * no salen.
 */
public interface ConsultarConsumoDeCuentasUseCase {
    /**
     * Una cuenta con su plan de hoy y lo que consumió en el mes. {@code porcentaje} es el del tope usado, hacia abajo, y nulo si el plan no tiene tope; {@code enAlerta}
     * es que ya llegó al umbral ({@code UsoDeLimite}). {@code estado} es el de pago de su suscripción vigente (al día, en gracia o vencida); {@code hastaCuandoCubre} es
     * hasta cuándo se la sirve, gracia incluida, y es nulo si el plan no vence.
     */
    record Fila(UUID cuentaId, String nombre, String email, UUID planId, String planNombre, long documentos, Limite limite, Integer porcentaje, boolean enAlerta,
                EstadoSuscripcion estado, Instant venceEn, int diasDeGracia, Instant hastaCuandoCubre) {}

    /** El mes en curso, en hora de Lima. */
    YearMonth mesActual();

    /** Una página, desde 1. Un {@code mes}, {@code filtro} u {@code orden} nulos son el mes en curso, todas y por porcentaje. */
    List<Fila> listar(YearMonth mes, FiltroDeConsumo filtro, OrdenDeConsumo orden, int pagina, int porPagina);

    long contar(YearMonth mes, FiltroDeConsumo filtro);

    /** Todas las filas, en el mismo orden (para exportar). */
    List<Fila> todas(YearMonth mes, FiltroDeConsumo filtro, OrdenDeConsumo orden);
}
