package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #388: SUNAT no admite descuentos ni cargos (AllowanceCharge) en una nota sobre una factura: exige LineExtensionAmount = valor unitario × cantidad (3271) y
 * TaxableAmount del IGV = suma de las líneas (3277). Una nota expresa entonces cada línea ya neta, y el descuento o cargo global repartido entre las líneas
 * gravadas, de modo que las bases, el IGV y el total de la nota total sean los de la factura.
 */
class NotaNetaTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate FECHA = LocalDate.of(2026, 9, 18);
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE SAC", null);
    static final BigDecimal CENTAVO = new BigDecimal("0.01");

    static Item gravado(String descripcion, String cantidad, String precio) {
        return new Item(null, descripcion, "NIU", new BigDecimal(cantidad), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO);
    }

    static Comprobante factura(List<Item> items, Descuento global, List<Cargo> cargos) {
        return Comprobante.factura(UUID.randomUUID(), "F001", FECHA, "PEN", "0101", RECEPTOR, items).descuentoGlobal(global).cargos(cargos).crear(CLOCK);
    }

    /** La nota total: copia los ítems, el descuento global y los cargos de la factura (lo que hace el servicio). */
    static Comprobante notaTotal(TipoDocumento tipo, Comprobante f) {
        String motivo = tipo == TipoDocumento.NOTA_CREDITO ? "01" : "02";
        return Comprobante.nota(UUID.randomUUID(), tipo, tipo == TipoDocumento.NOTA_CREDITO ? "FC01" : "FD01", FECHA, new Nota(TipoDocumento.FACTURA, "F001", 1, motivo, "Prueba"), RECEPTOR, f.items())
                .descuentoGlobal(f.descuentoGlobal()).cargos(f.cargos()).copiaDelDocumento().crear(CLOCK);
    }

    static void exigirLoMismoQueLaFactura(Comprobante nota, Comprobante factura) {
        Totales n = nota.totales(), f = factura.totales();
        assertThat(n.gravado()).as("base gravada").isEqualByComparingTo(f.gravado());
        assertThat(n.igv()).as("IGV").isEqualByComparingTo(f.igv());
        assertThat(n.totalValorVenta()).as("total valor de venta").isEqualByComparingTo(f.totalValorVenta());
        assertThat(n.total()).as("importe total").isEqualByComparingTo(f.total());
    }

    /** 3277: el TaxableAmount del IGV (la base global) es la suma de los LineExtensionAmount; y el IGV global, la suma del IGV de las líneas. */
    static void exigirQueLasLineasCuadrenConElTotal(Comprobante nota) {
        Totales n = nota.totales();
        BigDecimal valores = n.items().stream().map(ItemCalculado::valorVenta).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal igvs = n.items().stream().map(ItemCalculado::igv).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(valores).as("suma de LineExtensionAmount = base gravada").isEqualByComparingTo(n.gravado());
        assertThat(igvs).as("suma del IGV de las líneas = IGV").isEqualByComparingTo(n.igv());
    }

    /** 3271: LineExtensionAmount = valor unitario × cantidad. */
    static void exigirQueCadaLineaSeaValorPorCantidad(Comprobante nota) {
        for (ItemCalculado i : nota.totales().items())
            assertThat(i.valorUnitario().multiply(i.item().cantidad()).setScale(2, RoundingMode.HALF_UP)).as("valor unitario × cantidad de «" + i.item().descripcion() + "»")
                    .isEqualByComparingTo(i.valorVenta());
    }

    @Test void elDescuentoDeLineaPasaAlValorDeLaLineaYLaNotaSumaLoMismoQueLaFactura() {
        List<Item> items = List.of(
                new Item(null, "Con descuento", "NIU", new BigDecimal("3"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("10"), true)),
                gravado("Sin descuento", "2", "59.00"));
        Comprobante f = factura(items, null, List.of());
        for (TipoDocumento tipo : List.of(TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO)) {
            Comprobante n = notaTotal(tipo, f);
            assertThat(n.totales().items().get(0).item().tieneDescuento()).as("la línea de la nota no lleva descuento").isFalse();
            assertThat(n.totales().items().get(0).valorVenta()).isEqualByComparingTo("270.00");   // 3 × 100.00 − 10 %
            exigirLoMismoQueLaFactura(n, f);
            exigirQueLasLineasCuadrenConElTotal(n);
            exigirQueCadaLineaSeaValorPorCantidad(n);
        }
    }

    @Test void elCargoDeLineaQueAfectaLaBasePasaAlValorDeLaLinea() {
        List<Item> items = List.of(new Item(null, "Con flete", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false,
                List.of(Cargo.porcentaje("47", new BigDecimal("10")))));
        Comprobante f = factura(items, null, List.of());
        Comprobante n = notaTotal(TipoDocumento.NOTA_DEBITO, f);
        assertThat(n.totales().items().get(0).item().tieneCargos()).as("la línea de la nota no lleva cargos").isFalse();
        assertThat(n.totales().items().get(0).valorVenta()).isEqualByComparingTo("110.00");   // 100.00 + 10 %
        exigirLoMismoQueLaFactura(n, f);
        exigirQueCadaLineaSeaValorPorCantidad(n);
    }

    @Test void elDescuentoGlobalSeRepartePorLineasYLaNotaNoLoLleva() {
        List<Item> items = List.of(gravado("A", "2", "1180.00"), gravado("B", "1", "590.00"), gravado("C", "5", "23.60"));
        Comprobante f = factura(items, Descuento.porcentaje(new BigDecimal("2"), true), List.of());
        Comprobante n = notaTotal(TipoDocumento.NOTA_CREDITO, f);
        assertThat(n.totales().descuentoGlobal()).as("la nota no lleva el descuento global").isNull();
        exigirLoMismoQueLaFactura(n, f);
        exigirQueLasLineasCuadrenConElTotal(n);
        exigirQueCadaLineaSeaValorPorCantidad(n);
    }

    @Test void elCargoGlobalQueAfectaLaBaseSeRepartePorLineas() {
        List<Item> items = List.of(gravado("A", "2", "1180.00"), gravado("B", "1", "590.00"));
        Comprobante f = factura(items, null, List.of(Cargo.monto("49", new BigDecimal("37.00"))));
        Comprobante n = notaTotal(TipoDocumento.NOTA_DEBITO, f);
        assertThat(n.totales().cargosGlobales()).as("la nota no lleva el cargo global").isEmpty();
        exigirLoMismoQueLaFactura(n, f);
        exigirQueLasLineasCuadrenConElTotal(n);
    }

    /** El descuento no se reparte exacto: 0.10 entre tres líneas iguales deja un centavo sobrante que no puede perderse ni duplicarse. */
    @Test void losCentavosSobrantesDelRepartoNoRompenElTotal() {
        List<Item> items = List.of(gravado("A", "1", "11.80"), gravado("B", "1", "11.80"), gravado("C", "1", "11.80"));
        Comprobante f = factura(items, Descuento.monto(new BigDecimal("0.10"), true), List.of());
        Comprobante n = notaTotal(TipoDocumento.NOTA_CREDITO, f);
        assertThat(f.totales().gravado()).isEqualByComparingTo("29.90");
        exigirLoMismoQueLaFactura(n, f);
        exigirQueLasLineasCuadrenConElTotal(n);
        for (ItemCalculado i : n.totales().items()) assertThat(i.valorVenta()).isBetween(new BigDecimal("9.96"), new BigDecimal("10.00"));
    }

    @Test void conLineasExoneradasElDescuentoGlobalSoloRepartePorLasGravadas() {
        List<Item> items = List.of(gravado("Gravada", "1", "1180.00"),
                new Item(null, "Exonerada", "NIU", BigDecimal.ONE, new BigDecimal("500.00"), TipoAfectacionIgv.EXONERADO));
        Comprobante f = factura(items, Descuento.porcentaje(new BigDecimal("5"), true), List.of());
        Comprobante n = notaTotal(TipoDocumento.NOTA_CREDITO, f);
        assertThat(n.totales().items().get(1).valorVenta()).as("la exonerada no cambia").isEqualByComparingTo("500.00");
        exigirLoMismoQueLaFactura(n, f);
        assertThat(n.totales().exonerado()).isEqualByComparingTo(f.totales().exonerado());
    }

    @Test void descuentoDeLineaYGlobalJuntos() {
        List<Item> items = List.of(
                new Item(null, "A", "NIU", new BigDecimal("4"), new BigDecimal("95.40"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("10"), true)),
                new Item(null, "B", "NIU", new BigDecimal("7"), new BigDecimal("13.33"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.monto("47", new BigDecimal("3.00")))),
                gravado("C", "1", "250.00"));
        Comprobante f = factura(items, Descuento.porcentaje(new BigDecimal("3"), true), List.of());
        Comprobante n = notaTotal(TipoDocumento.NOTA_CREDITO, f);
        exigirLoMismoQueLaFactura(n, f);
        exigirQueLasLineasCuadrenConElTotal(n);
        exigirQueCadaLineaSeaValorPorCantidad(n);
    }

    /**
     * 3280 de las hojas de nota: el importe total es bases + IGV + ISC + otros cargos + redondeo, sin restar ningún descuento. e-beta rechazó con 3280 una ND con un descuento
     * global que no afecta la base (140.00 en vez de 150.00) y la nota total de una factura con un descuento 01. Una nota con un descuento 01 o 03 no puede salir: se rechaza
     * antes de numerar, con lo que hay que hacer.
     */
    @Test void unaNotaNoAdmiteUnDescuentoQueNoAfectaLaBase() {
        Item conDescuento01 = new Item(null, "A", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("10"), false));
        Nota nota = new Nota(TipoDocumento.FACTURA, "F001", 1, "01", "Prueba");
        assertThatThrownBy(() -> Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", FECHA, nota, RECEPTOR, List.of(conDescuento01)).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("3280").hasMessageContaining("01")
                .extracting(e -> ((DomainException) e).codigo()).isEqualTo("DESCUENTO_INVALIDO");
        assertThatThrownBy(() -> Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", FECHA, new Nota(TipoDocumento.FACTURA, "F001", 1, "02", "Prueba"), RECEPTOR, List.of(gravado("A", "1", "118.00")))
                .descuentoGlobal(Descuento.monto(new BigDecimal("10.00"), false)).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("3280").hasMessageContaining("03");
    }

    /** Un cargo que no afecta la base (48, 46, 50) sí cabe: el total de la nota suma «otros cargos» y e-beta aceptó la ND con un cargo global 50 (escenario 31). */
    @Test void unCargoQueNoAfectaLaBaseQuedaComoEstaba() {
        List<Item> items = List.of(new Item(null, "A", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.monto("48", new BigDecimal("5.00")))));
        Comprobante f = factura(items, null, List.of(Cargo.monto("50", new BigDecimal("7.00"))));
        Comprobante n = notaTotal(TipoDocumento.NOTA_DEBITO, f);
        assertThat(n.totales().items().get(0).item().tieneCargos()).isTrue();
        assertThat(n.totales().cargosGlobales()).hasSize(1);
        exigirLoMismoQueLaFactura(n, f);
    }

    /** Barrido: con montos y descuentos al azar el reparto nunca descuadra la base, el IGV ni el total de la factura (semilla fija). */
    @Test void barridoDeRepartosNuncaDescuadra() {
        Random azar = new Random(388);
        for (int caso = 0; caso < 400; caso++) {
            List<Item> items = new ArrayList<>();
            int lineas = 1 + azar.nextInt(7);
            for (int k = 0; k < lineas; k++) {
                BigDecimal cantidad = BigDecimal.valueOf(1 + azar.nextInt(9));
                BigDecimal precio = BigDecimal.valueOf(100 + azar.nextInt(200000), 2);
                Descuento d = azar.nextInt(3) == 0 ? Descuento.porcentaje(BigDecimal.valueOf(1 + azar.nextInt(30)), true) : null;
                List<Cargo> cargos = azar.nextInt(5) == 0 ? List.of(Cargo.porcentaje("47", BigDecimal.valueOf(1 + azar.nextInt(15)))) : List.of();
                items.add(new Item(null, "Línea " + k, "NIU", cantidad, precio, TipoAfectacionIgv.GRAVADO, d, null, false, cargos));
            }
            Descuento global = azar.nextBoolean() ? Descuento.porcentaje(BigDecimal.valueOf(1 + azar.nextInt(20)), true) : null;
            List<Cargo> cargosGlobales = global == null && azar.nextBoolean() ? List.of(Cargo.monto("49", BigDecimal.valueOf(1 + azar.nextInt(5000), 2))) : List.of();
            Comprobante f = factura(items, global, cargosGlobales);
            Comprobante n = notaTotal(TipoDocumento.NOTA_CREDITO, f);
            String caso_ = "caso " + caso + " (" + lineas + " líneas, global " + (global != null ? global.valor() + " %" : cargosGlobales.isEmpty() ? "no" : "cargo") + ")";
            assertThat(n.totales().total()).as(caso_ + ": total").isEqualByComparingTo(f.totales().total());
            assertThat(n.totales().gravado()).as(caso_ + ": base").isEqualByComparingTo(f.totales().gravado());
            assertThat(n.totales().igv()).as(caso_ + ": IGV").isEqualByComparingTo(f.totales().igv());
            BigDecimal valores = n.totales().items().stream().map(ItemCalculado::valorVenta).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal igvs = n.totales().items().stream().map(ItemCalculado::igv).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(valores).as(caso_ + ": suma de líneas").isEqualByComparingTo(n.totales().gravado());
            assertThat(igvs).as(caso_ + ": suma del IGV de líneas").isEqualByComparingTo(n.totales().igv());
            for (ItemCalculado i : n.totales().items()) {
                assertThat(i.item().tieneDescuento() || i.item().tieneCargos()).as(caso_ + ": línea sin descuento ni cargo").isFalse();
                assertThat(i.valorUnitario().multiply(i.item().cantidad()).subtract(i.valorVenta()).abs()).as(caso_ + ": 3271").isLessThanOrEqualTo(CENTAVO);
                assertThat(i.valorVenta()).as(caso_ + ": valor de línea positivo").isPositive();
            }
        }
    }
}
