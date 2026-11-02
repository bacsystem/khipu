package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

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
 * Liquidación de compra (04, #327; hoja LiquidacionCompra2_0): el comprador (el emisor) compra a una persona sin RUC, le retiene el IGV y el 1.5 % de renta, y le paga el
 * neto. Cada regla de la hoja que khipu aplica antes de numerar, con su código, y lo que todavía no emite, con un rechazo claro.
 */
class LiquidacionCompraTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.of(2026, 10, 20);
    private final Receptor vendedor = new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n, Cajamarca");
    private final LiquidacionCompra datos = new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12 carretera a Bambamarca", "060101", "02"), null);

    static Item linea(String descripcion, String cantidad, String precio, TipoAfectacionIgv afectacion) {
        return new Item("CAF-01", descripcion, "KGM", new BigDecimal(cantidad), new BigDecimal(precio), afectacion, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null);
    }

    private Comprobante.LiquidacionBuilder liquidacion(List<Item> items) { return liquidacion(vendedor, datos, items); }

    private Comprobante.LiquidacionBuilder liquidacion(Receptor v, LiquidacionCompra d, List<Item> items) {
        return Comprobante.liquidacionCompra(tenant, "L001", hoy, "PEN", null, v, d, items);
    }

    private static void rechaza(Runnable r, String codigo, String regla) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).hasMessageStartingWith(regla).extracting("codigo").isEqualTo(codigo);
    }

    @Test void unaGravadaRetieneElIgvYElUnoComaCincoPorCientoDeRentaYPagaElNeto() {
        // 10 kg a S/ 118 con IGV: valor 1000.00, IGV 180.00, subtotal 1180.00; el comprador retiene el IGV y 15.00 de renta y paga 985.00 (regla 4312).
        Comprobante c = liquidacion(List.of(linea("Café pergamino", "10", "118", TipoAfectacionIgv.GRAVADO))).crear(clock);
        assertThat(c.tipo()).isEqualTo(TipoDocumento.LIQUIDACION_COMPRA);
        assertThat(c.esLiquidacionCompra()).isTrue();
        assertThat(c.tipoOperacion()).as("0501 compra interna por defecto").isEqualTo("0501");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECIBIDO);
        assertThat(c.totales().totalValorVenta()).isEqualByComparingTo("1000.00");
        assertThat(c.totales().igv()).isEqualByComparingTo("180.00");
        assertThat(c.totales().totalPrecioVenta()).as("subtotal de la liquidación (TaxInclusiveAmount)").isEqualByComparingTo("1180.00");
        assertThat(c.retencionRenta()).isEqualByComparingTo("15.00");
        assertThat(c.liquidacion().baseRetencion(c.totales())).isEqualByComparingTo("1000.00");
        assertThat(c.totales().total()).as("importe neto (PayableAmount)").isEqualByComparingTo("985.00");
        assertThat(c.liquidacion().tasaRetencionRenta()).isEqualByComparingTo(LiquidacionCompra.TASA_RETENCION_RENTA);
        // La retención de renta no es la afectación de ningún ítem: los subtotales globales siguen siendo los del IGV.
        assertThat(c.totales().subtotales()).extracting(Totales.SubtotalTributo::tributo).containsExactly(Tributo.IGV);
        c.asignarNumero(1, "20100066603");
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-04-L001-1");
    }

    @Test void unaExoneradaNoTieneIgvQueRetener() {
        Comprobante c = liquidacion(List.of(linea("Papa blanca", "3", "50", TipoAfectacionIgv.EXONERADO))).crear(clock);
        assertThat(c.totales().exonerado()).isEqualByComparingTo("150.00");
        assertThat(c.totales().igv()).isEqualByComparingTo("0.00");
        assertThat(c.retencionRenta()).isEqualByComparingTo("2.25");
        assertThat(c.totales().total()).isEqualByComparingTo("147.75");
    }

    @Test void laRetencionGlobalEsLaSumaDeLaDeCadaLineaNoLaBasePorLaTasa() {
        // Tres líneas de 10.30: 0.1545 → 0.15 cada una, 0.45 en total; 30.90 × 1.5 % daría 0.46. SUNAT compara con la suma de las líneas (4346).
        Item l = linea("Maíz amarillo", "1", "10.30", TipoAfectacionIgv.INAFECTO);
        Comprobante c = liquidacion(List.of(l, l, l)).crear(clock);
        assertThat(c.totales().items()).extracting(c.liquidacion()::rentaDe).containsOnly(new BigDecimal("0.15"));
        assertThat(c.retencionRenta()).isEqualByComparingTo("0.45");
        assertThat(c.totales().total()).isEqualByComparingTo("30.45");
    }

    @Test void elRedondeoSeSumaAlNeto() {
        Comprobante c = liquidacion(List.of(linea("Papa", "3", "50", TipoAfectacionIgv.EXONERADO))).redondeo(new BigDecimal("0.25")).crear(clock);
        assertThat(c.totales().redondeo()).isEqualByComparingTo("0.25");
        assertThat(c.totales().total()).isEqualByComparingTo("148.00");
        rechaza(() -> liquidacion(List.of(linea("Papa", "3", "50", TipoAfectacionIgv.EXONERADO))).redondeo(new BigDecimal("1.01")).crear(clock), "REDONDEO_INVALIDO", "4314");
    }

    @Test void unTipoLiquidacionSinSusDatosONoLiquidacionConEllosNoSeRehidrata() {
        Comprobante c = liquidacion(List.of(linea("Café", "10", "118", TipoAfectacionIgv.GRAVADO))).crear(clock);
        assertThatThrownBy(() -> Comprobante.persistido(c.id(), tenant, TipoDocumento.LIQUIDACION_COMPRA, "L001", 1L, hoy, EstadoDocumento.FIRMADO, c.receptor(), c.items()).rehidratar())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tipo 04");
        assertThatThrownBy(() -> Comprobante.persistido(c.id(), tenant, TipoDocumento.FACTURA, "F001", 1L, hoy, EstadoDocumento.FIRMADO, c.receptor(), c.items()).liquidacion(c.liquidacion()).rehidratar())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tipo 01");
    }

    @Test void enDolaresConTipoDeCambio() {
        Comprobante c = Comprobante.liquidacionCompra(tenant, "L001", hoy, "USD", "0501", vendedor, datos, List.of(linea("Cacao", "100", "2.36", TipoAfectacionIgv.GRAVADO)))
                .tipoCambio(new BigDecimal("3.750")).crear(clock);
        assertThat(c.moneda()).isEqualTo("USD");
        assertThat(c.totales().total()).isEqualByComparingTo("197.00");   // 200.00 − 3.00 de renta
        assertThat(c.totalEnSoles()).contains(new BigDecimal("738.75"));
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy, "XXX", "0501", vendedor, datos, List.of(linea("Cacao", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "MONEDA_INVALIDA", "3088");
    }

    @Test void laSerieEmpiezaConL() {
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "F001", hoy, "PEN", null, vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "SERIE_INVALIDA", "1001");
        rechaza(() -> Comprobante.factura(tenant, "L001", hoy, "PEN", "0101", new Receptor("6", "20131312955", "SUNAT", null), List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "SERIE_INVALIDA", "Serie de factura");
    }

    @Test void seEnviaHastaElDiaSiguienteASuEmision() {
        assertThat(Comprobante.liquidacionCompra(tenant, "L001", hoy.minusDays(1), "PEN", null, vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock)
                .fechaLimiteEnvio()).isEqualTo(hoy);
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy.minusDays(2), "PEN", null, vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "FECHA_INVALIDA", "2108");
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy.plusDays(1), "PEN", null, vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "FECHA_INVALIDA", "La fecha de emisión no puede ser futura");
    }

    @Test void vencidaQuedaFueraDePlazoSinCanalYNoSeConsultaConGetStatusCdr() {
        Comprobante c = liquidacion(List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock);
        assertThat(c.fueraDePlazo(hoy.plusDays(1))).isFalse();
        assertThat(c.fueraDePlazo(hoy.plusDays(2))).as("no tiene resumen diario: vencida no le queda canal").isTrue();
        assertThat(c.esDeBoleta()).isFalse();
        assertThat(c.consultableEnGetStatusCdr()).as("getStatusCdr cubre 01, 07 y 08").isFalse();
    }

    @Test void losTiposDeOperacionDeLaLiquidacionDeCompra() {
        assertThat(Comprobante.liquidacionCompra(tenant, "L001", hoy, "PEN", "0502", vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock)
                .tipoOperacion()).isEqualTo("0502");
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy, "PEN", "0503", vendedor, datos, List.of(linea("Oro", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "TIPO_OPERACION_INVALIDO", "2466");
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy, "PEN", "0101", vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "TIPO_OPERACION_INVALIDO", "3206");
        rechaza(() -> Comprobante.liquidacionCompra(tenant, "L001", hoy, "PEN", "0599", vendedor, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "TIPO_OPERACION_INVALIDO", "3206");
    }

    @Test void unaFacturaNoAdmiteLosTiposDeLaLiquidacionDeCompra() {
        rechaza(() -> Comprobante.factura(tenant, "F001", hoy, "PEN", "0501", new Receptor("6", "20131312955", "SUNAT SA", null), List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "TIPO_OPERACION_INVALIDO", "3206");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "6|20131312955|SUNAT SA|Av. Lima 123|2800",
            "0|ABC123|JOHN DOE|Av. Lima 123|2800",
            "1|4567891|JUAN QUISPE|Av. Lima 123|2450",
            "1|4567891A|JUAN QUISPE|Av. Lima 123|2450",
            "7|AB 12345|JOHN DOE|Av. Lima 123|2802",
            "4|1234567890123456|JOHN DOE|Av. Lima 123|2802",
            "1|45678912|' '|Av. Lima 123|2021",
            "1|45678912|JUAN QUISPE|' '|2452",
            "1|45678912|JUAN QUISPE|Av|2593"})
    void elVendedorEsUnaPersonaSinRucConDomicilio(String tipo, String numero, String nombre, String direccion, String regla) {
        rechaza(() -> liquidacion(new Receptor(tipo, numero, nombre, direccion), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", regla);
    }

    @Test void elVendedorConCarneOPasaporteYSusLimites() {
        assertThat(liquidacion(new Receptor("4", "001234567", "MARIA SOUZA", "Jr. Puno 456"), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock)
                .receptor().tipoDoc()).isEqualTo("4");
        assertThat(liquidacion(new Receptor("7", "AB1234567", "  JOHN DOE  ", "  Jr. Puno 456  "), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock)
                .receptor()).as("sin espacios en los extremos (2593)").extracting(Receptor::razonSocial, Receptor::direccion).containsExactly("JOHN DOE", "Jr. Puno 456");
        rechaza(() -> liquidacion(new Receptor("1", "45678912", "JUAN\nQUISPE", "Av. Lima 1"), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", "2022");
        rechaza(() -> liquidacion(new Receptor("1", "45678912", "J".repeat(1501), "Av. Lima 1"), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", "2022");
        rechaza(() -> liquidacion(new Receptor("1", "45678912", "JUAN", "A".repeat(251)), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", "2593");
        assertThat(liquidacion(new Receptor("1", "45678912", "JUAN", "A".repeat(250)), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock).receptor().direccion()).hasSize(250);
        rechaza(() -> liquidacion(new Receptor("1", "45678912", "JUAN", "Av. Lima 1", "US"), datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", "4041");
        rechaza(() -> liquidacion(null, datos, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "RECEPTOR_INVALIDO", "2014");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "null|02|Fundo Santa Rosa|060101|02|2453",
            "999999|02|Fundo Santa Rosa|060101|02|4339",
            "060101|null|Fundo Santa Rosa|060101|02|2456",
            "060101|06|Fundo Santa Rosa|060101|02|2457",
            "060101|02|null|060101|02|2454",
            "060101|02|Fu|060101|02|2778",
            "060101|02|Fundo Santa Rosa|null|02|2455",
            "060101|02|Fundo Santa Rosa|99999|02|2917",
            "060101|02|Fundo Santa Rosa|060101|null|2458",
            "060101|02|Fundo Santa Rosa|060101|00|2459"})
    void elDomicilioDelVendedorYElLugarDeLaOperacion(String ubigeoVendedor, String condicionVendedor, String direccionLugar, String ubigeoLugar, String condicionLugar, String regla) {
        LiquidacionCompra d = new LiquidacionCompra(ubigeoVendedor, condicionVendedor, new LiquidacionCompra.Ubicacion(direccionLugar, ubigeoLugar, condicionLugar), null);
        rechaza(() -> liquidacion(vendedor, d, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "LIQUIDACION_INVALIDA", regla);
    }

    @Test void sinLugarDeLaOperacionNiDatosDeLaLiquidacion() {
        rechaza(() -> liquidacion(vendedor, new LiquidacionCompra("060101", "02", null, null), List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "LIQUIDACION_INVALIDA", "2454");
        rechaza(() -> liquidacion(vendedor, null, List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock), "LIQUIDACION_INVALIDA", "2453");
    }

    @Test void laDireccionDelLugarSeGuardaSinEspaciosEnLosExtremos() {
        LiquidacionCompra.Ubicacion u = new LiquidacionCompra.Ubicacion("  Muelle artesanal de Paita  ", " 200501 ", " 03 ");
        assertThat(u).isEqualTo(new LiquidacionCompra.Ubicacion("Muelle artesanal de Paita", "200501", "03"));
    }

    @Test void soloLaTasaVigenteDeRetencionDeRenta() {
        assertThat(liquidacion(vendedor, new LiquidacionCompra("060101", "02", datos.lugarOperacion(), new BigDecimal("1.50")), List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO)))
                .crear(clock).retencionRenta()).isEqualByComparingTo("0.13");
        rechaza(() -> liquidacion(vendedor, new LiquidacionCompra("060101", "02", datos.lugarOperacion(), new BigDecimal("2")), List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock),
                "LIQUIDACION_INVALIDA", "4237");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11", "15", "21", "31", "37"})
    void todaviaNoSeEmitenComprasGratuitas(String afectacion) {
        rechaza(() -> liquidacion(List.of(linea("Muestra", "1", "10", TipoAfectacionIgv.porCodigo(afectacion)))).crear(clock), "AFECTACION_INVALIDA", "La línea «Muestra»");
    }

    @ParameterizedTest
    @ValueSource(strings = {"17", "40"})
    void nadaDeIvapNiExportacion(String afectacion) {
        rechaza(() -> liquidacion(List.of(linea("Arroz", "1", "10", TipoAfectacionIgv.porCodigo(afectacion)))).crear(clock), "AFECTACION_INVALIDA", "3105");
    }

    @Test void cadaLineaLlevaElCodigoDeProductoSunat() {
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "KGM", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).crear(clock), "ITEM_INVALIDO", "3506");
    }

    @Test void loQueLaLineaNoLleva() {
        CodigoProductoSunat cod = CodigoProductoSunat.de("50201706");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "KGM", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, null, new Isc("01", BigDecimal.TEN, null, null), false, List.of(), cod, null))).crear(clock),
                "ITEM_INVALIDO", "3223");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Bolsa", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, null, null, true, List.of(), cod, null))).crear(clock),
                "ITEM_INVALIDO", "3223");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "KGM", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(BigDecimal.TEN, true), null, false, List.of(), cod, null))).crear(clock),
                "ITEM_INVALIDO", "La línea «Café»");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "KGM", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), cod, null, null, null, new OtrosTributos(BigDecimal.ONE), null))).crear(clock),
                "ITEM_INVALIDO", "La línea «Café»");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "KGM", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), cod, new Gtin("GTIN-13", "7750182000123")))).crear(clock),
                "ITEM_INVALIDO", "La línea «Café»");
    }

    @Test void unaLineaCuyaRetencionRedondeaACeroSeRechazaAntesDeNumerar() {
        // 0.30 × 1.5 % = 0.0045 → 0.00: SUNAT exige el monto de la retención de la línea «diferente de cero» (2033).
        rechaza(() -> liquidacion(List.of(linea("Hierba", "1", "0.30", TipoAfectacionIgv.EXONERADO))).crear(clock), "ITEM_INVALIDO", "2033");
        assertThat(liquidacion(List.of(linea("Hierba", "1", "0.34", TipoAfectacionIgv.EXONERADO))).crear(clock).retencionRenta()).isEqualByComparingTo("0.01");
    }

    @Test void lasReglasDeLineaDeSiempre() {
        rechaza(() -> liquidacion(List.of()).crear(clock), "SIN_ITEMS", "La liquidación de compra debe tener al menos un ítem");
        rechaza(() -> liquidacion(List.of(new Item("C1", "Café", "XYZ", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null))).crear(clock), "ITEM_INVALIDO", "2936");
        List<Item> muchas = java.util.Collections.nCopies(Comprobante.MAX_LINEAS + 1, linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO));
        rechaza(() -> liquidacion(muchas).crear(clock), "ITEM_INVALIDO", "2023");
    }

    @Test void documentosRelacionados() {
        Item l = linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO);
        Comprobante c = liquidacion(List.of(l)).referencias(new Referencias(null, List.of(new GuiaRelacionada("09", "T001-123")), List.of(new DocumentoRelacionado("99", "ACTA-7")))).crear(clock);
        assertThat(c.referencias().guias()).hasSize(1);
        assertThat(c.referencias().otros()).hasSize(1);
        rechaza(() -> liquidacion(List.of(l)).referencias(new Referencias(null, List.of(), List.of(new DocumentoRelacionado("04", "ENAPU-1")))).crear(clock), "DOCUMENTO_RELACIONADO_INVALIDO", "4009");
        rechaza(() -> liquidacion(List.of(l)).referencias(new Referencias("OC-1", List.of(), List.of())).crear(clock), "DOCUMENTO_RELACIONADO_INVALIDO", "Una liquidación de compra no lleva orden de compra");
    }

    @Test void todaviaNoRegularizaAnticipos() {
        rechaza(() -> liquidacion(List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).anticipos(List.of(new Anticipo("F001", 1, BigDecimal.ONE, null, null))).crear(clock),
                "ANTICIPO_INVALIDO", "khipu todavía no regulariza anticipos");
    }

    @Test void conLaTasaReducidaDelIgv() {
        Comprobante c = liquidacion(List.of(linea("Café", "1", "110", TipoAfectacionIgv.GRAVADO))).tasaIgv(new BigDecimal("10")).crear(clock);
        assertThat(c.totales().igv()).isEqualByComparingTo("10.00");
        assertThat(c.totales().total()).isEqualByComparingTo("98.50");
    }

    @Test void noSeDaDeBajaConUnaComunicacionDeBaja() {
        Comprobante c = liquidacion(List.of(linea("Café", "1", "10", TipoAfectacionIgv.GRAVADO))).crear(clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("hash", "k");
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "La Liquidacion de compra numero L001-1, ha sido aceptada", List.of()), "cdr");
        rechaza(() -> ComunicacionBaja.crear(c, 1, "Error en los datos", clock), "BAJA_INVALIDA", "2308");
    }

    @Test void rehidratadaDaLosMismosImportes() {
        Comprobante c = liquidacion(List.of(linea("Café", "10", "118", TipoAfectacionIgv.GRAVADO), linea("Papa", "3", "50", TipoAfectacionIgv.EXONERADO))).crear(clock);
        Comprobante r = Comprobante.persistido(c.id(), tenant, TipoDocumento.LIQUIDACION_COMPRA, "L001", 1L, hoy, EstadoDocumento.FIRMADO, c.receptor(), c.items())
                .tipoOperacion("0501").liquidacion(c.liquidacion()).rehidratar();
        assertThat(r.esLiquidacionCompra()).isTrue();
        assertThat(r.totales().total()).isEqualByComparingTo(c.totales().total()).isEqualByComparingTo("1132.75");
        assertThat(r.retencionRenta()).isEqualByComparingTo("17.25");
    }

    @Test void losDemasComprobantesNoRetienenRenta() {
        Comprobante f = Comprobante.factura(tenant, "F001", hoy, "PEN", "0101", new Receptor("6", "20131312955", "SUNAT SA", null), List.of(linea("Café", "1", "118", TipoAfectacionIgv.GRAVADO))).crear(clock);
        assertThat(f.esLiquidacionCompra()).isFalse();
        assertThat(f.retencionRenta()).isEqualByComparingTo("0.00");
        assertThat(f.totales().total()).isEqualByComparingTo("118.00");
    }
}
