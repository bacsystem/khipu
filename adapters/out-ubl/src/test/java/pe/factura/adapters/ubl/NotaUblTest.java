package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.documento.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.adapters.ubl.AtributosSunatFacturaTest.valor;

/**
 * Notas de crédito (CreditNote) y débito (DebitNote): raíz, DiscrepancyResponse (catálogo 09/10, reglas 2128–2136),
 * BillingReference (2524, 2117), sin PaymentTerms "Contado" (3246), elementos de línea y total propios de cada tipo, y XSD.
 */
class NotaUblTest {
    static final Nota NOTA = new Nota(TipoDocumento.FACTURA, "F001", 123, "01", "Anulación de la operación por error en el pedido");

    static Comprobante nota(TipoDocumento tipo, Nota nota, FormaPago formaPago) {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), tipo, "FC01", LocalDate.of(2026, 9, 13), nota, new Receptor("6", "20601234565", "CLIENTE S.A.C.", "AV. LIMA 1"), List.of(new Item("A", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).formaPago(formaPago).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(7, "20100066603");
        return c;
    }

    static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test void notaDeCredito() throws Exception {
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_CREDITO, NOTA, null), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "/cn:CreditNote/cbc:ID")).isEqualTo("FC01-7");
        assertThat(valor(d, "count(/cn:CreditNote/cbc:InvoiceTypeCode | /cn:CreditNote/cbc:DueDate)")).isEqualTo("0");
        assertThat(valor(d, "/cn:CreditNote/cbc:Note[@languageLocaleID='1000']")).startsWith("DOS MIL CUATROCIENTOS DIEZ CON 00/100");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ReferenceID")).isEqualTo("F001-123");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode")).isEqualTo("01");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listName")).isEqualTo("Tipo de nota de credito");   // 4252
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo09");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:Description")).isEqualTo(NOTA.descripcion());
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID")).isEqualTo("F001-123");
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode")).isEqualTo("01");
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        // Sin forma de pago: una nota no lleva "Contado" (3246) y la firma/emisor/receptor son los mismos bloques que en la factura.
        assertThat(valor(d, "count(/cn:CreditNote/cac:PaymentTerms)")).isEqualTo("0");
        assertThat(valor(d, "/cn:CreditNote/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, "/cn:CreditNote/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("20601234565");
        assertThat(valor(d, "/cn:CreditNote/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("360.00");
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("2410.00");
        assertThat(valor(d, "count(/cn:CreditNote/cac:CreditNoteLine)")).isEqualTo("2");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:CreditedQuantity")).isEqualTo("2");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:CreditedQuantity/@unitCode")).isEqualTo("NIU");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:LineExtensionAmount")).isEqualTo("2000.00");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("1000");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[2]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("9997");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_CREDITO);
    }

    /**
     * #293: una nota B sobre una boleta se envía sola con el tipo 03 en BillingReference (2524, 2583) y el comprador de la boleta, que puede ir sin documento
     * («-», como en la boleta y en el resumen: 2016). Cumple el XSD de CreditNote y DebitNote.
     */
    @Test void notaSobreUnaBoletaConCompradorSinDocumento() throws Exception {
        Nota sobreBoleta = new Nota(TipoDocumento.BOLETA, "B001", 45, "01", "Anulación de la operación");
        for (TipoDocumento tipo : List.of(TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO)) {
            String raiz = tipo == TipoDocumento.NOTA_CREDITO ? "/cn:CreditNote" : "/dn:DebitNote";
            Comprobante c = Comprobante.nota(UUID.randomUUID(), tipo, tipo == TipoDocumento.NOTA_CREDITO ? "BC01" : "BD01", LocalDate.of(2026, 9, 13), sobreBoleta,
                    new Receptor("-", "-", "CLIENTES VARIOS", null), List.of(new Item("A", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))
                    .crear(FreemarkerUblGeneratorTest.CLOCK);
            c.asignarNumero(2, "20100066603");
            String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
            Document d = parsear(xml);
            assertThat(valor(d, raiz + "/cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID")).isEqualTo("B001-45");
            assertThat(valor(d, raiz + "/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode")).isEqualTo("03");
            assertThat(valor(d, raiz + "/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("-");
            assertThat(valor(d, raiz + "/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("-");
            new JaxpXsdValidator().validar(firmaFalsa(xml), tipo);
        }
    }

    /** La nota total copia el redondeo de la factura (#123): el XML lo lleva en LegalMonetaryTotal (NC f406, 3303) y la mutación sin él no tenía test. */
    @Test void notaConRedondeoLoLlevaEnElTotal() throws Exception {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), NOTA, new Receptor("6", "20601234565", "CLIENTE S.A.C.", "AV. LIMA 1"),
                List.of(new Item("A", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.44"), TipoAfectacionIgv.GRAVADO))).redondeo(new BigDecimal("-0.44")).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(8, "20100066603");
        Document d = parsear(new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant()));
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableRoundingAmount")).isEqualTo("-0.44");
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("118.00");
    }

    /** #291: en la nota, igual que en la factura, la leyenda 1002 va solo si todas las líneas son gratuitas. */
    @Test void laLeyenda1002VaSoloSiTodasLasLineasSonGratuitas() throws Exception {
        Item bonificacion = new Item("B", "Bonificación", "NIU", BigDecimal.ONE, new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION);
        Comprobante mixta = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), NOTA, new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO), bonificacion)).crear(FreemarkerUblGeneratorTest.CLOCK);
        mixta.asignarNumero(9, "20100066603");
        Comprobante gratuita = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), NOTA, new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(bonificacion)).crear(FreemarkerUblGeneratorTest.CLOCK);
        gratuita.asignarNumero(10, "20100066603");

        assertThat(valor(parsear(new FreemarkerUblGenerator().generar(mixta, FreemarkerUblGeneratorTest.tenant())), "/cn:CreditNote/cbc:Note[@languageLocaleID='1002']")).isEmpty();
        assertThat(valor(parsear(new FreemarkerUblGenerator().generar(gratuita, FreemarkerUblGeneratorTest.tenant())), "/cn:CreditNote/cbc:Note[@languageLocaleID='1002']"))
                .isEqualTo("TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE");
    }

    /**
     * #305: en CreditNoteLine y DebitNoteLine el XSD UBL 2.1 pide TaxTotal **antes** que AllowanceCharge (al revés que InvoiceLine). Una nota con descuento o cargo
     * por ítem fallaba la validación XSD después de haber tomado número. Desde #388 el descuento 00 y el cargo 47 ya no se emiten en una nota (ver
     * {@link #unaNotaNoLlevaDescuentosNiCargosQueAfectanLaBase}); el cargo 48, que no afecta la base, sigue emitiéndose y conserva este orden (el descuento 01 ya no cabe en una
     * nota: 3280).
     */
    @Test void unaNotaConCargoPorItemCumpleElXsd() throws Exception {
        List<Item> items = List.of(
                new Item("A", "Con reembolso", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.monto("48", new BigDecimal("5.00")))),
                new Item("B", "Con gastos", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.porcentaje("48", new BigDecimal("10")))));
        Nota devolucion = new Nota(TipoDocumento.FACTURA, "F001", 123, "07", "Devolución por ítem");
        Comprobante nc = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), devolucion, new Receptor("6", "20601234565", "CLIENTE S.A.C.", null), items)
                .crear(FreemarkerUblGeneratorTest.CLOCK);
        nc.asignarNumero(11, "20100066603");
        Nota aumento = new Nota(TipoDocumento.FACTURA, "F001", 123, "02", "Aumento en el valor");
        Comprobante nd = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 13), aumento, new Receptor("6", "20601234565", "CLIENTE S.A.C.", null), items)
                .crear(FreemarkerUblGeneratorTest.CLOCK);
        nd.asignarNumero(12, "20100066603");

        String xmlNc = new FreemarkerUblGenerator().generar(nc, FreemarkerUblGeneratorTest.tenant());
        String xmlNd = new FreemarkerUblGenerator().generar(nd, FreemarkerUblGeneratorTest.tenant());

        assertThat(valor(parsear(xmlNc), "/cn:CreditNote/cac:CreditNoteLine[1]/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode")).isEqualTo("48");
        assertThat(valor(parsear(xmlNd), "/dn:DebitNote/cac:DebitNoteLine[2]/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode")).isEqualTo("48");
        new JaxpXsdValidator().validar(firmaFalsa(xmlNc), TipoDocumento.NOTA_CREDITO);
        new JaxpXsdValidator().validar(firmaFalsa(xmlNd), TipoDocumento.NOTA_DEBITO);
    }

    /**
     * #388: las hojas de reglas de SUNAT para notas (NotaCredito2_0, NotaDebito2_0) no tienen {@code AllowanceCharge}, ni por línea ni global, y exigen que el valor de
     * cada línea sea valor unitario × cantidad (3271) y que la base del IGV sea la suma de las líneas (3277). e-beta rechazó la nota de una factura con descuento (3271, 3277).
     * El descuento 00 y 02 y el cargo 47 y 49 pasan al valor de las líneas: ni el XML de la NC ni el de la ND llevan un solo AllowanceCharge, y los importes cuadran.
     */
    @Test void unaNotaNoLlevaDescuentosNiCargosQueAfectanLaBase() throws Exception {
        List<Item> items = List.of(
                new Item("A", "Con descuento", "NIU", new BigDecimal("3"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("10"), true)),
                new Item("B", "Con flete", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.porcentaje("47", new BigDecimal("10")))),
                new Item("C", "Sin ajuste", "NIU", new BigDecimal("2"), new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO));
        for (TipoDocumento tipo : List.of(TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO)) {
            boolean nc = tipo == TipoDocumento.NOTA_CREDITO;
            String raiz = nc ? "/cn:CreditNote" : "/dn:DebitNote", linea = nc ? "cac:CreditNoteLine" : "cac:DebitNoteLine", cantidad = nc ? "cbc:CreditedQuantity" : "cbc:DebitedQuantity";
            Comprobante c = Comprobante.nota(UUID.randomUUID(), tipo, nc ? "FC01" : "FD01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 123, nc ? "01" : "02", "Prueba"),
                            new Receptor("6", "20601234565", "CLIENTE S.A.C.", null), items)
                    .descuentoGlobal(Descuento.porcentaje(new BigDecimal("2"), true)).cargos(List.of(Cargo.monto("49", new BigDecimal("10.00")))).copiaDelDocumento().crear(FreemarkerUblGeneratorTest.CLOCK);
            c.asignarNumero(15, "20100066603");
            String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
            Document d = parsear(xml);

            assertThat(valor(d, "count(//cac:AllowanceCharge)")).as("ningún AllowanceCharge, ni por línea ni global").isEqualTo("0");
            // 3277: el TaxableAmount del IGV es la suma de los LineExtensionAmount de las líneas gravadas.
            double lineas = Double.parseDouble(valor(d, "sum(" + raiz + "/" + linea + "/cbc:LineExtensionAmount)"));
            double base = Double.parseDouble(valor(d, raiz + "/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxableAmount"));
            assertThat(base).as("3277").isEqualTo(lineas, org.assertj.core.data.Offset.offset(0.005));
            // 3271: el valor de cada línea es el valor unitario × la cantidad.
            for (int k = 1; k <= 3; k++) {
                String ruta = raiz + "/" + linea + "[" + k + "]";
                double valorLinea = Double.parseDouble(valor(d, ruta + "/cbc:LineExtensionAmount"));
                double unitario = Double.parseDouble(valor(d, ruta + "/cac:Price/cbc:PriceAmount"));
                double cant = Double.parseDouble(valor(d, ruta + "/" + cantidad));
                assertThat(unitario * cant).as("3271 línea " + k).isEqualTo(valorLinea, org.assertj.core.data.Offset.offset(0.005));
            }
            new JaxpXsdValidator().validar(firmaFalsa(xml), tipo);
        }
    }

    /**
     * #306: en DebitNote el XSD UBL 2.1 pone PrepaidPayment y AllowanceCharge **antes** que Delivery, DeliveryTerms, PaymentMeans y PaymentTerms (al revés que en
     * Invoice). Una ND sobre una exportación (DeliveryTerms con el Incoterm, o Delivery con el país de uso; la nota los hereda de la factura) con un cargo o descuento global fallaba el XSD. La NC tiene
     * el orden de Invoice y nunca lleva PrepaidPayment: la nota no copia anticipos.
     */
    @Test void unaNotaSobreUnaExportacionConCargoGlobalCumpleElXsd() throws Exception {
        Receptor exterior = new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St, Miami", "US");
        List<Item> items = List.of(new Item("CAF", "Café verde en grano", "KGM", new BigDecimal("100"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION));
        Comprobante nd = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 13, "11", "Ajuste de exportación"), exterior, items)
                .moneda("USD").tipoOperacion("0200").exportacion(new Exportacion("FOB", null))
                .cargos(List.of(Cargo.monto("50", new BigDecimal("30.00"))))
                .crear(FreemarkerUblGeneratorTest.CLOCK);
        nd.asignarNumero(13, "20100066603");
        Comprobante nc = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 13, "07", "Devolución parcial"), exterior, items)
                .moneda("USD").tipoOperacion("0200").exportacion(new Exportacion("FOB", null))
                .cargos(List.of(Cargo.monto("50", new BigDecimal("30.00"))))
                .crear(FreemarkerUblGeneratorTest.CLOCK);
        nc.asignarNumero(14, "20100066603");

        String xmlNd = new FreemarkerUblGenerator().generar(nd, FreemarkerUblGeneratorTest.tenant());
        String xmlNc = new FreemarkerUblGenerator().generar(nc, FreemarkerUblGeneratorTest.tenant());

        assertThat(valor(parsear(xmlNd), "count(/dn:DebitNote/cac:AllowanceCharge)")).isEqualTo("1");
        assertThat(valor(parsear(xmlNd), "count(/dn:DebitNote/cac:DeliveryTerms)")).isEqualTo("1");
        assertThat(valor(parsear(xmlNc), "count(/cn:CreditNote/cac:PrepaidPayment)")).isEqualTo("0");
        new JaxpXsdValidator().validar(firmaFalsa(xmlNd), TipoDocumento.NOTA_DEBITO);
        new JaxpXsdValidator().validar(firmaFalsa(xmlNc), TipoDocumento.NOTA_CREDITO);
    }

    @Test void notaDeCreditoConCuotasCorregidas_motivo13() throws Exception {
        Nota nota13 = new Nota(TipoDocumento.FACTURA, "F001", 123, "13", "Se reprograman las cuotas");
        FormaPago fp = FormaPago.credito(new BigDecimal("2410.00"), List.of(new FormaPago.Cuota(new BigDecimal("2410.00"), LocalDate.of(2026, 12, 1))));
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_CREDITO, nota13, fp), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "count(/cn:CreditNote/cac:PaymentTerms)")).isEqualTo("2");                                     // Credito + Cuota001 (3257)
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[1]/cbc:PaymentMeansID")).isEqualTo("Credito");
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[2]/cbc:PaymentMeansID")).isEqualTo("Cuota001");
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[2]/cbc:PaymentDueDate")).isEqualTo("2026-12-01");
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("0.00");                 // 3315
        assertThat(valor(d, "count(/cn:CreditNote/cac:CreditNoteLine)")).isEqualTo("1");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine/cac:Item/cbc:Description")).isEqualTo("Se reprograman las cuotas");
        assertThat(valor(d, "/cn:CreditNote/cbc:Note[@languageLocaleID='1000']")).startsWith("CERO CON 00/100");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_CREDITO);
    }

    @Test void notaDeDebito() throws Exception {
        Nota interes = new Nota(TipoDocumento.FACTURA, "F001", 123, "01", "Intereses por mora de 30 días");
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_DEBITO, interes, null), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "/dn:DebitNote/cbc:ID")).isEqualTo("FC01-7");
        assertThat(valor(d, "/dn:DebitNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listName")).isEqualTo("Tipo de nota de debito");
        assertThat(valor(d, "/dn:DebitNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo10");
        assertThat(valor(d, "/dn:DebitNote/cac:RequestedMonetaryTotal/cbc:PayableAmount")).isEqualTo("2410.00");     // ND: RequestedMonetaryTotal (UBL 2.1)
        assertThat(valor(d, "count(/dn:DebitNote/cac:LegalMonetaryTotal)")).isEqualTo("0");
        assertThat(valor(d, "/dn:DebitNote/cac:DebitNoteLine[1]/cbc:DebitedQuantity")).isEqualTo("2");
        assertThat(valor(d, "/dn:DebitNote/cac:DebitNoteLine[1]/cac:Price/cbc:PriceAmount")).isEqualTo("1000.0000000000");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_DEBITO);
    }

    private static String firmaFalsa(String xml) {
        return xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
    }
}
