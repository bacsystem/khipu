package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lo propio de un comprobante de retención (20) o de percepción (40) (#40; hojas Retenciones1_0 y Percepciones1_0): el régimen y su tasa, y los comprobantes que el agente paga
 * (retención) o cobra (percepción), cada uno con su pago. La contraparte —el proveedor al que se retiene o el cliente al que se percibe— va en {@link Comprobante#receptor()}.
 * <p>
 * Todo importe que el agente retiene o percibe, y el neto de cada pago, es en soles (2742, 2748, 2707, 2713): un documento en otra moneda se convierte con su tipo de cambio
 * ({@code cac:ExchangeRate}, 2719–2722). Por documento:
 * <ul>
 *   <li>retención: importe retenido = pago × tipo de cambio × tasa; neto pagado = pago × tipo de cambio − retenido (2623, ±1);</li>
 *   <li>percepción: importe percibido = pago × tipo de cambio × tasa; neto cobrado = pago × tipo de cambio + percibido (2608, ±1).</li>
 * </ul>
 * Los totales son la suma de los importes retenidos/percibidos (2628/2667) y la de los netos más el redondeo (2629/2668). khipu admite los documentos que llevan un pago
 * (01 factura, 08 nota de débito, 12 ticket y, en la percepción, 03 boleta); la nota de crédito (07) y el propio 20/40, que la hoja deja sin pago y fuera de los totales, todavía no.
 *
 * @param tipo {@link TipoDocumento#RETENCION} o {@link TipoDocumento#PERCEPCION}: el mismo del comprobante (lo exige su constructor)
 * @param regimen catálogo 23 (retención: 01 tasa 3 %, 02 tasa 6 %) o catálogo 22 (percepción: 01 venta interna 2 %, 02 combustible 1 %, 03 tasa especial 0.5 %)
 * @param tasa la del régimen en el catálogo, en porcentaje; nula al crear = la del catálogo
 */
public record ComprobanteDeAgente(TipoDocumento tipo, String regimen, BigDecimal tasa, List<Documento> documentos) {

    /** La retención del 6 % (régimen 02) solo se aplica a comprobantes emitidos hasta esta fecha (2985). */
    public static final LocalDate FIN_TASA_6 = LocalDate.of(2014, 2, 28);
    /** Tope de documentos por comprobante: SUNAT no fija uno; khipu el mismo que las líneas de un comprobante, para que el XML no crezca sin límite. */
    public static final int MAX_DOCUMENTOS = Comprobante.MAX_LINEAS;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal TOPE_IMPORTE = new BigDecimal("1000000000000");   // n(12,2): menos de 10^12
    /** Serie-número de un documento distinto del ticket (2694): factura electrónica o física, retención/percepción, boleta en la percepción. */
    private static final Pattern NUMERO_RETENCION = Pattern.compile("(E001|((F|R)[A-Z0-9]{3})|([0-9]{4}))-(?!0+$)([0-9]{1,8})");
    private static final Pattern NUMERO_PERCEPCION = Pattern.compile("(E001|EB01|((F|P|B)[A-Z0-9]{3})|([0-9]{4}))-(?!0+$)([0-9]{1,8})");
    /** El ticket o cinta de máquina registradora (12): su serie es la de la máquina (2694). */
    private static final Pattern NUMERO_TICKET = Pattern.compile("[a-zA-Z0-9-]{1,20}-[0-9]{1,20}");

    public ComprobanteDeAgente {
        if (tipo == null || !tipo.deAgente()) throw new IllegalArgumentException("Un comprobante de agente es una retención (20) o una percepción (40): " + tipo);
        regimen = regimen == null ? null : regimen.strip();
        if (tasa == null) tasa = tasaDelCatalogo(tipo, regimen);
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
    }

    public boolean esRetencion() { return tipo == TipoDocumento.RETENCION; }

    /** La tasa del régimen en el catálogo 23 (retención) o 22 (percepción); nula si el régimen no está en él. */
    static BigDecimal tasaDelCatalogo(TipoDocumento tipo, String regimen) {
        if (regimen == null) return null;
        if (tipo == TipoDocumento.RETENCION) {
            // El catálogo 23 publica la tasa en la descripción («Tasa 3%»).
            return CatalogoSunat.porId("23").flatMap(c -> c.entrada(regimen)).map(e -> e.descripcion().replaceAll("[^0-9.]", ""))
                    .filter(s -> !s.isEmpty()).map(BigDecimal::new).orElse(null);
        }
        return CatalogoSunat.porId("22").flatMap(c -> c.entrada(regimen)).map(e -> e.extra().get("Porcentaje %")).map(BigDecimal::new).orElse(null);
    }

    /** La tasa como la lleva el XML (n(1,2)): {@code 3.00}, {@code 0.50}. */
    public BigDecimal tasaXml() { return tasa.setScale(2, RoundingMode.HALF_UP); }

    /** Descripción del régimen en su catálogo, para el PDF. */
    public String descripcionRegimen() { return CatalogoSunat.descripcion(esRetencion() ? "23" : "22", regimen).orElse(""); }

    /** Importe total retenido o percibido (cbc:TotalInvoiceAmount, en soles): la suma de lo de cada documento (2628/2667). */
    public BigDecimal importeTotal() { return documentos.stream().map(d -> d.importeDelAgente(tasa)).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add); }

    /** Suma de los netos pagados o cobrados, sin el redondeo (sac:SUNATTotalPaid/SUNATTotalCashed sin PayableRoundingAmount; 2629/2668). */
    public BigDecimal netoTotal() { return documentos.stream().map(d -> d.neto(tipo, tasa)).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add); }

    /**
     * Las reglas de Retenciones1_0 / Percepciones1_0 que khipu puede comprobar antes de numerar, con el código de cada una: régimen y tasa (2618/2619, 2602/2603), tasa del 6 %
     * (2985), cada documento (tipo 2692, número 2694, importe 2696, pago 2733–2737/2697–2702, fechas 2625/2612 —que implican 2661/2659—, tipo de cambio 2716–2722, repetidos 2626) y totales
     * positivos (2669, 2730/2687). Solo al emitir: lo ya guardado se rehidrata tal cual.
     */
    void validar(LocalDate fechaEmision) {
        boolean ret = esRetencion();
        if (regimen == null || regimen.isEmpty() || tasaDelCatalogo(tipo, regimen) == null)
            throw new DomainException(codigo(), (ret ? "2618 - El régimen de retención es del catálogo 23 (01 tasa 3 %, 02 tasa 6 %)" : "2602 - El régimen de percepción es del catálogo 22 (01 venta interna 2 %, 02 combustible 1 %, 03 tasa especial 0.5 %)")
                    + "; recibido " + regimen);
        BigDecimal delCatalogo = tasaDelCatalogo(tipo, regimen);
        if (tasa.compareTo(delCatalogo) != 0)
            throw new DomainException(codigo(), (ret ? "2619 - La tasa de retención" : "2603 - La tasa de percepción") + " del régimen " + regimen + " es " + delCatalogo.stripTrailingZeros().toPlainString()
                    + " % (catálogo " + (ret ? "23" : "22") + "), no " + tasa.stripTrailingZeros().toPlainString() + " %");
        if (documentos.isEmpty())
            throw new DomainException(codigo(), "El comprobante de " + nombre() + " lleva al menos un documento relacionado (sac:SUNAT" + (ret ? "Retention" : "Perception") + "DocumentReference)");
        if (documentos.size() > MAX_DOCUMENTOS)
            throw new DomainException(codigo(), "khipu admite hasta " + MAX_DOCUMENTOS + " documentos relacionados por comprobante de " + nombre() + "; recibidos " + documentos.size() + ": divídalos en más de uno");
        // 2661/2659 (todas las fechas de pago de un mismo mes) no se comprueba aparte: la 2625/2612 de cada documento ya deja cada pago en el mes de la emisión del comprobante.
        Set<String> pagos = new HashSet<>();
        for (Documento d : documentos) {
            d.validar(this, fechaEmision);
            if (!pagos.add(d.numero() + "#" + d.numeroPago()))
                throw new DomainException(codigo(), "2626 - El documento " + d.numero() + " repite el número de " + (ret ? "pago " : "cobro ") + d.numeroPago() + " en el mismo comprobante");
        }
        if (importeTotal().signum() <= 0)
            throw new DomainException(codigo(), "2669 - El importe total " + (ret ? "retenido" : "percibido") + " debe ser mayor que cero");
        if (netoTotal().signum() <= 0)
            throw new DomainException(codigo(), (ret ? "2730 - El importe total pagado" : "2687 - El importe total cobrado") + " debe ser mayor que cero");
    }

    /**
     * El redondeo del total pagado/cobrado (PayableRoundingAmount): las hojas lo admiten hasta ±1 (3303) y el XSD lo tiene, pero e-beta (2026-10-20) responde 0306 «No se puede leer
     * (parsear) el archivo XML» a toda retención que lo lleva, positivo o negativo, en soles o con dólares, y acepta la misma sin él. khipu no lo emite hasta poder probarlo.
     */
    static void validarRedondeo(BigDecimal redondeo, boolean retencion) {
        if (redondeo != null && redondeo.signum() != 0)
            throw new DomainException("REDONDEO_INVALIDO", "khipu todavía no emite el redondeo del importe total " + (retencion ? "pagado" : "cobrado")
                    + " en un comprobante de " + (retencion ? "retención" : "percepción") + ": SUNAT (e-beta) no lee el XML que lo lleva (0306), aunque la hoja lo admita hasta ±1.00 (3303)");
    }

    String codigo() { return esRetencion() ? "RETENCION_INVALIDA" : "PERCEPCION_INVALIDA"; }
    String nombre() { return esRetencion() ? "retención" : "percepción"; }

    /** Tipos de documento relacionado que khipu admite (catálogo 01): los que llevan pago. La hoja admite además el 07 y el propio 20/40 (2692). */
    static final Set<String> TIPOS_RETENCION = Set.of("01", "08", "12");
    static final Set<String> TIPOS_PERCEPCION = Set.of("01", "03", "08", "12");

    /**
     * Un comprobante que el agente paga o cobra, con su pago. {@code importeTotal} y {@code importePago} van en la {@code moneda} del documento; {@code tipoCambio} (soles por unidad, n(4,6))
     * y su fecha solo fuera de soles (sin fecha, la del pago).
     *
     * @param numeroPago número correlativo del pago o cobro de ese documento (cac:Payment/cbc:ID, hasta 9 dígitos): 1 el primero, 2 el segundo… de un pago en cuotas
     */
    public record Documento(String tipo, String numero, LocalDate fechaEmision, BigDecimal importeTotal, String moneda,
                            long numeroPago, LocalDate fechaPago, BigDecimal importePago, BigDecimal tipoCambio, LocalDate fechaTipoCambio) {
        public Documento {
            tipo = tipo == null ? null : tipo.strip();
            numero = numero == null ? null : numero.strip();
            moneda = moneda == null ? Monedas.SOLES : moneda.strip().toUpperCase(java.util.Locale.ROOT);
            if (tipoCambio != null && fechaTipoCambio == null) fechaTipoCambio = fechaPago;
        }

        public boolean enSoles() { return Monedas.SOLES.equals(moneda); }

        /** El pago en soles, al céntimo: el mismo si el documento es en soles. */
        public BigDecimal pagoEnSoles() {
            return (enSoles() || tipoCambio == null ? importePago : importePago.multiply(tipoCambio)).setScale(2, RoundingMode.HALF_UP);
        }

        /** Lo que el agente retiene o percibe de este pago, en soles: pago × tipo de cambio × tasa, al céntimo (2623/2608 admiten ±1). */
        public BigDecimal importeDelAgente(BigDecimal tasa) {
            BigDecimal base = enSoles() || tipoCambio == null ? importePago : importePago.multiply(tipoCambio);
            return base.multiply(tasa).divide(CIEN, 2, RoundingMode.HALF_UP);
        }

        /** Neto pagado (retención: pago − retenido) o cobrado (percepción: pago + percibido), en soles. */
        public BigDecimal neto(TipoDocumento tipoAgente, BigDecimal tasa) {
            BigDecimal agente = importeDelAgente(tasa);
            return tipoAgente == TipoDocumento.RETENCION ? pagoEnSoles().subtract(agente) : pagoEnSoles().add(agente);
        }

        /** La fecha en que se retuvo o percibió: la del pago o cobro (sac:SUNATRetentionDate/SUNATPerceptionDate, sin validación SUNAT). */
        public LocalDate fechaDelAgente() { return fechaPago; }

        void validar(ComprobanteDeAgente c, LocalDate fechaEmisionAgente) {
            boolean ret = c.esRetencion();
            String cod = c.codigo();
            String doc = "El documento relacionado " + (numero == null ? "" : numero + " ");
            if (tipo == null || tipo.isEmpty())
                throw new DomainException(cod, "2691 - " + doc + "lleva su tipo (catálogo 01)");
            Set<String> admitidos = ret ? TIPOS_RETENCION : TIPOS_PERCEPCION;
            if (!admitidos.contains(tipo))
                throw new DomainException(cod, "2692 - " + doc + "es del tipo " + tipo + "; en un comprobante de " + c.nombre() + " khipu admite "
                        + String.join(", ", admitidos.stream().sorted().toList()) + ("07".equals(tipo) || c.tipo().codigo().equals(tipo)
                        ? " (la hoja admite también el " + tipo + ", que no lleva pago ni suma a los totales: khipu todavía no lo emite)" : " (catálogo 01)"));
            if (numero == null || numero.isEmpty())
                throw new DomainException(cod, "2693 - Falta la serie y número del documento relacionado");
            Pattern formato = "12".equals(tipo) ? NUMERO_TICKET : ret ? NUMERO_RETENCION : NUMERO_PERCEPCION;
            if (numero.length() > ("12".equals(tipo) ? 41 : 13) || !formato.matcher(numero).matches())
                throw new DomainException(cod, "2694 - " + doc + "no tiene el formato serie-número " + ("12".equals(tipo) ? "de un ticket (serie de la máquina-número)"
                        : ret ? "(F### o E001 electrónica, 4 dígitos física, R### retención; número de 1 a 8 dígitos distinto de cero)"
                        : "(F###, B###, E001, EB01 electrónica, 4 dígitos física, P### percepción; número de 1 a 8 dígitos distinto de cero)"));
            if (fechaEmision == null)
                throw new DomainException(cod, doc + "lleva su fecha de emisión");
            if (fechaEmision.isAfter(fechaEmisionAgente))
                throw new DomainException(cod, doc + "se emitió el " + fechaEmision + ", después que el comprobante de " + c.nombre() + " (" + fechaEmisionAgente + ")");
            if (ret && "02".equals(c.regimen()) && fechaEmision.isAfter(FIN_TASA_6))
                throw new DomainException(cod, "2985 - La retención del 6 % (régimen 02) solo se aplica a comprobantes emitidos hasta el " + FIN_TASA_6 + "; " + doc + "es del " + fechaEmision);
            exigirImporte(importeTotal, cod, "2696 - El importe total de " + numero);
            if (!Monedas.admitida(moneda))
                throw new DomainException(cod, "La moneda de " + numero + " es una del catálogo 02 que khipu admite (" + String.join(", ", Monedas.codigos()) + "); recibida " + moneda);
            if (numeroPago < 1 || numeroPago > 999_999_999L)
                throw new DomainException(cod, (ret ? "2734 - El número de pago" : "2698 - El número de cobro") + " de " + numero + " es un entero de 1 a 9 dígitos; recibido " + numeroPago);
            if (fechaPago == null)
                throw new DomainException(cod, (ret ? "2737 - Falta la fecha de pago" : "2702 - Falta la fecha de cobro") + " de " + numero);
            exigirFechaDePago(c, fechaEmisionAgente);
            exigirImporte(importePago, cod, (ret ? "2736 - El importe del pago sin retención" : "2700 - El importe del cobro sin percepción") + " de " + numero);
            if (importePago.compareTo(importeTotal) > 0)
                throw new DomainException(cod, "El " + (ret ? "pago" : "cobro") + " de " + numero + " (" + importePago + ") supera el importe total del documento (" + importeTotal + ")");
            if (enSoles()) {
                if (tipoCambio != null)
                    throw new DomainException(cod, numero + " es en soles: no lleva tipo de cambio (cac:ExchangeRate solo va fuera de PEN, 2719)");
            } else {
                if (tipoCambio == null)
                    throw new DomainException(cod, "2721 - " + numero + " es en " + moneda + ": lleva el tipo de cambio (soles por " + moneda + ") con que se convierte el pago");
                if (tipoCambio.signum() <= 0 || tipoCambio.scale() > 6 || tipoCambio.precision() - tipoCambio.scale() > 4)
                    throw new DomainException(cod, "2716 - El tipo de cambio de " + numero + " es positivo, de hasta 4 enteros y 6 decimales; recibido " + tipoCambio.toPlainString());
                if (fechaTipoCambio.isAfter(fechaEmisionAgente))
                    throw new DomainException(cod, "La fecha del tipo de cambio de " + numero + " (" + fechaTipoCambio + ") no puede ser posterior a la emisión del comprobante");
            }
            if (importeDelAgente(c.tasa()).signum() <= 0)
                throw new DomainException(cod, (ret ? "2740 - El importe retenido" : "2705 - El importe percibido") + " de " + numero + " (" + c.tasaXml() + " % de " + pagoEnSoles()
                        + ") redondea a 0.00: SUNAT no lo admite en cero");
        }

        /**
         * 2625 (retención) / 2612 (percepción): el pago es de la fecha de emisión del documento (si es del mismo mes que la retención) o del primer día del mes de la retención en
         * adelante (si es de un mes anterior), y nunca posterior a la emisión de la retención.
         */
        private void exigirFechaDePago(ComprobanteDeAgente c, LocalDate fechaEmisionAgente) {
            boolean ret = c.esRetencion();
            String regla = ret ? "2625 - La fecha de pago" : "2612 - La fecha de cobro";
            boolean mismoPeriodo = YearMonth.from(fechaEmision).equals(YearMonth.from(fechaEmisionAgente));
            LocalDate desde = mismoPeriodo ? fechaEmision : fechaEmisionAgente.withDayOfMonth(1);
            if (fechaPago.isBefore(desde) || fechaPago.isAfter(fechaEmisionAgente))
                throw new DomainException(c.codigo(), regla + " de " + numero + " (" + fechaPago + ") va del " + desde + " al " + fechaEmisionAgente + ": "
                        + (mismoPeriodo ? "no antes de la emisión del documento" : "el mes de la emisión del comprobante de " + c.nombre()) + " y no después de esta");
        }

        private static void exigirImporte(BigDecimal importe, String codigo, String que) {
            if (importe == null || importe.signum() <= 0 || importe.scale() > 2 || importe.compareTo(TOPE_IMPORTE) >= 0)
                throw new DomainException(codigo, que + " es positivo, de hasta 12 enteros y 2 decimales");
        }
    }
}
