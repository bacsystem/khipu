package pe.factura.adapters.rest;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Totales;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Exportación de comprobantes para el contador (#321): una fila por comprobante que cumple los filtros del listado, en CSV o en XLSX, escrita a medida que se lee (una página de
 * {@value #PAGINA} comprobantes a la vez en memoria, nunca el resultado entero). El tope ({@link #MAXIMO}) se comprueba antes de empezar con el mismo conteo que el listado: un 422
 * con cuántos hay, no un archivo truncado.
 *
 * <p>Las páginas se leen con el orden del listado (lo más reciente primero). Si mientras se exporta se emite otro comprobante, entra arriba y corre las páginas una fila: la fila
 * que se repetiría se descarta por id, y como lo nuevo entra antes de lo ya leído, no se pierde ninguna de las que había al empezar.
 *
 * <p>CSV (RFC 4180, registros con CRLF) con la marca UTF-8 al inicio, para que Excel lea las tildes; separador {@code ,} por defecto —el de Excel con la configuración regional de
 * Perú— o {@code ;} (la de España y otras). Los importes van con punto decimal y 2 decimales. Los textos que escribió un cliente (su nombre y documento) se escapan contra la
 * inyección de fórmulas: si empiezan por {@code = + - @}, tabulador o retorno se les antepone una comilla simple. En el XLSX los textos van como celdas de texto, que una hoja de
 * cálculo nunca evalúa, y los importes como números con 2 decimales.
 */
final class ExportacionDeComprobantes {
    /** Cuántos comprobantes admite una exportación: un mes de una empresa grande. Más que eso se pide por tramos de fechas. */
    static final int MAXIMO = 5000;
    static final int PAGINA = 100;

    static final List<String> COLUMNAS = List.of("tipo", "serie", "numero", "fecha_emision", "cliente_tipo_doc", "cliente_num_doc", "cliente", "moneda",
            "gravado", "exonerado", "inafecto", "gratuito", "exportacion", "igv", "icbper", "total", "estado", "documento_modificado");

    enum Formato {
        CSV("csv", "text/csv; charset=UTF-8"), XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        final String extension;
        final String tipoDeContenido;

        Formato(String extension, String tipoDeContenido) { this.extension = extension; this.tipoDeContenido = tipoDeContenido; }

        static Formato de(String valor) {
            String v = valor == null || valor.isBlank() ? "csv" : valor.strip().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "csv" -> CSV;
                case "xlsx" -> XLSX;
                default -> throw new DomainException("PARAMETRO_INVALIDO", "formato debe ser csv (por defecto) o xlsx: " + valor.strip());
            };
        }
    }

    static char separador(String valor) {
        String v = valor == null || valor.isBlank() ? "coma" : valor.strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "coma" -> ',';
            case "punto_y_coma" -> ';';
            default -> throw new DomainException("PARAMETRO_INVALIDO", "separador debe ser coma (por defecto) o punto_y_coma: " + valor.strip());
        };
    }

    /** Una fila de la exportación, ya con los textos y los importes como se escriben. */
    record Fila(String tipo, String serie, Long numero, LocalDate fechaEmision, String clienteTipoDoc, String clienteNumDoc, String cliente, String moneda,
                BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal exportacion, BigDecimal igv, BigDecimal icbper, BigDecimal total,
                String estado, String documentoModificado) {
        static Fila de(Comprobante c) {
            Totales t = c.totales();
            // El documento modificado de una nota con su tipo: «01 F001-125» (factura) o «03 B001-9» (boleta). Vacío en una factura o una boleta.
            String modificado = c.nota() == null ? null : c.nota().tipoAfectado().codigo() + " " + c.nota().documentoAfectado();
            return new Fila(c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(),
                    c.receptor() == null ? null : c.receptor().tipoDoc(), c.receptor() == null ? null : c.receptor().numDoc(), c.receptor() == null ? null : c.receptor().razonSocial(),
                    c.moneda(), importe(t.gravado()), importe(t.exonerado()), importe(t.inafecto()), importe(t.gratuito()), importe(t.exportacion()), importe(t.igv()),
                    importe(t.icbper()), importe(t.total()), c.estado().name(), modificado);
        }

        List<Object> valores() {
            return java.util.Arrays.asList(tipo, serie, numero, fechaEmision, clienteTipoDoc, clienteNumDoc, cliente, moneda, gravado, exonerado, inafecto, gratuito, exportacion, igv,
                    icbper, total, estado, documentoModificado);
        }
    }

    private ExportacionDeComprobantes() {}

    /** Valida el tope; devuelve cuántos comprobantes entran (puede ser cero: un mes sin ventas también es una respuesta, y el archivo sale solo con la cabecera). */
    static long verificar(ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro) {
        long total = consultar.contar(tenant, filtro);
        if (total > MAXIMO)
            throw new DomainException("EXPORTACION_EXCEDE_EL_TOPE", "Hay " + total + " comprobantes con esos filtros y una exportación admite hasta " + MAXIMO + ": acote las fechas y exporte por tramos");
        return total;
    }

    /** El nombre del archivo: el rango de fechas pedido, o la fecha de hoy (Lima) si no se acotó. */
    static String nombreDeArchivo(Filtro filtro, Formato formato, LocalDate hoy) {
        String rango = filtro.desde() == null && filtro.hasta() == null ? hoy.toString()
                : (filtro.desde() == null ? "inicio" : filtro.desde().toString()) + "_" + (filtro.hasta() == null ? hoy.toString() : filtro.hasta().toString());
        return "comprobantes-" + rango + "." + formato.extension;
    }

    static void escribir(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, Formato formato, char separador) throws IOException {
        if (formato == Formato.CSV) escribirCsv(salida, consultar, tenant, filtro, separador);
        else escribirXlsx(salida, consultar, tenant, filtro);
    }

    /** Recorre los comprobantes de a una página y entrega cada uno una sola vez, hasta {@link #MAXIMO}. */
    private static void recorrer(ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, Consumer<Comprobante> cada) {
        Set<UUID> vistos = new HashSet<>();
        for (int pagina = 1; vistos.size() < MAXIMO; pagina++) {
            List<Comprobante> lote = consultar.listar(tenant, filtro, pagina, PAGINA);
            if (lote.isEmpty()) return;
            for (Comprobante c : lote) {
                if (vistos.size() >= MAXIMO) return;
                if (vistos.add(c.id())) cada.accept(c);
            }
            if (lote.size() < PAGINA) return;
        }
    }

    // --- CSV ------------------------------------------------------------------------------------------------------------------------

    private static void escribirCsv(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, char separador) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8));
        w.write('﻿');
        w.write(String.join(String.valueOf(separador), COLUMNAS));
        w.write("\r\n");
        try {
            recorrer(consultar, tenant, filtro, c -> {
                try {
                    w.write(lineaCsv(Fila.de(c), separador));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        w.flush();
    }

    static String lineaCsv(Fila f, char separador) {
        StringBuilder sb = new StringBuilder();
        List<Object> valores = f.valores();
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(separador);
            Object v = valores.get(i);
            if (v instanceof BigDecimal b) sb.append(b.toPlainString());
            else if (v != null) sb.append(textoCsv(v.toString(), separador));
        }
        return sb.append("\r\n").toString();
    }

    /** Un texto en una celda: sin que se lea como fórmula y, si lleva el separador, comillas o saltos, entre comillas. */
    static String textoCsv(String valor, char separador) {
        String t = valor;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        boolean comillas = t.indexOf(separador) >= 0 || t.indexOf('"') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0;
        return comillas ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }

    // --- XLSX -----------------------------------------------------------------------------------------------------------------------

    private static void escribirXlsx(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro) throws IOException {
        Workbook wb = new Workbook(salida, "khipu", "1.0");
        Worksheet ws = wb.newWorksheet("Comprobantes");
        for (int c = 0; c < COLUMNAS.size(); c++) ws.value(0, c, COLUMNAS.get(c));
        ws.range(0, 0, 0, COLUMNAS.size() - 1).style().bold().set();
        ws.freezePane(0, 1);
        int[] fila = {1};
        try {
            recorrer(consultar, tenant, filtro, comprobante -> {
                List<Object> valores = Fila.de(comprobante).valores();
                int r = fila[0]++;
                for (int c = 0; c < valores.size(); c++) {
                    Object v = valores.get(c);
                    if (v == null) continue;
                    if (v instanceof BigDecimal b) {
                        ws.value(r, c, b);
                        ws.style(r, c).format("0.00").set();
                    } else if (v instanceof Long n) {
                        ws.value(r, c, n);
                    } else if (v instanceof LocalDate d) {
                        ws.value(r, c, d);
                        ws.style(r, c).format("yyyy-mm-dd").set();
                    } else {
                        ws.value(r, c, v.toString());
                    }
                }
                // Cada tanto se vuelca lo escrito: así la hoja no se acumula entera en memoria.
                if (r % PAGINA == 0) {
                    try {
                        ws.flush();
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        wb.finish();
    }

    /** Un importe con la escala de SUNAT (2 decimales). */
    private static BigDecimal importe(BigDecimal v) { return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP); }
}
