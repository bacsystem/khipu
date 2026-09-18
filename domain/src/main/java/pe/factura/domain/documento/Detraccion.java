package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * Detracción (SPOT): el adquirente deposita un porcentaje del importe en la cuenta del Banco de la Nación del emisor.
 * Va en facturas con tipo de operación 1001–1004 (reglas 3127–3129) con el bien/servicio del catálogo 54, el medio de
 * pago del catálogo 59, el porcentaje y el monto **siempre en soles** (3208). No altera los totales del comprobante.
 */
public record Detraccion(String codigoBienServicio, BigDecimal porcentaje, BigDecimal monto, String cuentaBancoNacion, String medioPago) {

    public static final Set<String> TIPOS_OPERACION = Set.of("1001", "1002", "1003", "1004");
    /** Tipos de operación que fijan el bien/servicio (regla 3129). */
    private static final Map<String, String> CODIGO_POR_OPERACION = Map.of("1002", "004", "1003", "028", "1004", "027");
    public static final String MEDIO_PAGO_DEPOSITO = "001";

    public Detraccion {
        if (codigoBienServicio == null || !CatalogoSunat.porId("54").orElseThrow().contiene(codigoBienServicio))
            throw new DomainException("DETRACCION_INVALIDA", "3033 - El código de bien o servicio sujeto a detracción no existe en el catálogo 54: " + codigoBienServicio);
        if (porcentaje == null || porcentaje.signum() <= 0 || porcentaje.compareTo(new BigDecimal("100")) >= 0 || porcentaje.scale() > 5)
            throw new DomainException("DETRACCION_INVALIDA", "El porcentaje de detracción debe ser mayor que 0 y menor que 100, con hasta 5 decimales");
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2)
            throw new DomainException("DETRACCION_INVALIDA", "3037 - El monto de la detracción debe ser positivo con hasta 2 decimales");
        medioPago = medioPago == null || medioPago.isBlank() ? MEDIO_PAGO_DEPOSITO : medioPago;
        if (!CatalogoSunat.porId("59").orElseThrow().contiene(medioPago))
            throw new DomainException("DETRACCION_INVALIDA", "3174 - El medio de pago no está en el catálogo 59: " + medioPago);
        // La cuenta puede venir vacía y completarse con la configurada en la empresa (conCuenta); sin ninguna, validarContra la exige.
        cuentaBancoNacion = cuentaBancoNacion == null || cuentaBancoNacion.isBlank() ? null : cuentaBancoNacion.trim();
    }

    public boolean sinCuenta() { return cuentaBancoNacion == null; }

    /** Completa la cuenta del Banco de la Nación con la de la empresa cuando la factura no la indica (regla 3034). */
    public Detraccion conCuenta(String cuentaEmpresa) {
        if (cuentaEmpresa == null || cuentaEmpresa.isBlank())
            throw new DomainException("DETRACCION_INVALIDA", "3034 - Indique cuenta_banco_nacion o configure la cuenta de detracciones de la empresa");
        return new Detraccion(codigoBienServicio, porcentaje, monto, cuentaEmpresa, medioPago);
    }

    /**
     * Monto del depósito para una factura en soles: importe total × porcentaje, redondeado al sol (el SPOT no admite
     * céntimos). Para otras monedas el emisor debe enviar el monto en soles al tipo de cambio que corresponda.
     */
    public static BigDecimal montoSobre(BigDecimal total, BigDecimal porcentaje) {
        return total.multiply(porcentaje).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).setScale(2);
    }

    /** Coherencia con el tipo de operación (3127/3128/3129) y con la moneda (el monto es en PEN: si la factura no es en soles, tiene que venir). */
    void validarContra(String tipoOperacion) {
        if (sinCuenta())
            throw new DomainException("DETRACCION_INVALIDA", "3034 - Debe indicar el número de cuenta de detracciones en el Banco de la Nación");
        if (!TIPOS_OPERACION.contains(tipoOperacion))
            throw new DomainException("DETRACCION_INVALIDA", "3128 - Una factura con detracción debe usar tipo de operación 1001, 1002, 1003 o 1004 (catálogo 51), no " + tipoOperacion);
        String fijo = CODIGO_POR_OPERACION.get(tipoOperacion);
        if (fijo != null && !fijo.equals(codigoBienServicio))
            throw new DomainException("DETRACCION_INVALIDA", "3129 - El tipo de operación " + tipoOperacion + " exige el código de bien/servicio " + fijo);
    }

    public String descripcionBienServicio() { return CatalogoSunat.descripcion("54", codigoBienServicio).orElse(""); }
}
