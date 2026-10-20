package pe.factura.adapters.rest;

/**
 * Una celda de texto de un CSV (RFC 4180) que escribió un cliente —un nombre, un correo, un documento—, para las exportaciones del consumo (#193) y de comprobantes (#321). Una celda
 * que empieza por {@code = + - @}, tabulador o retorno se evalúa como fórmula al abrirla en una hoja de cálculo, así que se le antepone una comilla simple y queda como texto; si
 * lleva el separador, comillas o saltos de línea, va entre comillas con las comillas duplicadas.
 */
final class CeldaCsv {
    private CeldaCsv() {}

    static String texto(String valor, char separador) {
        String t = valor == null ? "" : valor;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        boolean comillas = t.indexOf(separador) >= 0 || t.indexOf('"') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0;
        return comillas ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
    }
}
