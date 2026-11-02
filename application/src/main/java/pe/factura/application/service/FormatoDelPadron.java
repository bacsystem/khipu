package pe.factura.application.service;

import pe.factura.application.port.out.PadronRucRepository.Contribuyente;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Una línea del archivo del padrón reducido (#361). Son 15 campos separados por {@code |} y la línea termina con un {@code |}:
 * <pre>RUC|NOMBRE O RAZÓN SOCIAL|ESTADO|CONDICIÓN|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO|</pre>
 * Un {@code |} dentro de un campo viene escapado como {@code \|} («EBEN EZER \| INGENIERIA»); cortar por cada {@code |} sin mirar la barra corría las columnas. Lo que no
 * tiene valor es {@code -} (y {@code ----} en el tipo de vía).
 */
final class FormatoDelPadron {
    static final int CAMPOS = 15;
    private static final String SIN_DATO = "-";

    private FormatoDelPadron() { }

    /** La línea de títulos que abre el archivo: no es un dato ni un descarte. */
    static boolean esEncabezado(String linea) { return linea.startsWith("RUC|"); }

    /** El contribuyente de la línea, o vacío si no tiene 15 campos o su RUC no son 11 dígitos. */
    static Optional<Contribuyente> parsear(String linea) {
        List<String> campos = separar(linea);
        // Un «|» final deja un campo vacío de más.
        if (campos.size() == CAMPOS + 1 && campos.get(CAMPOS).isEmpty()) campos.remove(CAMPOS);
        if (campos.size() != CAMPOS) return Optional.empty();
        String ruc = campos.get(0).strip();
        if (!ruc.matches("\\d{11}")) return Optional.empty();
        String razonSocial = campos.get(1).strip();
        String ubigeo = campos.get(4).strip();
        return Optional.of(new Contribuyente(ruc, razonSocial.isEmpty() ? SIN_DATO : razonSocial, textoOGuion(campos.get(2)), textoOGuion(campos.get(3)),
                ubigeo.matches("\\d{6}") ? ubigeo : null, direccion(campos)));
    }

    /**
     * La dirección como la escribe SUNAT en la ficha RUC: vía, número, interior, departamento, manzana, lote, kilómetro y, al final, la zona
     * («PASEO DEL BOSQUE NRO. 500 URB. CHACARILLA DEL ESTANQUE»). Nula si el archivo no trae ninguna parte.
     */
    private static String direccion(List<String> c) {
        StringJoiner j = new StringJoiner(" ");
        agregar(j, null, c.get(5));
        agregar(j, null, c.get(6));
        agregar(j, "NRO.", c.get(9));
        agregar(j, "INT.", c.get(10));
        agregar(j, "DPTO.", c.get(12));
        agregar(j, "MZA.", c.get(13));
        agregar(j, "LOTE", c.get(11));
        agregar(j, "KM.", c.get(14));
        agregar(j, null, c.get(7));
        agregar(j, null, c.get(8));
        return j.length() == 0 ? null : j.toString();
    }

    private static void agregar(StringJoiner j, String etiqueta, String valor) {
        String v = valor.strip();
        if (v.isEmpty() || v.chars().allMatch(ch -> ch == '-')) return;
        j.add(etiqueta == null ? v : etiqueta + " " + v);
    }

    private static String textoOGuion(String valor) {
        String v = valor.strip();
        return v.isEmpty() ? SIN_DATO : v;
    }

    /** Corta por los {@code |} que no están escapados; {@code \|} queda como un {@code |} del campo. */
    static List<String> separar(String linea) {
        List<String> campos = new ArrayList<>(CAMPOS + 1);
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (ch == '\\' && i + 1 < linea.length() && linea.charAt(i + 1) == '|') {
                actual.append('|');
                i++;
            } else if (ch == '|') {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        campos.add(actual.toString());
        return campos;
    }
}
