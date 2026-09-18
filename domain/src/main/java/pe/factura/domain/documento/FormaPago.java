package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Forma de pago de la factura (RS 193-2020, obligatoria desde 2022): al contado, o al crédito con el monto neto
 * pendiente y sus cuotas. Las reglas replican las de la hoja Factura2_0 de SUNAT (códigos 3244–3267, 3319) para
 * rechazar localmente, con el mismo código, lo que SUNAT rechazaría después de consumir el número.
 */
public record FormaPago(Tipo tipo, BigDecimal montoPendiente, List<Cuota> cuotas) {

    public enum Tipo { CONTADO, CREDITO }

    /** Una cuota: monto y fecha de vencimiento. El identificador (Cuota001…) lo da la posición en la lista. */
    public record Cuota(BigDecimal monto, LocalDate vencimiento) {}

    public FormaPago {
        cuotas = cuotas == null ? List.of() : List.copyOf(cuotas);
        if (tipo == Tipo.CONTADO && (montoPendiente != null || !cuotas.isEmpty()))
            throw new DomainException("FORMA_PAGO_INVALIDA", "3252 - Si existe información de cuotas o monto pendiente, la forma de pago debe ser al crédito");
        if (tipo == Tipo.CREDITO) {
            if (cuotas.isEmpty()) throw new DomainException("FORMA_PAGO_INVALIDA", "3249 - Si la forma de pago es al crédito debe existir al menos una cuota");
            if (montoPendiente == null) throw new DomainException("FORMA_PAGO_INVALIDA", "3251 - Si la forma de pago es al crédito debe consignarse el monto neto pendiente de pago");
            if (montoPendiente.signum() <= 0 || montoPendiente.scale() > 2)
                throw new DomainException("FORMA_PAGO_INVALIDA", "3250 - El monto neto pendiente de pago debe ser positivo con hasta 2 decimales");
            for (Cuota q : cuotas) {
                if (q.monto() == null || q.monto().signum() <= 0 || q.monto().scale() > 2)
                    throw new DomainException("FORMA_PAGO_INVALIDA", "3253 - El monto de cada cuota debe ser positivo con hasta 2 decimales");
                if (q.vencimiento() == null)
                    throw new DomainException("FORMA_PAGO_INVALIDA", "3256 - Cada cuota debe indicar su fecha de pago");
            }
            BigDecimal suma = cuotas.stream().map(Cuota::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (suma.compareTo(montoPendiente) != 0)
                throw new DomainException("FORMA_PAGO_INVALIDA", "3319 - La suma de las cuotas (" + suma + ") debe ser igual al monto neto pendiente de pago (" + montoPendiente + ")");
        }
    }

    public static FormaPago contado() { return new FormaPago(Tipo.CONTADO, null, List.of()); }

    public static FormaPago credito(BigDecimal montoPendiente, List<Cuota> cuotas) { return new FormaPago(Tipo.CREDITO, montoPendiente, cuotas); }

    public boolean esCredito() { return tipo == Tipo.CREDITO; }

    /**
     * Reglas que dependen del comprobante: el pendiente no supera el total (3265) y cada cuota vence después de la
     * emisión (3267). La 3266 (cuota ≤ total) queda garantizada por 3319 + 3265, no se comprueba aparte.
     */
    void validarContra(BigDecimal total, LocalDate fechaEmision) {
        if (!esCredito()) return;
        if (montoPendiente.compareTo(total) > 0)
            throw new DomainException("FORMA_PAGO_INVALIDA", "3265 - El monto neto pendiente de pago (" + montoPendiente + ") debe ser menor o igual al importe total (" + total + ")");
        for (Cuota q : cuotas) {
            if (!q.vencimiento().isAfter(fechaEmision))
                throw new DomainException("FORMA_PAGO_INVALIDA", "3267 - La fecha de pago de la cuota (" + q.vencimiento() + ") no puede ser anterior o igual a la fecha de emisión (" + fechaEmision + ")");
        }
    }

    /** Identificador SUNAT de la cuota en la posición dada (base 1): Cuota001, Cuota002… */
    public static String idCuota(int posicion) { return String.format("Cuota%03d", posicion); }
}
