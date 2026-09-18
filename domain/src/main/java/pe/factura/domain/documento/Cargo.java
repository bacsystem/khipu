package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Quien construye un cargo no elige el código: lo derivan {@link #deLinea} y {@link #global} del nivel, de si afecta la
 * base y del {@link Motivo}, igual que {@link Descuento#codigoSunat(boolean)} para los descuentos.
 */
public record Cargo(String codigo, Tipo tipo, BigDecimal valor) {

    public enum Tipo { PORCENTAJE, MONTO }

    /** Motivos con código propio en el catálogo 53; los cargos "genéricos" (47–50) no llevan motivo. */
    public enum Motivo {
        /** Recargo al consumo y/o propinas (46): por la Ley 25988 nunca forma parte de la base imponible. Solo global. */
        RECARGO_CONSUMO("46");
        private final String codigo;
        Motivo(String codigo) { this.codigo = codigo; }
        public String codigo() { return codigo; }
        static Optional<Motivo> porCodigo(String codigo) {
            for (Motivo m : values()) if (m.codigo.equals(codigo)) return Optional.of(m);
            return Optional.empty();
        }
        /** Nombre público del motivo en la API (minúsculas: {@code recargo_consumo}). */
        public String nombre() { return name().toLowerCase(Locale.ROOT); }
        /** Resuelve el nombre de la API; un nombre desconocido es {@code CARGO_INVALIDO} con la lista admitida. */
        public static Motivo porNombre(String nombre) {
            for (Motivo m : values()) if (m.nombre().equals(nombre)) return m;
            throw new DomainException("CARGO_INVALIDO", "Motivo de cargo desconocido: " + nombre + "; admitidos: "
                    + Arrays.stream(values()).map(Motivo::nombre).collect(Collectors.joining(", ")));
        }
    }

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

    /** Cargo de línea: 47 si afecta la base del IGV (se suma al valor de venta), 48 si se cobra sin IGV. */
    public static Cargo deLinea(boolean afectaBaseIgv, Tipo tipo, BigDecimal valor) {
        return new Cargo(afectaBaseIgv ? "47" : "48", tipo, valor);
    }

    /**
     * Cargo global: 49 si afecta la base gravada, 50 si no; con {@code motivo} el código es el del motivo y
     * {@code afectaBaseIgv} debe ser falso (el recargo al consumo no paga IGV).
     */
    public static Cargo global(boolean afectaBaseIgv, Motivo motivo, Tipo tipo, BigDecimal valor) {
        if (motivo == null) return new Cargo(afectaBaseIgv ? "49" : "50", tipo, valor);
        if (afectaBaseIgv)
            throw new DomainException("CARGO_INVALIDO", "El recargo al consumo (46) no afecta la base del IGV: no indique afecta_base_igv: true");
        return new Cargo(motivo.codigo(), tipo, valor);
    }

    public boolean global() { return CODIGOS_GLOBALES.contains(codigo); }
    public boolean afectaBaseIgv() { return AFECTAN_BASE.contains(codigo); }
    /** Motivo con código propio (hoy solo el recargo al consumo), vacío para los cargos genéricos 47–50. */
    public Optional<Motivo> motivo() { return Motivo.porCodigo(codigo); }

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
