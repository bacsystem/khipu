package pe.factura.adapters.rest;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Llave;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.PaginaPorLlave;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.documento.Totales;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Exportación de comprobantes para el contador (#321): una fila por comprobante que cumple los filtros del listado, en CSV o en XLSX, escrita a medida que se lee (una página de
 * {@value #PAGINA} comprobantes a la vez en memoria, nunca el resultado entero). El tope ({@link #MAXIMO}) se comprueba antes de empezar con el mismo conteo: un 422 con cuántos hay,
 * no un archivo truncado.
 *
 * <p><b>Qué entra.</b> Sin filtro de {@code estado} quedan fuera los que no son ventas ({@link #NO_SON_VENTAS}: los que SUNAT rechazó, los que no pasaron la validación y los que un
 * administrador descartó); pedir uno de esos estados con {@code estado} sí los trae. Sin filtro de {@code tipo} quedan fuera también los tipos que no son ventas
 * ({@link TipoDocumento#esVenta()}: liquidación de compra, retención, percepción), cuyas columnas no cuadran con la venta; pedir el tipo con {@code tipo} sí los trae. Una nota de crédito sale con sus importes en negativo —el archivo es para el contador y así la
 * columna suma la venta neta—; la de débito, en positivo como la factura.
 *
 * <p><b>Cómo cuadra el total.</b> {@code total} = gravado + exonerado + inafecto + exportacion + igv + ivap + isc + icbper + otros_tributos + total_cargos − total_descuentos +
 * redondeo. {@code gratuito} no se cobra y no entra. Las bases y el IGV ya vienen netos de los anticipos: {@code total_anticipos} (lo pagado antes, IGV incluido) es informativo.
 *
 * <p><b>Lectura por páginas.</b> Por llave ({@code created_at}, {@code id}), no por {@code OFFSET}: lo que entra o sale del resultado mientras se exporta no corre las páginas, así
 * que un comprobante que no cambia sale exactamente una vez. Uno que cambia de estado durante la exportación sale con el estado de ese momento, o queda fuera si deja de cumplir el
 * filtro; uno emitido después de empezar no entra (va arriba, antes de la primera llave).
 *
 * <p><b>Formato.</b> CSV (RFC 4180, registros con CRLF) con la marca UTF-8 al inicio, para que Excel lea las tildes; separador {@code ,} por defecto —el de Excel con la configuración
 * regional de Perú— o {@code ;}. Los importes con punto decimal y 2 decimales. Los textos que escribió un cliente se escapan contra la inyección de fórmulas ({@link CeldaCsv}). En
 * el XLSX los textos van como celdas de texto, que una hoja de cálculo nunca evalúa, y los importes como números con 2 decimales.
 */
final class ExportacionDeComprobantes {
    /** Cuántos comprobantes admite una exportación: un mes de una empresa grande. Más que eso se pide por tramos de fechas. */
    static final int MAXIMO = 5000;
    static final int PAGINA = 100;
    static final String BOM = "\uFEFF";
    /** Lo que no es una venta y no se exporta salvo que el filtro {@code estado} lo pida. */
    static final Set<EstadoDocumento> NO_SON_VENTAS = EnumSet.of(EstadoDocumento.RECHAZADO, EstadoDocumento.INVALIDO, EstadoDocumento.DESCARTADO);

    static final List<String> COLUMNAS = List.of("tipo", "serie", "numero", "fecha_emision", "cliente_tipo_doc", "cliente_num_doc", "cliente", "moneda", "tipo_cambio",
            "gravado", "exonerado", "inafecto", "gratuito", "exportacion", "igv", "icbper", "isc", "ivap", "otros_tributos", "total_descuentos", "total_cargos", "total_anticipos",
            "redondeo", "total", "estado", "documento_modificado");

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

    private ExportacionDeComprobantes() {}

    /** Los estados que no entran: los que no son ventas, salvo que el filtro pida un estado concreto (entonces manda el filtro). */
    static Set<EstadoDocumento> excluidos(Filtro filtro) { return filtro.estado() != null ? Set.of() : NO_SON_VENTAS; }

    /** Sin filtro de tipo solo las ventas; con él, manda el filtro (como con el estado). */
    static boolean entra(Filtro filtro, Comprobante c) { return filtro.tipo() != null || c.tipo().esVenta(); }

    /** Los valores de la fila de un comprobante, en el orden de {@link #COLUMNAS}; {@code null} es una celda vacía. */
    static List<Object> valores(Comprobante c) {
        Totales t = c.totales();
        boolean credito = c.tipo() == TipoDocumento.NOTA_CREDITO;
        // El documento modificado de una nota con su tipo: «01 F001-125» (factura) o «03 B001-9» (boleta). Vacío en una factura o una boleta.
        String modificado = c.nota() == null ? null : c.nota().tipoAfectado().codigo() + " " + c.nota().documentoAfectado();
        List<Object> v = new ArrayList<>();
        v.add(c.tipo().codigo());
        v.add(c.serie());
        v.add(c.numero());
        v.add(c.fechaEmision());
        v.add(c.receptor() == null ? null : c.receptor().tipoDoc());
        v.add(c.receptor() == null ? null : c.receptor().numDoc());
        v.add(c.receptor() == null ? null : c.receptor().razonSocial());
        v.add(c.moneda());
        v.add(c.tipoCambio() == null ? null : c.tipoCambio().setScale(3, RoundingMode.HALF_UP));
        for (BigDecimal monto : List.of(t.gravado(), t.exonerado(), t.inafecto(), t.gratuito(), t.exportacion(), t.igv(), t.icbper(), t.isc(), t.ivap(), t.otrosTributos(),
                t.totalDescuentos(), t.totalCargos(), t.totalAnticipos(), t.redondeo(), t.total()))
            v.add(importe(monto, credito));
        v.add(c.estado().name());
        v.add(modificado);
        return v;
    }

    /** Valida el tope; devuelve cuántos comprobantes entran (puede ser cero: un mes sin ventas también es una respuesta, y el archivo sale solo con la cabecera). */
    static long verificar(ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro) {
        long total = consultar.contar(tenant, filtro, excluidos(filtro));
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
        try {
            if (formato == Formato.CSV) escribirCsv(salida, consultar, tenant, filtro, separador);
            else escribirXlsx(salida, consultar, tenant, filtro);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Recorre los comprobantes de a una página por llave y entrega cada uno una sola vez, hasta {@link #MAXIMO}. */
    private static void recorrer(ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, Consumer<Comprobante> cada) {
        Set<UUID> vistos = new HashSet<>();
        Set<EstadoDocumento> excluidos = excluidos(filtro);
        Llave despues = null;
        while (vistos.size() < MAXIMO) {
            PaginaPorLlave pagina = consultar.listarPorLlave(tenant, filtro, excluidos, despues, PAGINA);
            for (Comprobante c : pagina.filas()) {
                if (vistos.size() >= MAXIMO) return;
                if (!entra(filtro, c)) continue;
                if (vistos.add(c.id())) cada.accept(c);
            }
            if (pagina.filas().size() < PAGINA || pagina.ultima() == null) return;
            despues = pagina.ultima();
        }
    }

    // --- CSV ------------------------------------------------------------------------------------------------------------------------

    private static void escribirCsv(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, char separador) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8));
        w.write(BOM);
        w.write(String.join(String.valueOf(separador), COLUMNAS));
        w.write("\r\n");
        recorrer(consultar, tenant, filtro, c -> {
            try {
                w.write(lineaCsv(valores(c), separador));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        w.flush();
    }

    static String lineaCsv(List<Object> valores, char separador) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(separador);
            Object v = valores.get(i);
            if (v instanceof BigDecimal b) sb.append(b.toPlainString());
            else if (v != null) sb.append(CeldaCsv.texto(v.toString(), separador));
        }
        return sb.append("\r\n").toString();
    }

    // --- XLSX -----------------------------------------------------------------------------------------------------------------------

    private static void escribirXlsx(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro) throws IOException {
        Workbook wb = new Workbook(salida, "khipu", "1.0");
        Worksheet ws = wb.newWorksheet("Comprobantes");
        for (int c = 0; c < COLUMNAS.size(); c++) ws.value(0, c, COLUMNAS.get(c));
        ws.range(0, 0, 0, COLUMNAS.size() - 1).style().bold().set();
        ws.freezePane(0, 1);
        int[] fila = {1};
        recorrer(consultar, tenant, filtro, comprobante -> {
            List<Object> valores = valores(comprobante);
            int r = fila[0]++;
            for (int c = 0; c < valores.size(); c++) {
                Object v = valores.get(c);
                if (v == null) continue;
                if (v instanceof BigDecimal b) {
                    ws.value(r, c, b);
                    ws.style(r, c).format(b.scale() == 3 ? "0.000" : "0.00").set();
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
                    throw new UncheckedIOException(e);
                }
            }
        });
        wb.finish();
    }

    /** Un importe con la escala de SUNAT (2 decimales); en una nota de crédito, con el signo cambiado. */
    private static BigDecimal importe(BigDecimal v, boolean negativo) {
        BigDecimal m = v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
        return negativo && m.signum() != 0 ? m.negate() : m;
    }
}
