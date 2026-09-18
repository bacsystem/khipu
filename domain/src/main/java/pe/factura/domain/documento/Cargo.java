package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Cargo adicional sobre una línea o sobre el comprobante (catálogo 53, {@code ChargeIndicator true}): flete, embalaje,
 * recargo al consumo, propinas, gastos administrativos… Se expresa como porcentaje sobre la base o como monto fijo y el
 * código SUNAT decide el nivel y si entra en la base del IGV:
 * <ul>
 *   <li>Línea: {@code 47} afecta la base (se suma al valor de venta y paga IGV), {@code 48} no la afecta.</li>
 *   <li>Global: {@code 49} afecta la base gravada, {@code 50} no; {@code 46} recargo al consumo y/o propinas, que tampoco
 *       la afecta (Ley 25988: el recargo no forma parte de la base imponible).</li>
 * </ul>
 * Los cargos que no afectan la base suman al importe a pagar a través de {@code ChargeTotalAmount} (reglas 3301, 3280).
 */
public record Cargo(String codigo, Tipo tipo, BigDecimal valor) {

    public enum Tipo { PORCENTAJE, MONTO }

    public static final Set<String> CODIGOS_LINEA = Set.of("47", "48");
    public static final Set<String> CODIGOS_GLOBALES = Set.of("46", "49", "50");
    private static final Set<String> AFECTAN_BASE = Set.of("47", "49");

    public Cargo {
        if (codigo == null || !(CODIGOS_LINEA.contains(codigo) || CODIGOS_GLOBALES.contains(codigo)))
            throw new DomainException("CARGO_INVALIDO", "2954 - Código de cargo no válido (catálogo 53): use 47/48 por línea o 46/49/50 globales");
        if (tipo == null || valor == null || valor.signum() <= 0)
            throw new DomainException("CARGO_INVALIDO", "2955 - El cargo debe ser un porcentaje o monto positivo");
        if (valor.scale() > (tipo == Tipo.PORCENTAJE ? 5 : 2))
            throw new DomainException("CARGO_INVALIDO", "2955 - El cargo admite hasta 2 decimales (monto) o 5 (porcentaje)");
        if (tipo == Tipo.PORCENTAJE && valor.compareTo(new BigDecimal("1000")) >= 0)
            throw new DomainException("CARGO_INVALIDO", "3052 - El porcentaje del cargo debe ser menor que 1000 (factor de hasta 3 enteros)");
    }

    public static Cargo porcentaje(String codigo, BigDecimal pct) { return new Cargo(codigo, Tipo.PORCENTAJE, pct); }
    public static Cargo monto(String codigo, BigDecimal monto) { return new Cargo(codigo, Tipo.MONTO, monto); }

    public boolean global() { return CODIGOS_GLOBALES.contains(codigo); }
    public boolean afectaBaseIgv() { return AFECTAN_BASE.contains(codigo); }

    /** Monto del cargo sobre una base (valor de venta sin IGV), a 2 decimales; SUNAT exige que sea distinto de cero (2955/2968). */
    public BigDecimal montoSobre(BigDecimal base) {
        BigDecimal monto = tipo == Tipo.PORCENTAJE
                ? base.multiply(valor).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP);
        if (monto.signum() == 0)
            throw new DomainException("CARGO_INVALIDO", "2955 - El cargo " + codigo + " resulta en 0.00 sobre la base " + base);
        return monto;
    }
}
