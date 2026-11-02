package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un pago que un administrador registró a mano (#194): quién pagó, qué periodo cubre, cuánto, por qué medio y cuándo. No hay pasarela de pago, a propósito: es el
 * apunte que reemplaza a la hoja de cálculo. {@code suscripcionId} es la suscripción vigente cuando se registró; {@code extendioHasta} es el nuevo vencimiento si el
 * pago movió el de la suscripción, y nulo si solo quedó anotado. El monto es en soles.
 */
public record Pago(UUID id, UUID cuentaId, UUID suscripcionId, LocalDate periodoDesde, LocalDate periodoHasta, BigDecimal monto, MedioDePago medio, LocalDate fechaDePago,
                   String referencia, String nota, Instant registradoEn, Instant extendioHasta) {
    static final int REFERENCIA_MAX = 100;
    static final int NOTA_MAX = 200;
    private static final BigDecimal MONTO_MAX = new BigDecimal("9999999.99");

    public Pago {
        if (id == null || cuentaId == null || suscripcionId == null || registradoEn == null)
            throw new DomainException("PAGO_INVALIDO", "Un pago necesita su cuenta, su suscripción y el instante en que se registró");
        if (periodoDesde == null || periodoHasta == null) throw new DomainException("PERIODO_INVALIDO", "El pago necesita el periodo que cubre: desde y hasta");
        if (periodoHasta.isBefore(periodoDesde)) throw new DomainException("PERIODO_INVALIDO", "El periodo no puede terminar antes de empezar");
        if (periodoHasta.isAfter(periodoDesde.plusYears(1).minusDays(1))) throw new DomainException("PERIODO_INVALIDO", "El periodo no puede pasar de un año");
        monto = montoValido(monto);
        if (medio == null) throw new DomainException("MEDIO_INVALIDO", "El pago necesita el medio por el que se hizo");
        if (fechaDePago == null) throw new DomainException("FECHA_DE_PAGO_INVALIDA", "El pago necesita la fecha en que se hizo");
        referencia = recortado(referencia, REFERENCIA_MAX, "REFERENCIA_INVALIDA", "La referencia");
        nota = recortado(nota, NOTA_MAX, "NOTA_INVALIDA", "La nota");
    }

    /** Un pago nuevo: todavía no movió ningún vencimiento. */
    public static Pago registrar(UUID id, UUID cuentaId, UUID suscripcionId, LocalDate periodoDesde, LocalDate periodoHasta, BigDecimal monto, MedioDePago medio, LocalDate fechaDePago,
                                 String referencia, String nota, Instant registradoEn, Instant extendioHasta) {
        return new Pago(id, cuentaId, suscripcionId, periodoDesde, periodoHasta, monto, medio, fechaDePago, referencia, nota, registradoEn, extendioHasta);
    }

    /**
     * El vencimiento que daría este pago: «pagado hasta el 31 de octubre» es la medianoche del 1 de noviembre en Lima. Es exclusivo, igual que el de la suscripción: en ese
     * instante ya empieza la gracia.
     */
    public Instant venceriaEn() { return periodoHasta.plusDays(1).atStartOfDay(CicloMensual.ZONA).toInstant(); }

    /** El mismo pago, anotando que movió el vencimiento de la suscripción hasta ese instante. */
    public Pago conExtension(Instant nuevoVencimiento) {
        return new Pago(id, cuentaId, suscripcionId, periodoDesde, periodoHasta, monto, medio, fechaDePago, referencia, nota, registradoEn, nuevoVencimiento);
    }

    private static BigDecimal montoValido(BigDecimal monto) {
        if (monto == null) throw new DomainException("MONTO_INVALIDO", "El pago necesita su monto");
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.scale() > 2) throw new DomainException("MONTO_INVALIDO", "El monto admite como mucho dos decimales: " + monto.toPlainString());
        if (monto.signum() <= 0) throw new DomainException("MONTO_INVALIDO", "El monto debe ser mayor que cero: " + monto.toPlainString());
        if (monto.compareTo(MONTO_MAX) > 0) throw new DomainException("MONTO_INVALIDO", "El monto no puede pasar de " + MONTO_MAX.toPlainString());
        return monto.setScale(2);
    }

    private static String recortado(String texto, int maximo, String codigo, String que) {
        if (texto == null) return null;
        String t = texto.strip();
        if (t.isEmpty()) return null;
        if (t.length() > maximo) throw new DomainException(codigo, que + " no puede pasar de " + maximo + " caracteres");
        return t;
    }
}
