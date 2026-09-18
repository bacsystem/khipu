package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Percepción del IGV cobrada por un agente de percepción (catálogo 53: 51 venta interna 2 %, 52 combustible 1 %,
 * 53 tasa especial 0,5 %; tasas del catálogo 22). Solo en operaciones 2001 al contado y en soles (reglas 2788, 2792,
 * 2797, 2798, 3093, 3233, 3308–3311, 3330). Se cobra además del importe total: el cliente paga total + percepción.
 */
public record Percepcion(String regimen, BigDecimal porcentaje, BigDecimal base, BigDecimal monto) {

    public static final String TIPO_OPERACION = "2001";
    /** Régimen del catálogo 53 (código de cargo en el XML) → fila del catálogo 22 que publica su tasa. */
    private static final Map<String, String> TASA_POR_REGIMEN = Map.of("51", "01", "52", "02", "53", "03");

    public Percepcion {
        if (regimen == null || !TASA_POR_REGIMEN.containsKey(regimen))
            throw new DomainException("PERCEPCION_INVALIDA", "3071 - El régimen de percepción debe ser 51, 52 o 53 (catálogo 53)");
        BigDecimal tasaCatalogo = CatalogoSunat.porId("22").flatMap(c -> c.entrada(TASA_POR_REGIMEN.get(regimen))).map(e -> new BigDecimal(e.extra().get("Porcentaje %")))
                .orElseThrow(() -> new DomainException("PERCEPCION_INVALIDA", "El catálogo 22 no publica la tasa del régimen " + regimen));
        porcentaje = porcentaje == null ? tasaCatalogo : porcentaje;
        if (porcentaje.compareTo(tasaCatalogo) != 0)
            throw new DomainException("PERCEPCION_INVALIDA", "La tasa del régimen " + regimen + " es " + tasaCatalogo + " % (catálogo 22), no " + porcentaje + " %");
        if (base != null && (base.signum() <= 0 || base.scale() > 2))
            throw new DomainException("PERCEPCION_INVALIDA", "3233 - La base de la percepción debe ser mayor a 0.00");
        if (monto != null && (monto.signum() <= 0 || monto.scale() > 2))
            throw new DomainException("PERCEPCION_INVALIDA", "2968 - El monto de la percepción debe ser positivo con hasta 2 decimales");
    }

    public static BigDecimal montoSobre(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /** Coherencia con el comprobante: operación 2001 al contado en PEN, base ≤ total (2797) y monto = base × % ±1 (2798); completa base y monto. */
    Percepcion completarContra(String tipoOperacion, FormaPago formaPago, String moneda, BigDecimal importeTotal) {
        if (!TIPO_OPERACION.equals(tipoOperacion))
            throw new DomainException("PERCEPCION_INVALIDA", "3308 - Solo se informa percepción con tipo de operación 2001, no " + tipoOperacion);
        if (formaPago.esCredito())
            throw new DomainException("PERCEPCION_INVALIDA", "3330 - La percepción solo se informa en facturas al contado");
        if (!"PEN".equals(moneda))
            throw new DomainException("PERCEPCION_INVALIDA", "2788 - La percepción solo se informa en facturas en soles");
        BigDecimal baseFinal = base == null ? importeTotal : base;
        if (baseFinal.compareTo(importeTotal) > 0)
            throw new DomainException("PERCEPCION_INVALIDA", "2797 - La base de la percepción (" + baseFinal + ") no puede superar el importe total (" + importeTotal + ")");
        BigDecimal esperado = montoSobre(baseFinal, porcentaje);
        if (monto != null && monto.subtract(esperado).abs().compareTo(BigDecimal.ONE) > 0)
            throw new DomainException("PERCEPCION_INVALIDA", "2798 - El monto de la percepción (" + monto + ") no corresponde a " + porcentaje + " % de la base (" + esperado + ")");
        return new Percepcion(regimen, porcentaje, baseFinal, monto == null ? esperado : monto);
    }

    public BigDecimal factor() { return porcentaje.divide(new BigDecimal("100"), 5, RoundingMode.HALF_UP); }
    /** Importe total más la percepción: lo que el cliente paga (PaymentTerms 'Percepcion', regla 3310). */
    public BigDecimal totalConPercepcion(BigDecimal importeTotal) { return importeTotal.add(monto); }
    public String descripcionRegimen() { return CatalogoSunat.descripcion("53", regimen).orElse(""); }
}
