package pe.factura.domain.sire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.Isc;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;
import pe.factura.domain.documento.TipoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada campo de la fila del RVIE (#322) y sus bordes: el orden de los 40 campos, los importes de cada afectación, el signo de las notas, los anulados, la moneda y el
 * tipo de cambio, el comprador y el texto que no cabe en el formato.
 */
class FilaRvieTest {
    static final String RUC = "20100066603";
    static final PeriodoSire OCTUBRE = PeriodoSire.de("202610");
    static final LocalDate DIA = LocalDate.of(2026, 10, 14);
    static final Receptor CLIENTE = new Receptor("6", "20601234565", "COMERCIAL ANDINA S.A.C.", null);

    static Item item(String precio, TipoAfectacionIgv afectacion) { return new Item("P1", "Producto", "NIU", BigDecimal.ONE, new BigDecimal(precio), afectacion); }

    static Comprobante.Persistido comprobante(TipoDocumento tipo, String serie, long numero, EstadoDocumento estado, Receptor r, List<Item> items) {
        return Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), tipo, serie, numero, DIA, estado, r, items);
    }

    static Comprobante factura(EstadoDocumento estado, Item... items) { return comprobante(TipoDocumento.FACTURA, "F001", 125, estado, CLIENTE, List.of(items)).rehidratar(); }

    static FilaRvie fila() { return FilaRvie.de(RUC, "MI EMPRESA", OCTUBRE, factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO)), null); }

    static List<String> campos(Comprobante c) { return FilaRvie.de(RUC, "MI EMPRESA S.A.C.", OCTUBRE, c, null).campos(); }

    /** El campo {@code n} (numerado desde 1, como en el Anexo 3). */
    static String campo(Comprobante c, int n) { return campos(c).get(n - 1); }

    @Test void unaFacturaGravadaEnSolesSaleConSusCuarentaCamposEnOrden() {
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 125, EstadoDocumento.ACEPTADO, CLIENTE, List.of(item("118.00", TipoAfectacionIgv.GRAVADO)))
                .fechaVencimiento(LocalDate.of(2026, 11, 13)).rehidratar();

        String linea = FormatoRvie.linea(FilaRvie.de(RUC, "MI EMPRESA S.A.C.", OCTUBRE, c, null));

        assertThat(linea).isEqualTo("20100066603|MI EMPRESA S.A.C.|202610|2010006660301F0010000000125|14/10/2026|13/11/2026|01|F001|125||6|20601234565|COMERCIAL ANDINA S.A.C."
                + "|0.00|100.00|0.00|18.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|118.00|PEN|1.000|||||||1||0.00|0101|||\r\n");
        assertThat(linea.split("\\|", -1)).hasSize(FormatoRvie.CAMPOS + 1);
    }

    @Test void elCarEsRucTipoSerieYNumeroEnDiezDigitos() {
        String car = campo(factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO)), 4);
        assertThat(car).hasSize(27).isEqualTo("20100066603" + "01" + "F001" + "0000000125");
        assertThat(FilaRvie.car(RUC, "03", "B001", 99_999_999L)).endsWith("B0010099999999");
    }

    @Test void exoneradoEInafectoVanEnSusCamposYSumanAlTotal() {
        Comprobante c = factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO), item("50", TipoAfectacionIgv.EXONERADO), item("30", TipoAfectacionIgv.INAFECTO));
        assertThat(campo(c, 15)).isEqualTo("100.00");
        assertThat(campo(c, 17)).isEqualTo("18.00");
        assertThat(campo(c, 19)).isEqualTo("50.00");
        assertThat(campo(c, 20)).isEqualTo("30.00");
        assertThat(campo(c, 26)).isEqualTo("198.00");
    }

    @Test void laExportacionVaEnElCampoCatorceYNoEnLaBaseGravada() {
        Receptor exterior = new Receptor("0", "X123", "BUYER INC", null, "US");
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 7, EstadoDocumento.ACEPTADO, exterior, List.of(item("500", TipoAfectacionIgv.EXPORTACION)))
                .moneda("USD").tipoOperacion("0200").tipoCambio(new BigDecimal("3.745")).rehidratar();
        assertThat(campo(c, 14)).isEqualTo("500.00");
        assertThat(campo(c, 15)).isEqualTo("0.00");
        assertThat(campo(c, 17)).isEqualTo("0.00");
        assertThat(campo(c, 26)).isEqualTo("500.00");
        assertThat(campo(c, 38)).isEqualTo("0200");
    }

    @Test void elIvapVaEnLosCamposVeintidosYVeintitresNoEnLaBaseNiElIgv() {
        Comprobante c = factura(EstadoDocumento.ACEPTADO, item("104", TipoAfectacionIgv.IVAP));
        assertThat(c.totales().tieneIvap()).isTrue();
        assertThat(campo(c, 15)).isEqualTo("0.00");
        assertThat(campo(c, 17)).isEqualTo("0.00");
        assertThat(campo(c, 22)).isEqualTo("100.00");
        assertThat(campo(c, 23)).isEqualTo("4.00");
        assertThat(campo(c, 26)).isEqualTo("104.00");
    }

    @Test void lasGratuitasVanEnElCampoTreintaYSieteYNoSumanABaseNiTotal() {
        Comprobante c = factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO), item("59", TipoAfectacionIgv.GRAVADO_BONIFICACION));
        assertThat(campo(c, 15)).isEqualTo("100.00");
        assertThat(campo(c, 37)).isEqualTo(FormatoRvie.importe(c.totales().gratuito())).isNotEqualTo("0.00");
        assertThat(campo(c, 26)).isEqualTo("118.00");
    }

    @Test void elIscYElIcbperVanEnSusCampos() {
        Item conIsc = new Item("P1", "Licor", "NIU", BigDecimal.ONE, new BigDecimal("118"), TipoAfectacionIgv.GRAVADO, null, new Isc("02", null, new BigDecimal("10")), false);
        Item bolsa = new Item("P2", "Bolsa", "NIU", BigDecimal.ONE, new BigDecimal("0.10"), TipoAfectacionIgv.GRAVADO, null, null, true);
        Comprobante c = factura(EstadoDocumento.ACEPTADO, conIsc, bolsa);
        assertThat(campo(c, 21)).isEqualTo(FormatoRvie.importe(c.totales().isc())).isEqualTo("10.00");
        assertThat(campo(c, 24)).isEqualTo("0.50");
        assertThat(campo(c, 26)).isEqualTo(FormatoRvie.importe(c.totales().total()));
    }

    @Test void laNotaDeCreditoVaEnNegativoConElComprobanteQueModificaYSuMotivo() {
        Nota n = new Nota(TipoDocumento.FACTURA, "F001", 125, "01", "Anulación de la operación");
        Comprobante nc = comprobante(TipoDocumento.NOTA_CREDITO, "F001", 9, EstadoDocumento.ACEPTADO, CLIENTE, List.of(item("118", TipoAfectacionIgv.GRAVADO), item("50", TipoAfectacionIgv.EXONERADO)))
                .nota(n).rehidratar();

        List<String> f = FilaRvie.de(RUC, "MI EMPRESA", OCTUBRE, nc, LocalDate.of(2026, 9, 30)).campos();

        assertThat(f.get(6)).isEqualTo("07");
        assertThat(f.get(14)).isEqualTo("-100.00");
        assertThat(f.get(16)).isEqualTo("-18.00");
        assertThat(f.get(18)).isEqualTo("-50.00");
        assertThat(f.get(25)).isEqualTo("-168.00");
        assertThat(f.subList(28, 32)).containsExactly("30/09/2026", "01", "F001", "125");
        assertThat(f.get(33)).isEqualTo("01");
        assertThat(f.get(34)).isEqualTo(FilaRvie.ESTADO_VIGENTE);
        // Un importe cero de una nota de crédito no sale «-0.00».
        assertThat(f.get(19)).isEqualTo("0.00");
    }

    @Test void laNotaDeDebitoVaEnPositivoYSinFechaDelModificadoSiKhipuNoLaTiene() {
        Nota n = new Nota(TipoDocumento.FACTURA, "F001", 125, "02", "Intereses");
        Comprobante nd = comprobante(TipoDocumento.NOTA_DEBITO, "F001", 3, EstadoDocumento.ACEPTADO, CLIENTE, List.of(item("11.80", TipoAfectacionIgv.GRAVADO))).nota(n).rehidratar();
        List<String> f = campos(nd);
        assertThat(f.get(25)).isEqualTo("11.80");
        assertThat(f.get(28)).isEmpty();
        assertThat(f.subList(29, 32)).containsExactly("01", "F001", "125");
        assertThat(f.get(33)).isEqualTo("02");
    }

    @Test void unaFacturaNoLlevaNadaEnLosCamposDeNotas() {
        List<String> f = campos(factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO)));
        assertThat(f.subList(28, 34)).containsOnly("");
    }

    @Test void unAnuladoVaConEstadoDosEImportesEnCeroPeroConSuIdentificacion() {
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 125, EstadoDocumento.ANULADO, CLIENTE, List.of(item("118", TipoAfectacionIgv.GRAVADO), item("59", TipoAfectacionIgv.GRAVADO_BONIFICACION)))
                .moneda("USD").tipoCambio(new BigDecimal("3.7")).rehidratar();
        List<String> f = campos(c);
        assertThat(f.get(34)).isEqualTo(FilaRvie.ESTADO_ANULADO);
        for (int n : new int[]{14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 37}) assertThat(f.get(n - 1)).as("campo %d", n).isEqualTo("0.00");
        assertThat(f.subList(6, 13)).containsExactly("01", "F001", "125", "", "6", "20601234565", "COMERCIAL ANDINA S.A.C.");
        assertThat(f.get(26)).isEqualTo("USD");
        assertThat(f.get(27)).isEqualTo("3.700");
    }

    @Test void unaNotaDeCreditoAnuladaTambienVaEnCeroSinSigno() {
        Comprobante nc = comprobante(TipoDocumento.NOTA_CREDITO, "F001", 9, EstadoDocumento.ANULADO, CLIENTE, List.of(item("118", TipoAfectacionIgv.GRAVADO)))
                .nota(new Nota(TipoDocumento.FACTURA, "F001", 125, "01", "x")).rehidratar();
        assertThat(campo(nc, 26)).isEqualTo("0.00");
        assertThat(campo(nc, 35)).isEqualTo("2");
    }

    @Test void enSolesElTipoDeCambioEsUnoYFueraDeSolesElDelComprobanteOVacio() {
        assertThat(campo(factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO)), 28)).isEqualTo("1.000");
        Comprobante usd = comprobante(TipoDocumento.FACTURA, "F001", 1, EstadoDocumento.ACEPTADO, CLIENTE, List.of(item("118", TipoAfectacionIgv.GRAVADO))).moneda("USD").tipoCambio(new BigDecimal("3.745")).rehidratar();
        assertThat(campo(usd, 27)).isEqualTo("USD");
        assertThat(campo(usd, 28)).isEqualTo("3.745");
        assertThat(campo(usd, 26)).as("los importes van en la moneda del comprobante, no convertidos").isEqualTo("118.00");
        Comprobante sinTc = comprobante(TipoDocumento.FACTURA, "F001", 2, EstadoDocumento.ACEPTADO, CLIENTE, List.of(item("118", TipoAfectacionIgv.GRAVADO))).moneda("EUR").rehidratar();
        assertThat(campo(sinTc, 28)).isEmpty();
    }

    @Test void unaBoletaSinDocumentoLlevaElGuionYClientesVarios() {
        Comprobante b = comprobante(TipoDocumento.BOLETA, "B001", 88, EstadoDocumento.ACEPTADO, new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(item("59", TipoAfectacionIgv.GRAVADO))).tipoOperacion("0101").rehidratar();
        List<String> f = campos(b);
        assertThat(f.subList(6, 13)).containsExactly("03", "B001", "88", "", "-", "-", "CLIENTES VARIOS");
        assertThat(f.get(25)).isEqualTo("59.00");
    }

    @Test void elSeparadorYLosSaltosDeLineaDelNombreNoRompenLaFila() {
        Receptor raro = new Receptor("6", "20601234565", "  ACME | HERMANOS\r\nS.A.C.\tLIMA  ", null);
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 1, EstadoDocumento.ACEPTADO, raro, List.of(item("118", TipoAfectacionIgv.GRAVADO))).rehidratar();
        FilaRvie fila = FilaRvie.de(RUC, "MI | EMPRESA", OCTUBRE, c, null);
        String linea = FormatoRvie.linea(fila);
        assertThat(fila.nombreCliente()).isEqualTo("ACME HERMANOS S.A.C. LIMA");
        assertThat(fila.razonSocialEmisor()).isEqualTo("MI EMPRESA");
        assertThat(linea.split("\\|", -1)).hasSize(FormatoRvie.CAMPOS + 1);
        assertThat(linea.indexOf('\n')).isEqualTo(linea.length() - 1);
    }

    @Test void lasTildesYLaEnheSeConservan() {
        Receptor r = new Receptor("6", "20601234565", "PEÑA & CÍA. — ÁVILA S.R.L.", null);
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 1, EstadoDocumento.ACEPTADO, r, List.of(item("118", TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThat(campo(c, 13)).isEqualTo("PEÑA & CÍA. — ÁVILA S.R.L.");
    }

    @Test void unNombreMasLargoQueElDelComprobanteSeCortaSinPartirUnCaracter() {
        String largo = "Ñ".repeat(FilaRvie.MAX_NOMBRE - 1) + "😀😀";
        Receptor r = new Receptor("6", "20601234565", largo, null);
        Comprobante c = comprobante(TipoDocumento.FACTURA, "F001", 1, EstadoDocumento.ACEPTADO, r, List.of(item("118", TipoAfectacionIgv.GRAVADO))).rehidratar();
        String nombre = campo(c, 13);
        assertThat(nombre.codePointCount(0, nombre.length())).isEqualTo(FilaRvie.MAX_NOMBRE);
        assertThat(nombre).endsWith("Ñ😀");
    }

    @Test void lasFechasVanDiaMesAnioYLaDeVencimientoVaciaSiNoHay() {
        Comprobante c = factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO));
        assertThat(campo(c, 5)).isEqualTo("14/10/2026");
        assertThat(campo(c, 6)).isEmpty();
        assertThat(campo(c, 3)).isEqualTo("202610");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoDocumento.class, names = {"ACEPTADO", "ACEPTADO_CON_OBS", "ANULADO"}, mode = EnumSource.Mode.EXCLUDE)
    void loQueSunatNoTieneNoArmaUnaFila(EstadoDocumento estado) {
        Comprobante c = factura(estado, item("118", TipoAfectacionIgv.GRAVADO));
        assertThatThrownBy(() -> FilaRvie.de(RUC, "X", OCTUBRE, c, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(estado.name());
    }

    @Test void entranLoAceptadoYLoAnuladoYSonPendientesLoQueVaEnCamino() {
        assertThat(FilaRvie.INCLUIDOS).containsExactlyInAnyOrder(EstadoDocumento.ACEPTADO, EstadoDocumento.ACEPTADO_CON_OBS, EstadoDocumento.ANULADO);
        assertThat(FilaRvie.PENDIENTES).containsExactlyInAnyOrder(EstadoDocumento.FIRMADO, EstadoDocumento.ENVIADO, EstadoDocumento.ERROR_ENVIO, EstadoDocumento.PENDIENTE_AGRUPACION);
        assertThat(FilaRvie.INCLUIDOS).doesNotContainAnyElementsOf(FilaRvie.PENDIENTES);
    }

    @Test void aceptadoConObservacionesEsVigente() {
        assertThat(campo(factura(EstadoDocumento.ACEPTADO_CON_OBS, item("118", TipoAfectacionIgv.GRAVADO)), 35)).isEqualTo("1");
    }

    @Test void losImportesDeLaConciliacionSonLosDeLaFila() {
        Comprobante c = factura(EstadoDocumento.ACEPTADO, item("118", TipoAfectacionIgv.GRAVADO));
        ImportesRvie i = FilaRvie.de(RUC, "X", OCTUBRE, c, null).importes();
        assertThat(i.clave()).isEqualTo("01-F001-125");
        assertThat(i.total()).isEqualByComparingTo("118");
        assertThat(i.estado()).isEqualTo("1");
    }
}
