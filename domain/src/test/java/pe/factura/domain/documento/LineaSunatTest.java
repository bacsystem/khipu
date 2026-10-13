package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #354: lo que SUNAT rechaza en la línea (ICBPER 2892/3236/4320, NRUS 3240, precio 0 2367/2369/2370, más de 999 líneas 2023/2137) se ataja antes de
 * numerar, en la factura, la boleta y las notas. Todo sale como {@link DomainException} (422) con el código SUNAT al inicio del mensaje.
 */
class LineaSunatTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.of(2026, 9, 13);
    private final Receptor empresa = new Receptor("6", "20601234565", "CLIENTE SAC", "AV. LIMA 1");
    private final Receptor comprador = new Receptor("1", "12345678", "JUAN PEREZ", null);

    private static Item bolsas(String cantidad, String unidad, String precio) {
        return new Item("B1", "Bolsa plástica", unidad, new BigDecimal(cantidad), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO, null, null, true);
    }

    private static Item producto(String precio, TipoAfectacionIgv afectacion) {
        return new Item("P1", "Producto", "NIU", BigDecimal.ONE, new BigDecimal(precio), afectacion);
    }

    private Comprobante factura(List<Item> items) { return Comprobante.factura(tenant, "F001", hoy, "PEN", "0101", empresa, items).crear(clock); }
    private Comprobante boleta(String operacion, List<Item> items) { return Comprobante.boleta(tenant, "B001", hoy, "PEN", operacion, comprador, items).crear(clock); }
    private Comprobante notaCredito(List<Item> items) {
        return Comprobante.nota(tenant, TipoDocumento.NOTA_CREDITO, "FC01", hoy, new Nota(TipoDocumento.FACTURA, "F001", 1L, "07", "Devolución parcial"), empresa, items).crear(clock);
    }
    private Comprobante notaDebito(List<Item> items) {
        return Comprobante.nota(tenant, TipoDocumento.NOTA_DEBITO, "FD01", hoy, new Nota(TipoDocumento.FACTURA, "F001", 1L, "02", "Aumento en el valor"), empresa, items).crear(clock);
    }
    private Comprobante notaDeBoleta(List<Item> items) {
        return Comprobante.nota(tenant, TipoDocumento.NOTA_CREDITO, "BC01", hoy, new Nota(TipoDocumento.BOLETA, "B001", 1L, "07", "Devolución parcial"), comprador, items).crear(clock);
    }

    private static void rechaza(Runnable emision, String codigoSunat) {
        assertThatThrownBy(emision::run).isInstanceOf(DomainException.class).hasMessageStartingWith(codigoSunat + " - ")
                .extracting("codigo").isEqualTo("ITEM_INVALIDO");
    }

    // ---------------------------------------------------------------- ICBPER (H1)

    /** 2892: la cantidad de bolsas es un entero de hasta 5 dígitos; una fracción o un sexto dígito se rechazan, no se redondean en el XML. */
    @Test void laCantidadDeBolsasEsEnteraDeHasta5Digitos() {
        for (String mala : List.of("1.5", "0.5", "2.0000000001", "100000", "123456", "1000000"))
            assertThatThrownBy(() -> bolsas(mala, "NIU", "0.60").exigirValidoParaFactura()).as(mala).isInstanceOf(DomainException.class)
                    .hasMessageStartingWith("2892 - ").hasMessageContaining("bolsas").extracting("codigo").isEqualTo("ITEM_INVALIDO");
        for (String buena : List.of("1", "2", "99999", "100", "3.000", "10.0"))
            bolsas(buena, "NIU", "0.60").exigirValidoParaFactura();
    }

    /** 4320: el ICBPER va en unidades (NIU); con otra unidad la línea no corresponde al valor esperado. */
    @Test void laUnidadDeLasBolsasEsNiu() {
        for (String otra : List.of("ZZ", "KGM", "BX"))
            assertThatThrownBy(() -> bolsas("2", otra, "0.60").exigirValidoParaFactura()).as(otra).isInstanceOf(DomainException.class)
                    .hasMessageStartingWith("4320 - ").hasMessageContaining("NIU").extracting("codigo").isEqualTo("ITEM_INVALIDO");
    }

    /** Las reglas del ICBPER son de la línea con ICBPER: una cantidad fraccionaria en kilos sin ICBPER sigue valiendo. */
    @Test void sinIcbperLaCantidadPuedeSerFraccionariaYDeOtraUnidad() {
        new Item("P", "Arroz", "KGM", new BigDecimal("1.5"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO).exigirValidoParaFactura();
        new Item("P", "Arroz", "KGM", new BigDecimal("123456"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO).exigirValidoParaFactura();
    }

    @Test void facturaBoletaYNotasRechazanLasBolsasInvalidasAntesDeNumerar() {
        List<Item> fraccionaria = List.of(bolsas("1.5", "NIU", "0.60"));
        rechaza(() -> factura(fraccionaria), "2892");
        rechaza(() -> boleta("0101", fraccionaria), "2892");
        rechaza(() -> notaCredito(fraccionaria), "2892");
        rechaza(() -> notaDebito(fraccionaria), "2892");
        rechaza(() -> notaDeBoleta(fraccionaria), "2892");
        List<Item> enKilos = List.of(bolsas("2", "KGM", "0.60"));
        rechaza(() -> factura(enKilos), "4320");
        rechaza(() -> boleta("0101", enKilos), "4320");
        rechaza(() -> notaCredito(enKilos), "4320");
    }

    @Test void unaNotaTotalQueCopiaLasBolsasTambienSeValida() {
        Nota anulacion = new Nota(TipoDocumento.BOLETA, "B001", 7L, "01", "Anulación de la operación");
        rechaza(() -> Comprobante.nota(tenant, TipoDocumento.NOTA_CREDITO, "BC01", hoy, anulacion, comprador, List.of(bolsas("1.5", "NIU", "0.60"))).copiaDelDocumento().crear(clock), "2892");
    }

    /** Con bolsas válidas el ICBPER sale con la cantidad exacta del ítem, que es el BaseUnitMeasure (3236). */
    @Test void bolsasValidasSeEmitenConSuCantidadExacta() {
        Comprobante c = boleta("0101", List.of(bolsas("99999", "NIU", "0.60")));
        assertThat(c.totales().items().get(0).icbper()).isEqualByComparingTo("49999.50");
        assertThat(c.totales().items().get(0).icbperBolsas()).isEqualTo(99999);
        assertThat(boleta("0101", List.of(bolsas("3.000", "NIU", "0.60"))).totales().items().get(0).icbperBolsas()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- NRUS (H2)

    @Test void nrusConIcbperSeRechazaConLaRegla3240() {
        rechaza(() -> boleta("0113", List.of(bolsas("2", "NIU", "0.60"))), "3240");
        // mezclada con una línea normal también: basta una línea con ICBPER
        rechaza(() -> boleta("0113", List.of(producto("10.00", TipoAfectacionIgv.GRAVADO), bolsas("2", "NIU", "0.60"))), "3240");
        assertThatThrownBy(() -> boleta("0113", List.of(bolsas("2", "NIU", "0.60")))).hasMessageContaining("NRUS");
    }

    @Test void nrusSinIcbperYIcbperFueraDeNrusSiPasan() {
        assertThat(boleta("0113", List.of(producto("10.00", TipoAfectacionIgv.GRAVADO))).tipoOperacion()).isEqualTo("0113");
        assertThat(boleta("0101", List.of(bolsas("2", "NIU", "0.60"))).totales().icbper()).isEqualByComparingTo("1.00");
    }

    // ---------------------------------------------------------------- Precio 0 (H3)

    /** 2369/2367: el precio (o el valor referencial de una gratuita) en cero lo rechaza SUNAT; el portal ya lo bloqueaba, la API no. */
    @Test void unPrecioEnCeroSeRechazaEnLaOnerosa() {
        for (TipoAfectacionIgv af : List.of(TipoAfectacionIgv.GRAVADO, TipoAfectacionIgv.EXONERADO, TipoAfectacionIgv.INAFECTO))
            assertThatThrownBy(() -> producto("0", af).exigirValidoParaFactura()).as(af.name()).isInstanceOf(DomainException.class)
                    .hasMessageStartingWith("2369 - ").hasMessageContaining("mayor que cero").extracting("codigo").isEqualTo("ITEM_INVALIDO");
        assertThatThrownBy(() -> producto("0.00", TipoAfectacionIgv.GRAVADO).exigirValidoParaFactura()).hasMessageStartingWith("2369 - ");
    }

    /** Una gratuita (11–17, 21, 31–37) lleva el valor referencial en el precio: en cero SUNAT rechaza 2367 (Precio de venta/valor referencial distinto de cero). */
    @Test void unValorReferencialEnCeroSeRechazaEnLaGratuita() {
        assertThatThrownBy(() -> producto("0", TipoAfectacionIgv.GRAVADO_BONIFICACION).exigirValidoParaFactura()).isInstanceOf(DomainException.class)
                .hasMessageStartingWith("2367 - ").hasMessageContaining("valor referencial").extracting("codigo").isEqualTo("ITEM_INVALIDO");
        assertThatThrownBy(() -> producto("0.00", TipoAfectacionIgv.INAFECTO_RETIRO).exigirValidoParaFactura()).hasMessageStartingWith("2367 - ");
        // con valor referencial positivo la gratuita es válida y su Price/PriceAmount se emite en cero (2640)
        producto("100.00", TipoAfectacionIgv.GRAVADO_BONIFICACION).exigirValidoParaFactura();
        assertThat(ItemCalculado.de(producto("100.00", TipoAfectacionIgv.GRAVADO_BONIFICACION)).valorUnitario()).isEqualByComparingTo("0");
    }

    @Test void facturaBoletaYNotasRechazanElPrecioEnCeroAntesDeNumerar() {
        List<Item> gratis = List.of(producto("0", TipoAfectacionIgv.GRAVADO));
        rechaza(() -> factura(gratis), "2369");
        rechaza(() -> boleta("0101", gratis), "2369");
        rechaza(() -> notaCredito(gratis), "2369");
        rechaza(() -> notaDebito(gratis), "2369");
        rechaza(() -> notaDeBoleta(gratis), "2369");
        rechaza(() -> factura(List.of(producto("0", TipoAfectacionIgv.GRAVADO_RETIRO))), "2367");
    }

    /** La NC 13 no mueve importes: su única línea de valor 0 la arma el dominio (regla 3315) y no pasa por esta regla. */
    @Test void laNotaDeCreditoDeCuotasSigueConSuLineaSinImporte() {
        FormaPago credito = FormaPago.credito(new BigDecimal("100.00"), List.of(new FormaPago.Cuota(new BigDecimal("100.00"), hoy.plusDays(30))));
        Comprobante nc13 = Comprobante.nota(tenant, TipoDocumento.NOTA_CREDITO, "FC01", hoy, new Nota(TipoDocumento.FACTURA, "F001", 1L, "13", "Corrección de cuotas"), empresa, null)
                .formaPago(credito).crear(clock);
        assertThat(nc13.totales().total()).isEqualByComparingTo("0.00");
    }

    /** 2370: aun con precio positivo, una línea cuyo valor de venta redondea a 0.00 (o queda negativo al separar el ICBPER) la rechaza SUNAT. */
    @Test void unaLineaConValorDeVentaEnCeroSeRechaza() {
        // 0.004 / 1.18 × 1 = 0.0034 → 0.00
        rechaza(() -> factura(List.of(producto("0.004", TipoAfectacionIgv.GRAVADO))), "2370");
        rechaza(() -> boleta("0101", List.of(producto("0.004", TipoAfectacionIgv.EXONERADO))), "2370");
        rechaza(() -> notaCredito(List.of(producto("0.004", TipoAfectacionIgv.GRAVADO))), "2370");
        // el precio incluye el ICBPER: 0.50 por bolsa es solo el impuesto y el valor de venta queda en 0.00; menos aún, negativo
        rechaza(() -> boleta("0101", List.of(bolsas("2", "NIU", "0.50"))), "2370");
        rechaza(() -> boleta("0101", List.of(bolsas("2", "NIU", "0.40"))), "2370");
        assertThatThrownBy(() -> boleta("0101", List.of(bolsas("2", "NIU", "0.50")))).hasMessageContaining("ICBPER");
        // 0.01 / 1.18 = 0.0085 → 0.01 (HALF_UP) es un valor de venta positivo
        assertThat(factura(List.of(producto("0.01", TipoAfectacionIgv.GRAVADO))).totales().items().get(0).valorVenta()).isEqualByComparingTo("0.01");
        assertThat(boleta("0101", List.of(bolsas("2", "NIU", "0.60"))).totales().items().get(0).valorVenta()).isEqualByComparingTo("0.17");   // (0.60 − 0.50) / 1.18 × 2
    }

    // ---------------------------------------------------------------- Tope de 999 líneas (H4)

    private static List<Item> lineas(int n) {
        List<Item> lineas = new ArrayList<>();
        for (int i = 1; i <= n; i++) lineas.add(new Item("P" + i, "Producto " + i, "NIU", BigDecimal.ONE, new BigDecimal("1.18"), TipoAfectacionIgv.GRAVADO));
        return lineas;
    }

    @Test void elTopeDeLineasEsUnaSolaConstante() {
        assertThat(Comprobante.MAX_LINEAS).isEqualTo(999);
    }

    @Test void facturaYBoletaAceptan999LineasYRechazan1000Con2023() {
        assertThat(factura(lineas(999)).items()).hasSize(999);
        assertThat(boleta("0101", lineas(999)).items()).hasSize(999);
        for (Runnable emision : List.<Runnable>of(() -> factura(lineas(1000)), () -> boleta("0101", lineas(1000)))) {
            assertThatThrownBy(emision::run).isInstanceOf(DomainException.class).hasMessageStartingWith("2023 - ").hasMessageContaining("999").extracting("codigo").isEqualTo("ITEM_INVALIDO");
        }
    }

    /** En las notas la misma línea de 3 dígitos la valida la regla 2137 (NotaCredito2_0 fila 161, NotaDebito2_0 fila 138). */
    @Test void lasNotasAceptan999LineasYRechazan1000Con2137() {
        assertThat(notaCredito(lineas(999)).items()).hasSize(999);
        assertThat(notaDebito(lineas(999)).items()).hasSize(999);
        assertThat(notaDeBoleta(lineas(999)).items()).hasSize(999);
        for (Runnable emision : List.<Runnable>of(() -> notaCredito(lineas(1000)), () -> notaDebito(lineas(1000)), () -> notaDeBoleta(lineas(1000)))) {
            assertThatThrownBy(emision::run).isInstanceOf(DomainException.class).hasMessageStartingWith("2137 - ").hasMessageContaining("999").extracting("codigo").isEqualTo("ITEM_INVALIDO");
        }
    }
}
