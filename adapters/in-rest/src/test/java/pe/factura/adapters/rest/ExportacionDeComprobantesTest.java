package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Llave;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.PaginaPorLlave;
import pe.factura.domain.documento.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La exportación para el contador (#321): columnas, escala y signo de los importes, que el total cuadre con las columnas, notas y bajas, estados que no son ventas, inyección de
 * fórmulas, separador, XLSX y la lectura por llave sin repetir filas.
 */
class ExportacionDeComprobantesTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final String BOM = "\uFEFF";
    final UUID tenant = UUID.randomUUID();
    final ConsultarComprobanteUseCase consultar = mock(ConsultarComprobanteUseCase.class);

    Comprobante.FacturaBuilder builder(String cliente, List<Item> items) {
        return Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", cliente, null), items);
    }

    Comprobante firmar(Comprobante c, long numero) {
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k.xml");
        return c;
    }

    Comprobante factura(long numero, String cliente, List<Item> items) { return firmar(builder(cliente, items).crear(CLOCK), numero); }

    Comprobante factura(long numero) {
        return factura(numero, "CLIENTE SAC", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)));
    }

    /** Una sola página (menos que {@link ExportacionDeComprobantes#PAGINA}): el recorrido termina con ella. */
    String csv(char separador, List<Comprobante> lista) throws Exception {
        when(consultar.listarPorLlave(eq(tenant), any(), any(), any(), isNull(), anyInt())).thenReturn(new PaginaPorLlave(lista, lista.isEmpty() ? null : new Llave(Instant.EPOCH, UUID.randomUUID())));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, Filtro.NINGUNO, ExportacionDeComprobantes.Formato.CSV, separador);
        return out.toString(StandardCharsets.UTF_8);
    }

    static Map<String, String> fila(String linea) {
        String[] celdas = linea.split(",", -1);
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < ExportacionDeComprobantes.COLUMNAS.size(); i++) m.put(ExportacionDeComprobantes.COLUMNAS.get(i), celdas[i]);
        return m;
    }

    /** total = bases cobradas + tributos + cargos − descuentos + redondeo (la regla de la documentación). */
    static BigDecimal suma(Map<String, String> f) {
        BigDecimal s = BigDecimal.ZERO;
        for (String c : List.of("gravado", "exonerado", "inafecto", "exportacion", "igv", "ivap", "isc", "icbper", "otros_tributos", "total_cargos", "redondeo")) s = s.add(new BigDecimal(f.get(c)));
        return s.subtract(new BigDecimal(f.get("total_descuentos")));
    }

    @Test void laCabeceraLlevaElBomYLasColumnasEnSnakeCase() throws Exception {
        String vacio = csv(',', List.of());
        assertThat(vacio).isEqualTo(BOM + String.join(",", ExportacionDeComprobantes.COLUMNAS) + "\r\n");
        assertThat(ExportacionDeComprobantes.COLUMNAS).containsSubsequence("icbper", "isc", "ivap", "otros_tributos", "total_descuentos", "total_cargos", "total_anticipos", "redondeo", "total");
    }

    @Test void unaFacturaSaleConSusBasesConDosDecimalesYSinDocumentoModificado() throws Exception {
        Comprobante mixta = factura(1, "CLIENTE SAC", List.of(
                new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118"), TipoAfectacionIgv.GRAVADO),
                new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("40.5"), TipoAfectacionIgv.EXONERADO)));

        String[] lineas = csv(',', List.of(mixta)).split("\r\n");

        assertThat(lineas[1]).isEqualTo("01,F001,1,2026-09-13,6,20601234565,CLIENTE SAC,PEN,,100.00,40.50,0.00,0.00,0.00,18.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,158.50,FIRMADO,");
    }

    @Test void elIcbperSaleEnSuColumna() throws Exception {
        Comprobante conBolsas = factura(9, "CLIENTE SAC", List.of(new Item("B1", "Bolsa", "NIU", new BigDecimal("2"), new BigDecimal("1.20"), TipoAfectacionIgv.GRAVADO, null, null, true)));
        assertThat(conBolsas.totales().icbper().signum()).as("la línea lleva ICBPER").isPositive();

        Map<String, String> f = fila(csv(',', List.of(conBolsas)).split("\r\n")[1]);

        assertThat(f.get("icbper")).isEqualTo(conBolsas.totales().icbper().setScale(2).toPlainString());
        assertThat(suma(f)).isEqualByComparingTo(f.get("total"));
    }

    /** I2: con ISC, cargo y descuento que no afectan la base, redondeo y tipo de cambio, las columnas suman el total. */
    @Test void elTotalCuadraConLasColumnasConIscCargoDescuentoYRedondeo() throws Exception {
        Comprobante c = firmar(builder("CLIENTE SAC", List.of(
                        new Item("P1", "Cerveza", "NIU", new BigDecimal("3"), new BigDecimal("159.30"), TipoAfectacionIgv.GRAVADO, null, new Isc("01", new BigDecimal("35"), null), false),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)))
                .descuentoGlobal(Descuento.monto(new BigDecimal("5.00"), false))
                .cargos(List.of(Cargo.global(false, null, Cargo.Tipo.MONTO, new BigDecimal("10.00"))))
                .redondeo(new BigDecimal("-0.05"))
                .crear(CLOCK), 7);

        Map<String, String> f = fila(csv(',', List.of(c)).split("\r\n")[1]);

        assertThat(new BigDecimal(f.get("isc"))).isPositive();
        assertThat(f.get("total_cargos")).isEqualTo("10.00");
        assertThat(f.get("total_descuentos")).isEqualTo("5.00");
        assertThat(f.get("redondeo")).isEqualTo("-0.05");
        assertThat(suma(f)).isEqualByComparingTo(f.get("total"));
        assertThat(f.get("total")).isEqualTo(c.totales().total().setScale(2).toPlainString());
    }

    /** Con anticipo las bases y el IGV ya vienen netos: el total cuadra sin restar total_anticipos, que es informativo. */
    @Test void conAnticipoLasBasesYaVienenNetasYElTotalCuadra() throws Exception {
        Comprobante c = firmar(builder("CLIENTE SAC", List.of(new Item("P1", "Obra", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)))
                .anticipos(List.of(new Anticipo("F001", 3, new BigDecimal("100.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1))))
                .crear(CLOCK), 8);

        Map<String, String> f = fila(csv(',', List.of(c)).split("\r\n")[1]);

        assertThat(f.get("total_anticipos")).isEqualTo("118.00");
        assertThat(f.get("gravado")).isEqualTo("900.00");
        assertThat(suma(f)).isEqualByComparingTo(f.get("total"));
    }

    /** I3: la nota de crédito sale en negativo (todos sus importes) y la de débito en positivo; una baja sale ANULADO. */
    @Test void laNotaDeCreditoSaleEnNegativoLaDeDebitoEnPositivoYLaBajaAnulada() throws Exception {
        Comprobante nc = NotaControllerTest.notaAceptada(tenant);
        Comprobante nd = Comprobante.nota(tenant, TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 1, "01", "Intereses"),
                new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("I1", "Intereses", "ZZ", BigDecimal.ONE, new BigDecimal("11.80"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        nd.asignarNumero(2, "20100066603");
        nd.firmar("H", "k.xml");
        Comprobante anulada = factura(2);
        anulada.marcarEnviado();
        anulada.aplicarCdr(new Cdr("0", "ok", List.of()), "c.zip");
        anulada.anular();

        String[] lineas = csv(',', List.of(nc, nd, anulada)).split("\r\n");

        Map<String, String> credito = fila(lineas[1]);
        assertThat(credito.get("tipo")).isEqualTo("07");
        assertThat(credito.get("documento_modificado")).isEqualTo("01 " + nc.nota().documentoAfectado());
        assertThat(credito.get("total")).isEqualTo(nc.totales().total().setScale(2).negate().toPlainString());
        assertThat(credito.get("gravado")).startsWith("-");
        assertThat(credito.get("igv")).startsWith("-");
        assertThat(credito.get("exonerado")).as("un cero no lleva signo").isEqualTo("0.00");
        assertThat(suma(credito)).isEqualByComparingTo(credito.get("total"));
        Map<String, String> debito = fila(lineas[2]);
        assertThat(debito.get("total")).isEqualTo("11.80");
        assertThat(debito.get("documento_modificado")).isEqualTo("01 F001-1");
        assertThat(fila(lineas[3]).get("estado")).isEqualTo("ANULADO");
    }

    @Test void elTipoDeCambioSaleConTresDecimalesSoloSiSeInformo() throws Exception {
        Comprobante usd = firmar(Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "USD", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).tipoCambio(new BigDecimal("3.75")).crear(CLOCK), 4);

        String[] lineas = csv(',', List.of(usd, factura(5))).split("\r\n");

        assertThat(fila(lineas[1]).get("tipo_cambio")).isEqualTo("3.750");
        assertThat(fila(lineas[1]).get("moneda")).isEqualTo("USD");
        assertThat(fila(lineas[2]).get("tipo_cambio")).isEmpty();
    }

    @Test void sinFiltroDeEstadoQuedanFueraLosQueNoSonVentasYConElFiltroMandaElFiltro() throws Exception {
        csv(',', List.of());
        verify(consultar).listarPorLlave(eq(tenant), eq(Filtro.NINGUNO), eq(Set.of(EstadoDocumento.RECHAZADO, EstadoDocumento.INVALIDO, EstadoDocumento.DESCARTADO)), eq(TipoDocumento.ventas()),
                isNull(), anyInt());

        Filtro rechazados = new Filtro(EstadoDocumento.RECHAZADO, null, null);
        assertThat(ExportacionDeComprobantes.excluidos(rechazados)).isEmpty();
        when(consultar.contar(tenant, rechazados, Set.of(), TipoDocumento.ventas())).thenReturn(3L);
        assertThat(ExportacionDeComprobantes.verificar(consultar, tenant, rechazados)).isEqualTo(3);
    }

    /**
     * Sin filtro de tipo, lo que no es una venta (TipoDocumento#esVenta: 04, 20, 40) no sale: sus columnas no cuadran con una venta. Pedir el tipo sí lo trae. El filtro va a la
     * consulta, la del tope y la de la lectura por igual: el tope cuenta las mismas filas que tendrá el archivo, no las retenciones que después se saltarían.
     */
    @Test void sinFiltroDeTipoQuedanFueraLosTiposQueNoSonVentasYConElFiltroMandaElFiltro() throws Exception {
        assertThat(ExportacionDeComprobantes.tipos(Filtro.NINGUNO)).containsExactlyInAnyOrder(TipoDocumento.FACTURA, TipoDocumento.BOLETA, TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO);
        for (TipoDocumento noVenta : List.of(TipoDocumento.LIQUIDACION_COMPRA, TipoDocumento.RETENCION, TipoDocumento.PERCEPCION)) {
            Filtro pedido = new Filtro(null, null, null, null, noVenta.codigo(), null, null);
            assertThat(ExportacionDeComprobantes.tipos(Filtro.NINGUNO)).as(noVenta.name()).doesNotContain(noVenta);
            assertThat(ExportacionDeComprobantes.tipos(pedido)).as(noVenta.name() + " pedido: manda el filtro").isEmpty();
        }

        // El tope cuenta con los mismos tipos que la lectura: 4 999 ventas entran aunque haya 2 000 retenciones en el rango, que la consulta ya no cuenta.
        when(consultar.contar(tenant, Filtro.NINGUNO, ExportacionDeComprobantes.NO_SON_VENTAS, TipoDocumento.ventas())).thenReturn((long) ExportacionDeComprobantes.MAXIMO - 1);
        when(consultar.contar(tenant, Filtro.NINGUNO, ExportacionDeComprobantes.NO_SON_VENTAS, Set.of())).thenReturn((long) ExportacionDeComprobantes.MAXIMO + 2000);
        assertThat(ExportacionDeComprobantes.verificar(consultar, tenant, Filtro.NINGUNO)).isEqualTo(ExportacionDeComprobantes.MAXIMO - 1);

        csv(',', List.of(factura(3)));
        verify(consultar).listarPorLlave(eq(tenant), eq(Filtro.NINGUNO), any(), eq(TipoDocumento.ventas()), isNull(), anyInt());
        Filtro retenciones = new Filtro(null, null, null, null, "20", null, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, retenciones, ExportacionDeComprobantes.Formato.CSV, ',');
        verify(consultar).listarPorLlave(eq(tenant), eq(retenciones), any(), eq(Set.of()), isNull(), anyInt());
    }

    @Test void losTextosQueEmpiezanComoFormulaSeNeutralizanYLosQueLlevanElSeparadorVanEntreComillas() throws Exception {
        assertThat(CeldaCsv.texto("=HYPERLINK(\"x\")", ',')).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CeldaCsv.texto("+51 999", ',')).isEqualTo("'+51 999");
        assertThat(CeldaCsv.texto("-SAC", ',')).isEqualTo("'-SAC");
        assertThat(CeldaCsv.texto("@foo", ';')).isEqualTo("'@foo");
        assertThat(CeldaCsv.texto("ANDINA, S.A.C.", ',')).isEqualTo("\"ANDINA, S.A.C.\"");
        assertThat(CeldaCsv.texto("ANDINA, S.A.C.", ';')).as("con punto y coma la coma no molesta").isEqualTo("ANDINA, S.A.C.");
        assertThat(CeldaCsv.texto("ANDINA; S.A.C.", ';')).isEqualTo("\"ANDINA; S.A.C.\"");

        String linea = csv(';', List.of(factura(3, "=CMD|' /C calc'!A0", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))))).split("\r\n")[1];
        assertThat(linea).contains(";'=CMD|' /C calc'!A0;").as("los importes van con punto decimal también con ;").contains(";100.00;");
    }

    /** I1: la segunda página se pide después de la llave de la primera, no con OFFSET; y una fila repetida no sale dos veces. */
    @Test void lasPaginasSePidenPorLlaveYNoRepitenFilas() throws Exception {
        int pagina = ExportacionDeComprobantes.PAGINA;
        List<Comprobante> todos = new ArrayList<>();
        for (int n = 1; n <= pagina + 3; n++) todos.add(factura(n));
        Llave primera = new Llave(Instant.parse("2026-09-13T10:00:00Z"), todos.get(pagina - 1).id());
        when(consultar.listarPorLlave(eq(tenant), any(), any(), any(), isNull(), eq(pagina))).thenReturn(new PaginaPorLlave(todos.subList(0, pagina), primera));
        when(consultar.listarPorLlave(eq(tenant), any(), any(), any(), eq(primera), eq(pagina))).thenReturn(new PaginaPorLlave(todos.subList(pagina - 1, pagina + 3), new Llave(Instant.EPOCH, UUID.randomUUID())));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, Filtro.NINGUNO, ExportacionDeComprobantes.Formato.CSV, ',');

        String[] lineas = out.toString(StandardCharsets.UTF_8).split("\r\n");
        List<String> numeros = java.util.Arrays.stream(lineas).skip(1).map(l -> l.split(",")[2]).toList();
        assertThat(numeros).doesNotHaveDuplicates().hasSize(pagina + 3);
        verify(consultar, never()).listar(any(), any(), anyInt(), anyInt());
    }

    @Test void elTopeSeCompruebaAntesYCeroNoEsError() {
        when(consultar.contar(tenant, Filtro.NINGUNO, ExportacionDeComprobantes.NO_SON_VENTAS, TipoDocumento.ventas())).thenReturn((long) ExportacionDeComprobantes.MAXIMO + 1);
        assertThatThrownBy(() -> ExportacionDeComprobantes.verificar(consultar, tenant, Filtro.NINGUNO)).extracting("codigo").isEqualTo("EXPORTACION_EXCEDE_EL_TOPE");
        when(consultar.contar(tenant, Filtro.NINGUNO, ExportacionDeComprobantes.NO_SON_VENTAS, TipoDocumento.ventas())).thenReturn(0L);
        assertThat(ExportacionDeComprobantes.verificar(consultar, tenant, Filtro.NINGUNO)).isZero();
    }

    @Test void elNombreDelArchivoDiceElRango() {
        LocalDate hoy = LocalDate.of(2026, 10, 20);
        assertThat(ExportacionDeComprobantes.nombreDeArchivo(Filtro.NINGUNO, ExportacionDeComprobantes.Formato.CSV, hoy)).isEqualTo("comprobantes-2026-10-20.csv");
        assertThat(ExportacionDeComprobantes.nombreDeArchivo(new Filtro(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), ExportacionDeComprobantes.Formato.XLSX, hoy))
                .isEqualTo("comprobantes-2026-09-01_2026-09-30.xlsx");
        assertThat(ExportacionDeComprobantes.nombreDeArchivo(new Filtro(null, LocalDate.of(2026, 9, 1), null), ExportacionDeComprobantes.Formato.CSV, hoy)).isEqualTo("comprobantes-2026-09-01_2026-10-20.csv");
        assertThat(ExportacionDeComprobantes.nombreDeArchivo(new Filtro(null, null, LocalDate.of(2026, 9, 30)), ExportacionDeComprobantes.Formato.CSV, hoy)).isEqualTo("comprobantes-inicio_2026-09-30.csv");
    }

    @Test void elFormatoNoDistingueMayusculas() {
        assertThat(ExportacionDeComprobantes.Formato.de("XLSX")).isEqualTo(ExportacionDeComprobantes.Formato.XLSX);
        assertThat(ExportacionDeComprobantes.Formato.de(null)).isEqualTo(ExportacionDeComprobantes.Formato.CSV);
    }

    /** En el XLSX los importes son números (no textos) con formato 0.00, y el nombre del cliente va como texto: una hoja de cálculo nunca lo evalúa. */
    @Test void elXlsxLlevaLosImportesComoNumerosYLosTextosComoTexto() throws Exception {
        when(consultar.listarPorLlave(eq(tenant), any(), any(), any(), isNull(), anyInt())).thenReturn(new PaginaPorLlave(
                List.of(factura(5, "=1+1", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))), null));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, Filtro.NINGUNO, ExportacionDeComprobantes.Formato.XLSX, ',');

        Map<String, String> libro = libro(out.toByteArray());
        String hoja = libro.get("xl/worksheets/sheet1.xml");
        assertThat(hoja).as("importes numéricos").contains("t=\"n\"><v>118.00</v>").contains("t=\"n\"><v>100.00</v>").contains("t=\"n\"><v>18.00</v>");
        assertThat(hoja).as("ninguna fórmula").doesNotContain("<f>");
        assertThat(libro.get("xl/sharedStrings.xml")).as("el nombre va tal cual, como texto").contains("=1+1").contains("F001");
        assertThat(libro.get("xl/styles.xml")).contains("0.00").contains("yyyy-mm-dd");
    }

    /**
     * Las entradas de un XLSX por su directorio central, como lo lee Excel. {@code ZipInputStream} no sirve: lee por las cabeceras locales y fastexcel (opczip) escribe los tamaños
     * recién en el directorio central.
     */
    static Map<String, String> libro(byte[] xlsx) throws java.io.IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("exportacion", ".xlsx");
        try {
            java.nio.file.Files.write(tmp, xlsx);
            Map<String, String> entradas = new HashMap<>();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile())) {
                for (ZipEntry e : java.util.Collections.list(zip.entries()))
                    entradas.put(e.getName(), new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
            }
            return entradas;
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}
