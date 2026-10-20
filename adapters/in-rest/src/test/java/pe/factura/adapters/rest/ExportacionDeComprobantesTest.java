package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
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
import java.util.UUID;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La exportación para el contador (#321): columnas, escala de importes, notas y bajas, inyección de fórmulas, separador, XLSX y la lectura por páginas sin repetir filas. */
class ExportacionDeComprobantesTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    final UUID tenant = UUID.randomUUID();
    final ConsultarComprobanteUseCase consultar = mock(ConsultarComprobanteUseCase.class);

    Comprobante factura(long numero, String cliente, List<Item> items) {
        Comprobante c = Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", cliente, null), items).crear(CLOCK);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k.xml");
        return c;
    }

    Comprobante factura(long numero) {
        return factura(numero, "CLIENTE SAC", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)));
    }

    String csv(char separador, List<Comprobante> lista) throws Exception {
        when(consultar.listar(eq(tenant), any(), eq(1), anyInt())).thenReturn(lista);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, Filtro.NINGUNO, ExportacionDeComprobantes.Formato.CSV, separador);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test void unaFacturaSaleConSusBasesConDosDecimalesYSinDocumentoModificado() throws Exception {
        Comprobante mixta = factura(1, "CLIENTE SAC", List.of(
                new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118"), TipoAfectacionIgv.GRAVADO),
                new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("40.5"), TipoAfectacionIgv.EXONERADO)));

        String[] lineas = csv(',', List.of(mixta)).split("\r\n");

        assertThat(lineas[0]).isEqualTo("﻿" + String.join(",", ExportacionDeComprobantes.COLUMNAS));
        assertThat(lineas[1]).isEqualTo("01,F001,1,2026-09-13,6,20601234565,CLIENTE SAC,PEN,100.00,40.50,0.00,0.00,0.00,18.00,0.00,158.50,FIRMADO,");
    }

    @Test void elIcbperSaleEnSuColumna() throws Exception {
        Comprobante conBolsas = factura(9, "CLIENTE SAC", List.of(new Item("B1", "Bolsa", "NIU", new BigDecimal("2"), new BigDecimal("1.20"), TipoAfectacionIgv.GRAVADO, null, null, true)));
        assertThat(conBolsas.totales().icbper().signum()).as("la línea lleva ICBPER").isPositive();

        String[] celdas = csv(',', List.of(conBolsas)).split("\r\n")[1].split(",", -1);

        assertThat(celdas[14]).isEqualTo(conBolsas.totales().icbper().setScale(2).toPlainString());
        assertThat(celdas[15]).isEqualTo(conBolsas.totales().total().setScale(2).toPlainString());
    }

    @Test void unaNotaLlevaElDocumentoQueModificaYUnaBajaSaleAnulada() throws Exception {
        Comprobante nc = NotaControllerTest.notaAceptada(tenant);
        Comprobante anulada = factura(2);
        anulada.marcarEnviado();
        anulada.aplicarCdr(new Cdr("0", "ok", List.of()), "c.zip");
        anulada.anular();

        String[] lineas = csv(',', List.of(nc, anulada)).split("\r\n");

        assertThat(lineas[1]).startsWith("07,FC01,4,").endsWith(",ACEPTADO,01 " + nc.nota().documentoAfectado());
        assertThat(lineas[2]).startsWith("01,F001,2,").endsWith(",ANULADO,");
    }

    @Test void losTextosQueEmpiezanComoFormulaSeNeutralizanYLosQueLlevanElSeparadorVanEntreComillas() throws Exception {
        assertThat(ExportacionDeComprobantes.textoCsv("=HYPERLINK(\"x\")", ',')).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(ExportacionDeComprobantes.textoCsv("+51 999", ',')).isEqualTo("'+51 999");
        assertThat(ExportacionDeComprobantes.textoCsv("-SAC", ',')).isEqualTo("'-SAC");
        assertThat(ExportacionDeComprobantes.textoCsv("@foo", ';')).isEqualTo("'@foo");
        assertThat(ExportacionDeComprobantes.textoCsv("ANDINA, S.A.C.", ',')).isEqualTo("\"ANDINA, S.A.C.\"");
        assertThat(ExportacionDeComprobantes.textoCsv("ANDINA, S.A.C.", ';')).as("con punto y coma la coma no molesta").isEqualTo("ANDINA, S.A.C.");
        assertThat(ExportacionDeComprobantes.textoCsv("ANDINA; S.A.C.", ';')).isEqualTo("\"ANDINA; S.A.C.\"");

        String linea = csv(';', List.of(factura(3, "=CMD|' /C calc'!A0", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))))).split("\r\n")[1];
        assertThat(linea).contains(";'=CMD|' /C calc'!A0;").as("los importes van con punto decimal también con ;").contains(";100.00;");
    }

    /** Si mientras se exporta entra un comprobante nuevo arriba, la página 2 repite la última fila de la 1: no sale dos veces y no se pierde ninguna. */
    @Test void lasPaginasNoRepitenFilasAunqueEntreUnComprobanteNuevo() throws Exception {
        int pagina = ExportacionDeComprobantes.PAGINA;
        List<Comprobante> todos = new ArrayList<>();
        for (int n = 1; n <= pagina + 3; n++) todos.add(factura(n));
        when(consultar.listar(eq(tenant), any(), eq(1), eq(pagina))).thenReturn(todos.subList(0, pagina));
        // En la página 2 aparece repetido el último de la 1: lo corrió el comprobante nuevo.
        when(consultar.listar(eq(tenant), any(), eq(2), eq(pagina))).thenReturn(todos.subList(pagina - 1, pagina + 3));
        when(consultar.listar(eq(tenant), any(), eq(3), eq(pagina))).thenReturn(List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExportacionDeComprobantes.escribir(out, consultar, tenant, Filtro.NINGUNO, ExportacionDeComprobantes.Formato.CSV, ',');

        String[] lineas = out.toString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lineas).hasSize(1 + pagina + 3);
        List<String> numeros = java.util.Arrays.stream(lineas).skip(1).map(l -> l.split(",")[2]).toList();
        assertThat(numeros).doesNotHaveDuplicates().hasSize(pagina + 3);
    }

    @Test void elTopeSeCompruebaAntesYCeroNoEsError() {
        when(consultar.contar(tenant, Filtro.NINGUNO)).thenReturn((long) ExportacionDeComprobantes.MAXIMO + 1);
        assertThatThrownBy(() -> ExportacionDeComprobantes.verificar(consultar, tenant, Filtro.NINGUNO)).extracting("codigo").isEqualTo("EXPORTACION_EXCEDE_EL_TOPE");
        when(consultar.contar(tenant, Filtro.NINGUNO)).thenReturn(0L);
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

    /** En el XLSX los importes son números (no textos) con formato 0.00, y el nombre del cliente va como texto: una hoja de cálculo nunca lo evalúa. */
    @Test void elXlsxLlevaLosImportesComoNumerosYLosTextosComoTexto() throws Exception {
        when(consultar.listar(eq(tenant), any(), eq(1), anyInt())).thenReturn(List.of(factura(5, "=1+1", List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))));
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
