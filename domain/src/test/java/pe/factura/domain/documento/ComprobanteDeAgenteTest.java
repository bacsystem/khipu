package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.DesignacionDeAgente;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprobantes de retención (20) y de percepción (40), #40 (hojas Retenciones1_0 y Percepciones1_0): los importes que calcula khipu, cada regla que aplica antes de numerar con su
 * código y lo que todavía no emite.
 */
class ComprobanteDeAgenteTest {
    static final String RUC_AGENTE = "20100066603";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.of(2026, 10, 20);
    static final Receptor PROVEEDOR = new Receptor("6", "20131312955", "PROVEEDOR DEL NORTE S.A.C.", "Av. Grau 123, Piura");
    static final Receptor CLIENTE = new Receptor("6", "20601234565", "CLIENTE MAYORISTA S.A.C.", null);

    static ComprobanteDeAgente.Documento factura(String numero, String total, String pago) {
        return new ComprobanteDeAgente.Documento("01", numero, LocalDate.of(2026, 10, 5), new BigDecimal(total), "PEN", 1, LocalDate.of(2026, 10, 20), new BigDecimal(pago), null, null);
    }

    static ComprobanteDeAgente.Documento enDolares(String numero, String total, String pago, String tipoCambio) {
        return new ComprobanteDeAgente.Documento("01", numero, LocalDate.of(2026, 10, 5), new BigDecimal(total), "USD", 1, LocalDate.of(2026, 10, 18), new BigDecimal(pago), new BigDecimal(tipoCambio), null);
    }

    private Comprobante retencion(ComprobanteDeAgente.Documento... docs) {
        return Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(docs))).crear(RUC_AGENTE, clock);
    }

    private Comprobante percepcion(String regimen, ComprobanteDeAgente.Documento... docs) {
        return Comprobante.deAgente(tenant, "P001", hoy, CLIENTE, new ComprobanteDeAgente(TipoDocumento.PERCEPCION, regimen, null, List.of(docs))).crear(RUC_AGENTE, clock);
    }

    private static void rechaza(Runnable r, String codigo, String regla) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).hasMessageStartingWith(regla).extracting("codigo").isEqualTo(codigo);
    }

    @Test void laRetencionDelTresPorCientoDescuentaLoRetenidoDeLoPagado() {
        // Dos facturas del proveedor pagadas en soles: 3 % de 1180.00 = 35.40 y de 590.00 = 17.70; se le paga 1144.60 + 572.30.
        Comprobante c = retencion(factura("F001-123", "1180.00", "1180.00"), factura("E001-45", "590.00", "590.00"));
        assertThat(c.tipo()).isEqualTo(TipoDocumento.RETENCION);
        assertThat(c.esDeAgente()).isTrue();
        assertThat(c.moneda()).as("todo lo retenido es en soles (2728)").isEqualTo("PEN");
        assertThat(c.items()).isEmpty();
        assertThat(c.tipoOperacion()).as("no tiene tipo de operación").isNull();
        assertThat(c.agente().tasa()).as("la del catálogo 23").isEqualByComparingTo("3");
        assertThat(c.agente().tasaXml()).isEqualByComparingTo("3.00");
        assertThat(c.agente().documentos()).extracting(d -> d.importeDelAgente(c.agente().tasa())).containsExactly(new BigDecimal("35.40"), new BigDecimal("17.70"));
        assertThat(c.agente().documentos()).extracting(d -> d.neto(TipoDocumento.RETENCION, c.agente().tasa())).containsExactly(new BigDecimal("1144.60"), new BigDecimal("572.30"));
        assertThat(c.totales().total()).as("importe total retenido (2628)").isEqualByComparingTo("53.10");
        assertThat(c.importeNetoDeAgente()).as("importe total pagado (2629)").isEqualByComparingTo("1716.90");
        assertThat(c.consultableEnGetStatusCdr()).isFalse();
    }

    /** 2623: en otra moneda el pago se convierte con su tipo de cambio; lo retenido y el neto son en soles. */
    @Test void unaFacturaEnDolaresSeRetieneEnSolesConSuTipoDeCambio() {
        Comprobante c = retencion(enDolares("F001-77", "1000.00", "1000.00", "3.756"));
        ComprobanteDeAgente.Documento d = c.agente().documentos().get(0);
        assertThat(d.pagoEnSoles()).isEqualByComparingTo("3756.00");
        assertThat(d.importeDelAgente(c.agente().tasa())).isEqualByComparingTo("112.68");
        assertThat(d.neto(TipoDocumento.RETENCION, c.agente().tasa())).isEqualByComparingTo("3643.32");
        assertThat(d.fechaTipoCambio()).as("sin fecha, la del pago (2722)").isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(c.totales().total()).isEqualByComparingTo("112.68");
    }

    @Test void laPercepcionSeSumaALoCobrado() {
        // Venta interna 2 %: de un cobro de 1000.00 se perciben 20.00 y el cliente paga 1020.00.
        Comprobante c = percepcion("01", factura("F001-9", "1000.00", "1000.00"));
        assertThat(c.tipo()).isEqualTo(TipoDocumento.PERCEPCION);
        assertThat(c.agente().tasa()).as("la del catálogo 22").isEqualByComparingTo("2");
        assertThat(c.totales().total()).as("importe total percibido (2667)").isEqualByComparingTo("20.00");
        assertThat(c.importeNetoDeAgente()).as("importe total cobrado (2668)").isEqualByComparingTo("1020.00");
    }

    @ParameterizedTest
    @CsvSource({"01, 2, 20.00", "02, 1, 10.00", "03, 0.5, 5.00"})
    void cadaRegimenDePercepcionTieneSuTasa(String regimen, String tasa, String percibido) {
        Comprobante c = percepcion(regimen, factura("F001-9", "1000.00", "1000.00"));
        assertThat(c.agente().tasa()).isEqualByComparingTo(tasa);
        assertThat(c.totales().total()).isEqualByComparingTo(percibido);
    }

    /** e-beta responde 0306 a toda retención con PayableRoundingAmount (2026-10-20): khipu no lo emite; cero es lo mismo que no informarlo. */
    @Test void todaviaNoSeEmiteConRedondeo() {
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100.10", "100.10"))))
                .redondeo(new BigDecimal("-0.10")).crear(RUC_AGENTE, clock), "REDONDEO_INVALIDO", "khipu todavía no emite el redondeo");
        Comprobante c = Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100.10", "100.10"))))
                .redondeo(BigDecimal.ZERO).crear(RUC_AGENTE, clock);
        assertThat(c.totales().tieneRedondeo()).isFalse();
        assertThat(c.importeNetoDeAgente()).isEqualByComparingTo("97.10");
    }

    @Test void regimenYTasaSonLosDelCatalogo() {
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "03", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "RETENCION_INVALIDA", "2618");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", new BigDecimal("6"), List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "RETENCION_INVALIDA", "2619");
        rechaza(() -> Comprobante.deAgente(tenant, "P001", hoy, CLIENTE, new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "04", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "PERCEPCION_INVALIDA", "2602");
        rechaza(() -> Comprobante.deAgente(tenant, "P001", hoy, CLIENTE, new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", new BigDecimal("1"), List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "PERCEPCION_INVALIDA", "2603");
        // La tasa enviada que coincide con la del catálogo pasa, con o sin decimales.
        assertThat(Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", new BigDecimal("3.00"), List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock).totales().total()).isEqualByComparingTo("3.00");
    }

    /** 2985: la tasa del 6 % (régimen 02) solo para comprobantes emitidos hasta el 28/02/2014. */
    @Test void laRetencionDelSeisPorCientoEsSoloDeComprobantesHasta2014() {
        ComprobanteDeAgente.Documento reciente = factura("F001-1", "100", "100");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "02", null, List.of(reciente))).crear(RUC_AGENTE, clock),
                "RETENCION_INVALIDA", "2985");
        ComprobanteDeAgente.Documento antiguo = new ComprobanteDeAgente.Documento("01", "0001-345", LocalDate.of(2014, 2, 28), new BigDecimal("100"), "PEN", 1, hoy, new BigDecimal("100"), null, null);
        Comprobante c = Comprobante.deAgente(tenant, "R001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "02", null, List.of(antiguo))).crear(RUC_AGENTE, clock);
        assertThat(c.totales().total()).isEqualByComparingTo("6.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"07", "20", "03", "09"})
    void laRetencionSoloAdmiteDocumentosConPago(String tipo) {
        ComprobanteDeAgente.Documento d = new ComprobanteDeAgente.Documento(tipo, "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, hoy, new BigDecimal("100"), null, null);
        rechaza(() -> retencion(d), "RETENCION_INVALIDA", "2692");
    }

    @Test void laPercepcionAdmiteBoletasYTickets() {
        ComprobanteDeAgente.Documento boleta = new ComprobanteDeAgente.Documento("03", "B001-15", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, hoy, new BigDecimal("100"), null, null);
        ComprobanteDeAgente.Documento ticket = new ComprobanteDeAgente.Documento("12", "FFCF123456-87", LocalDate.of(2026, 10, 5), new BigDecimal("50"), "PEN", 1, hoy, new BigDecimal("50"), null, null);
        assertThat(percepcion("01", boleta, ticket).totales().total()).isEqualByComparingTo("3.00");
        rechaza(() -> percepcion("01", new ComprobanteDeAgente.Documento("40", "P001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, hoy, new BigDecimal("100"), null, null)),
                "PERCEPCION_INVALIDA", "2692");
    }

    @ParameterizedTest
    @ValueSource(strings = {"B001-1", "F001-0", "F001-123456789", "F01-1", "XAC1-1", ""})
    void elNumeroDelDocumentoDeUnaRetencionTieneElFormatoDeLaHoja(String numero) {
        rechaza(() -> retencion(factura(numero, "100", "100")), "RETENCION_INVALIDA", numero.isEmpty() ? "2693" : "2694");
    }

    /** 2625: un pago del mismo mes que la retención no es anterior a la emisión del documento; uno de otro mes es del mes de la retención; ninguno posterior a ella. */
    @Test void laFechaDePagoVaEntreElDocumentoOElInicioDelMesYLaRetencion() {
        var antesDelDocumento = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 4), new BigDecimal("100"), null, null);
        rechaza(() -> retencion(antesDelDocumento), "RETENCION_INVALIDA", "2625");
        var mesAnterior = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 8, 5), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 9, 30), new BigDecimal("100"), null, null);
        rechaza(() -> retencion(mesAnterior), "RETENCION_INVALIDA", "2625");
        var despues = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 21), new BigDecimal("100"), null, null);
        rechaza(() -> retencion(despues), "RETENCION_INVALIDA", "2625");
        var documentoAntiguoPagadoEnElMes = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 8, 5), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 1), new BigDecimal("100"), null, null);
        assertThat(retencion(documentoAntiguoPagadoEnElMes).totales().total()).isEqualByComparingTo("3.00");
        rechaza(() -> percepcion("01", antesDelDocumento), "PERCEPCION_INVALIDA", "2612");
    }

    @Test void losPagosDeUnComprobanteSonDeUnMismoMesYNoSeRepiten() {
        var octubre = factura("F001-1", "100", "100");
        var mismoPago = factura("F001-1", "100", "50");
        rechaza(() -> retencion(octubre, mismoPago), "RETENCION_INVALIDA", "2626");
        var segundaCuota = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("200"), "PEN", 2, LocalDate.of(2026, 10, 20), new BigDecimal("100"), null, null);
        var primeraCuota = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("200"), "PEN", 1, LocalDate.of(2026, 10, 10), new BigDecimal("100"), null, null);
        assertThat(retencion(primeraCuota, segundaCuota).totales().total()).as("dos cuotas del mismo documento").isEqualByComparingTo("6.00");
        // Un pago de octubre en una retención del 2026-11-02: la 2625 lo deja fuera (un documento de otro mes se paga en el mes de la retención), y con ella la 2661 (pagos de un mismo mes).
        Clock noviembre = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneId.of("America/Lima"));
        var pagoDeOctubre = new ComprobanteDeAgente.Documento("01", "F001-2", LocalDate.of(2026, 10, 30), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 31), new BigDecimal("100"), null, null);
        var pagoDeNoviembre = new ComprobanteDeAgente.Documento("01", "F001-3", LocalDate.of(2026, 11, 1), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 11, 2), new BigDecimal("100"), null, null);
        rechaza(() -> Comprobante.deAgente(tenant, "R001", LocalDate.of(2026, 11, 2), PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(pagoDeNoviembre, pagoDeOctubre)))
                .crear(RUC_AGENTE, noviembre), "RETENCION_INVALIDA", "2625");
    }

    @Test void importesYTipoDeCambio() {
        rechaza(() -> retencion(factura("F001-1", "0", "0")), "RETENCION_INVALIDA", "2696");
        rechaza(() -> retencion(factura("F001-1", "100", "0")), "RETENCION_INVALIDA", "2736");
        rechaza(() -> retencion(factura("F001-1", "100.001", "100")), "RETENCION_INVALIDA", "2696");
        rechaza(() -> retencion(factura("F001-1", "100", "100.50")), "RETENCION_INVALIDA", "El pago de F001-1");
        rechaza(() -> retencion(enDolares("F001-1", "100", "100", "0")), "RETENCION_INVALIDA", "2716");
        rechaza(() -> retencion(enDolares("F001-1", "100", "100", "3.1234567")), "RETENCION_INVALIDA", "2716");
        var sinTipoDeCambio = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "USD", 1, hoy, new BigDecimal("100"), null, null);
        rechaza(() -> retencion(sinTipoDeCambio), "RETENCION_INVALIDA", "2721");
        var solesConTipoDeCambio = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 1, hoy, new BigDecimal("100"), new BigDecimal("3.7"), null);
        rechaza(() -> retencion(solesConTipoDeCambio), "RETENCION_INVALIDA", "F001-1 es en soles");
        // 3 % de 0.10 redondea a 0.00: SUNAT no admite el retenido en cero (2740).
        rechaza(() -> retencion(factura("F001-1", "0.10", "0.10")), "RETENCION_INVALIDA", "2740");
        rechaza(() -> percepcion("03", factura("F001-1", "0.90", "0.90")), "PERCEPCION_INVALIDA", "2705");
        var pagoCero = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 5), new BigDecimal("100"), "PEN", 0, hoy, new BigDecimal("100"), null, null);
        rechaza(() -> retencion(pagoCero), "RETENCION_INVALIDA", "2734");
        rechaza(() -> percepcion("01", pagoCero), "PERCEPCION_INVALIDA", "2698");
    }

    @Test void elProveedorTieneRucYNoEsElAgente() {
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, new Receptor("1", "45678912", "JUAN PEREZ", null), new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2511");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, new Receptor("6", "2013131295", "X SAC", null), new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2724");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, new Receptor("6", RUC_AGENTE, "YO MISMO SAC", null), new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2620");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, new Receptor("6", "20131312955", " ", null), new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2134");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, null, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100"))))
                .crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2723");
    }

    @Test void elClienteDeUnaPercepcionPuedeTenerDni() {
        Comprobante c = Comprobante.deAgente(tenant, "P001", hoy, new Receptor("1", "45678912", "JUAN PEREZ", null),
                new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock);
        assertThat(c.receptor().tipoDoc()).isEqualTo("1");
        rechaza(() -> Comprobante.deAgente(tenant, "P001", hoy, new Receptor("1", "4567891", "JUAN PEREZ", null),
                new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2680");
        rechaza(() -> Comprobante.deAgente(tenant, "P001", hoy, new Receptor("6", RUC_AGENTE, "YO", null),
                new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock), "RECEPTOR_INVALIDO", "2604");
    }

    @Test void laSerieYElPlazoSonLosDeSuTipo() {
        rechaza(() -> Comprobante.deAgente(tenant, "F001", hoy, PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "SERIE_INVALIDA", "1001");
        rechaza(() -> Comprobante.deAgente(tenant, "R001", hoy, CLIENTE, new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", null, List.of(factura("F001-1", "100", "100")))).crear(RUC_AGENTE, clock),
                "SERIE_INVALIDA", "1001");
        // 9 días calendario (RS 274-2015): el 11 se emite, el 10 ya no (2600).
        var doc = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 1), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 10), new BigDecimal("100"), null, null);
        rechaza(() -> Comprobante.deAgente(tenant, "R001", LocalDate.of(2026, 10, 10), PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(doc))).crear(RUC_AGENTE, clock),
                "FECHA_INVALIDA", "2600");
        var doc11 = new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 10, 1), new BigDecimal("100"), "PEN", 1, LocalDate.of(2026, 10, 11), new BigDecimal("100"), null, null);
        Comprobante c = Comprobante.deAgente(tenant, "R001", LocalDate.of(2026, 10, 11), PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(doc11))).crear(RUC_AGENTE, clock);
        assertThat(c.fechaLimiteEnvio()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(c.fueraDePlazo(LocalDate.of(2026, 10, 21))).isTrue();
        assertThat(PlazoEnvio.de(TipoDocumento.PERCEPCION, null).motivoDeRechazo(hoy)).startsWith("2600 - El comprobante fue enviado fuera del plazo permitido");
        rechaza(() -> retencion(), "RETENCION_INVALIDA", "El comprobante de retención lleva al menos un documento");
    }

    /** Un solo origen de verdad: los datos del agente van si y solo si el tipo es 20 o 40, y del mismo tipo que el comprobante. */
    @Test void elTipoYLosDatosDelAgenteViajanJuntos() {
        ComprobanteDeAgente deRetencion = new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100", "100")));
        assertThatThrownBy(() -> Comprobante.persistido(UUID.randomUUID(), tenant, TipoDocumento.PERCEPCION, "P001", 1L, hoy, EstadoDocumento.FIRMADO, CLIENTE, List.of())
                .agente(deRetencion).rehidratar()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Comprobante.persistido(UUID.randomUUID(), tenant, TipoDocumento.RETENCION, "R001", 1L, hoy, EstadoDocumento.FIRMADO, PROVEEDOR, List.of())
                .rehidratar()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Comprobante.persistido(UUID.randomUUID(), tenant, TipoDocumento.FACTURA, "F001", 1L, hoy, EstadoDocumento.FIRMADO, CLIENTE, List.of())
                .agente(deRetencion).rehidratar()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ComprobanteDeAgente(TipoDocumento.FACTURA, "01", null, List.of())).isInstanceOf(IllegalArgumentException.class);
        Comprobante r = Comprobante.persistido(UUID.randomUUID(), tenant, TipoDocumento.RETENCION, "R001", 1L, hoy, EstadoDocumento.FIRMADO, PROVEEDOR, List.of())
                .tipoOperacion(null).agente(deRetencion).rehidratar();
        assertThat(r.totales().total()).isEqualByComparingTo("3.00");
    }

    @Test void soloSeEmiteConLaDesignacionDeAgente() {
        assertThatThrownBy(() -> DesignacionDeAgente.NINGUNA.exigirPara(TipoDocumento.RETENCION)).hasMessageStartingWith("2617").extracting("codigo").isEqualTo("AGENTE_NO_DESIGNADO");
        assertThatThrownBy(() -> DesignacionDeAgente.NINGUNA.exigirPara(TipoDocumento.PERCEPCION)).hasMessageStartingWith("4285").extracting("codigo").isEqualTo("AGENTE_NO_DESIGNADO");
        assertThatThrownBy(() -> new DesignacionDeAgente(false, true).exigirPara(TipoDocumento.RETENCION)).hasMessageStartingWith("2617");
        new DesignacionDeAgente(true, false).exigirPara(TipoDocumento.RETENCION);
        new DesignacionDeAgente(false, true).exigirPara(TipoDocumento.PERCEPCION);
        DesignacionDeAgente.NINGUNA.exigirPara(TipoDocumento.FACTURA);
    }

    /** Se revierten en un resumen de reversiones (RR), sin el plazo de 7 días de la comunicación de baja, y solo aceptadas (2750). */
    @Test void seRevierteEnUnResumenDeReversiones() {
        Comprobante c = retencion(factura("F001-1", "100", "100"));
        c.asignarNumero(7, RUC_AGENTE);
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-20-R001-7");
        rechaza(() -> ComunicacionBaja.crear(c, 1, "Pago anulado", clock), "BAJA_INVALIDA", "2750");
        c.firmar("H", "k.xml");
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptado", List.of()), "cdr.zip");
        Clock unMesDespues = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneId.of("America/Lima"));
        ComunicacionBaja rr = ComunicacionBaja.crear(c, 3, "Pago anulado", unMesDespues);
        assertThat(rr.reversion()).isTrue();
        assertThat(rr.resumenDiario()).isFalse();
        assertThat(rr.identificador()).isEqualTo("RR-20261125-3");
        assertThat(rr.nombreArchivo(RUC_AGENTE)).isEqualTo("20100066603-RR-20261125-3");
        c.anular();
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(c.eventosPendientes().get(c.eventosPendientes().size() - 1).detalle()).contains("resumen de reversiones");
    }
}
