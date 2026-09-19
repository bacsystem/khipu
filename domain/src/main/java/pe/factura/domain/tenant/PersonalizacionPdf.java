package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Diseño de la representación impresa de la empresa: plantilla, color primario, logo (clave en storage), pie de página y
 * observaciones por defecto. No afecta al XML ni a lo que se envía a SUNAT; solo al PDF.
 */
public record PersonalizacionPdf(PlantillaPdf plantilla, String colorPrimario, String logoKey, String pieDePagina, String observacionesPorDefecto) {

    public static final String COLOR_POR_DEFECTO = "#1E1E24";
    public static final int MAX_PIE = 300;
    public static final int MAX_OBSERVACIONES = 1000;

    public PersonalizacionPdf {
        plantilla = plantilla == null ? PlantillaPdf.CLASICO : plantilla;
        colorPrimario = colorPrimario == null || colorPrimario.isBlank() ? COLOR_POR_DEFECTO : colorPrimario.strip().toUpperCase(Locale.ROOT);
        if (!colorPrimario.matches("#[0-9A-F]{6}"))
            throw new DomainException("PERSONALIZACION_INVALIDA", "El color primario debe ser hexadecimal de 6 dígitos (p. ej. #1F5F4A): " + colorPrimario);
        logoKey = logoKey == null || logoKey.isBlank() ? null : logoKey.strip();
        pieDePagina = limpiar(pieDePagina, MAX_PIE, false, "El pie de página admite hasta " + MAX_PIE + " caracteres en una sola línea");
        observacionesPorDefecto = limpiar(observacionesPorDefecto, MAX_OBSERVACIONES, true, "Las observaciones admiten hasta " + MAX_OBSERVACIONES + " caracteres");
    }

    public static PersonalizacionPdf porDefecto() { return new PersonalizacionPdf(null, null, null, null, null); }

    public boolean tieneLogo() { return logoKey != null; }
    public PersonalizacionPdf conLogo(String key) { return new PersonalizacionPdf(plantilla, colorPrimario, key, pieDePagina, observacionesPorDefecto); }
    public PersonalizacionPdf sinLogo() { return conLogo(null); }
    /** Los campos de diseño de {@code otra} conservando el logo actual (el logo se gestiona con su propio endpoint). */
    public PersonalizacionPdf conDiseñoDe(PersonalizacionPdf otra) { return new PersonalizacionPdf(otra.plantilla, otra.colorPrimario, logoKey, otra.pieDePagina, otra.observacionesPorDefecto); }

    /**
     * Huella corta y estable del diseño, parte de la clave del PDF en storage: al cambiar cualquier campo el siguiente PDF se
     * regenera y los ya generados con el diseño anterior no se sobrescriben.
     */
    public String huella() {
        int h = Objects.hash(plantilla, colorPrimario, logoKey, pieDePagina, observacionesPorDefecto);
        return HexFormat.of().toHexDigits(h);
    }

    /** Texto validado: recortado, sin caracteres de control (salvo saltos de línea cuando {@code multilinea}); vacío = ausente. */
    static String limpiar(String v, int max, boolean multilinea, String error) {
        if (v == null || v.isBlank()) return null;
        String s = v.strip();
        boolean control = s.chars().anyMatch(ch -> Character.isISOControl(ch) && !(multilinea && (ch == '\n' || ch == '\r')));
        if (s.length() > max || control) throw new DomainException("PERSONALIZACION_INVALIDA", error);
        return s;
    }
}
