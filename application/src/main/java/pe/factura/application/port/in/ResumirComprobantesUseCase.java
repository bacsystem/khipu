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
     * {@code desde}/{@code hasta}: el rango pedido (nulo = abierto de ese lado). {@code atencionRequerida} es la suma de los cuatro que siguen: rechazados por SUNAT, con
     * error de envío, fuera de plazo y boletas sin enviar (#356: las que el cliente dejó FIRMADAS y ya están en el 5.º día o más).
     */
    record Resumen(LocalDate desde, LocalDate hasta, long emitidos, long aceptadosConCdr, long rechazados, long erroresDeEnvio, long fueraDePlazo, long boletasSinEnviar,
                   List<Total> facturado) {
        public long atencionRequerida() { return rechazados + erroresDeEnvio + fueraDePlazo + boletasSinEnviar; }
    }

    /** {@code RANGO_INVALIDO} si {@code desde} es posterior a {@code hasta}. */
    Resumen resumir(UUID tenantId, LocalDate desde, LocalDate hasta);
}
