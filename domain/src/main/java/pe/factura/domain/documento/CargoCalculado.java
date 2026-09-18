package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.util.Optional;

/** Cargo aplicado (línea o global): monto resultante, base sobre la que se calculó y factor SUNAT para el XML. */
public record CargoCalculado(Cargo cargo, BigDecimal base, BigDecimal monto) {
    static CargoCalculado de(Cargo cargo, BigDecimal base) { return new CargoCalculado(cargo, base, cargo.montoSobre(base)); }
    public String codigo() { return cargo.codigo(); }
    public boolean afectaBase() { return cargo.afectaBaseIgv(); }
    /** Factor para el XML, vacío cuando el redondeo a 5 decimales no reproduce el monto (reglas 3290/3307). */
    public Optional<BigDecimal> factor() { return FactorSunat.de(monto, base); }
}
