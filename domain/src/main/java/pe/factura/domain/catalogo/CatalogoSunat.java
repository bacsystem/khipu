package pe.factura.domain.catalogo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogos oficiales de SUNAT (Anexo 8 de las reglas de validación, hoja "Catálogos", versión 2026-08-26),
 * cargados desde recursos TSV del módulo. Son la única fuente para validar códigos antes de firmar y para
 * documentarlos al integrador (/v1/catalogos y el developer portal): un código que no está aquí no debe
 * llegar al XML.
 */
public final class CatalogoSunat {
    /** Una fila del catálogo: código, descripción y las columnas adicionales que tenga (tributo, nivel, %…). */
    public record Entrada(String codigo, String descripcion, Map<String, String> extra) {}

    public record Catalogo(String id, String nombre, List<String> columnas, List<Entrada> entradas) {
        public Optional<Entrada> entrada(String codigo) {
            return entradas.stream().filter(e -> e.codigo().equals(codigo)).findFirst();
        }
        public boolean contiene(String codigo) { return entrada(codigo).isPresent(); }
    }

    private static final Map<String, Catalogo> CATALOGOS = cargarTodos();

    private CatalogoSunat() {}

    public static List<Catalogo> todos() { return List.copyOf(CATALOGOS.values()); }

    public static Optional<Catalogo> porId(String id) { return Optional.ofNullable(CATALOGOS.get(id)); }

    /** Descripción oficial de un código, o vacío si el catálogo o el código no existen. */
    public static Optional<String> descripcion(String catalogoId, String codigo) {
        return porId(catalogoId).flatMap(c -> c.entrada(codigo)).map(Entrada::descripcion);
    }

    private static Map<String, Catalogo> cargarTodos() {
        Map<String, Catalogo> mapa = new LinkedHashMap<>();
        List<List<String>> indice = leer("index.tsv");
        for (List<String> fila : indice.subList(1, indice.size())) {   // la primera fila es la cabecera
            String id = fila.get(0);
            mapa.put(id, cargar(id, fila.get(1)));
        }
        return Collections.unmodifiableMap(mapa);
    }

    private static Catalogo cargar(String id, String nombre) {
        List<List<String>> filas = leer(id + ".tsv");
        List<String> columnas = List.copyOf(filas.get(0));
        List<Entrada> entradas = new ArrayList<>();
        for (List<String> f : filas.subList(1, filas.size())) {
            Map<String, String> extra = new LinkedHashMap<>();
            for (int k = 2; k < columnas.size() && k < f.size(); k++) if (!f.get(k).isBlank()) extra.put(columnas.get(k), f.get(k));
            entradas.add(new Entrada(f.get(0), f.get(1), Collections.unmodifiableMap(extra)));
        }
        return new Catalogo(id, nombre, columnas, List.copyOf(entradas));
    }

    /** TSV sin comillas ni escapes: la primera fila son las columnas; las celdas vacías finales se rellenan. */
    private static List<List<String>> leer(String recurso) {
        InputStream in = CatalogoSunat.class.getResourceAsStream("/catalogos/" + recurso);
        if (in == null) throw new IllegalStateException("Falta el recurso catalogos/" + recurso);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<List<String>> filas = new ArrayList<>();
            String linea;
            while ((linea = r.readLine()) != null) {
                if (linea.isBlank()) continue;
                filas.add(List.of(linea.split("\t", -1)));
            }
            return filas;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer catalogos/" + recurso, e);
        }
    }
}
