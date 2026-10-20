package pe.factura.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El resumen de los comprobantes de una empresa en un período (#15), para las métricas del portal: cuántos se emitieron, cuántos aceptó SUNAT con su CDR, cuántos piden
 * atención y cuánto se facturó por moneda. Lectura pura. Lo que cuenta como «emitido», «aceptado», «atención requerida» y «facturado» lo define {@code EstadoDocumento}.
 */
public interface ResumirComprobantesUseCase {
    /** Lo facturado en una moneda; negativo solo si las notas de crédito superan lo emitido. */
    record Total(String moneda, BigDecimal total) {}

    /**
     * {@code desde}/{@code hasta}: el rango pedido (nulo = abierto de ese lado). {@code emitidos}, {@code aceptadosConCdr} y {@code facturado} cuentan solo ventas (factura,
     * boleta y sus notas: {@link pe.factura.domain.documento.TipoDocumento#esVenta()}); {@code enCamino} y la atención requerida cuentan todos los tipos —liquidación de compra,
     * retención y percepción incluidas—, porque también tienen plazo ante SUNAT y el número tiene que cuadrar con el listado filtrado por ese estado. {@code atencionRequerida} es la suma de los cuatro que siguen: rechazados por SUNAT, con
     * error de envío, fuera de plazo y boletas sin enviar (#356: las que el cliente dejó FIRMADAS y ya están en el 5.º día o más). {@code enCamino} (#24): los que todavía
     * esperan la respuesta de SUNAT sin que haya nada que corregir ({@link pe.factura.domain.documento.EstadoDocumento#estaEnCamino()}).
     */
    record Resumen(LocalDate desde, LocalDate hasta, long emitidos, long aceptadosConCdr, long enCamino, long rechazados, long erroresDeEnvio, long fueraDePlazo,
                   long boletasSinEnviar, List<Total> facturado) {
        public long atencionRequerida() { return rechazados + erroresDeEnvio + fueraDePlazo + boletasSinEnviar; }
    }

    /** {@code RANGO_INVALIDO} si {@code desde} es posterior a {@code hasta}. */
    Resumen resumir(UUID tenantId, LocalDate desde, LocalDate hasta);

    /** Lo más largo que puede pedir {@link #porDia}: dos meses enteros (31 + 31), para que el gráfico del mes y su comparación con el anterior quepan en una sola llamada. */
    int MAXIMO_DIAS_POR_DIA = 62;

    /** Un día con comprobantes (#24): cuántos se emitieron ese día y cuánto se facturó, por moneda, con los mismos criterios que {@link Resumen}. */
    record Dia(LocalDate fecha, long emitidos, List<Total> facturado) {}

    /** {@code dias}: solo los días que tienen algo emitido o facturado, ordenados por fecha; los días sin comprobantes no vienen (el que dibuja el gráfico los rellena). */
    record PorDia(LocalDate desde, LocalDate hasta, List<Dia> dias) {}

    /**
     * La emisión día por día de un rango de fecha de emisión, inclusive (#24), para el gráfico del dashboard del portal. Los dos extremos son obligatorios:
     * {@code PARAMETRO_INVALIDO} si falta uno, {@code RANGO_INVALIDO} si {@code desde} es posterior a {@code hasta} o si el rango pasa de {@link #MAXIMO_DIAS_POR_DIA} días.
     */
    PorDia porDia(UUID tenantId, LocalDate desde, LocalDate hasta);
}
