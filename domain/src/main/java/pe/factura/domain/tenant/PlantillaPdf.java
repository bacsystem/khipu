package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

import java.util.Locale;

/** Diseños predefinidos de la representación impresa; el nombre en minúsculas es el que viaja por la API. */
public enum PlantillaPdf {
    /** Bordes definidos y recuadro con el número: el diseño original. */
    CLASICO,
    /** Aireado, sin bordes en los bloques, con el color primario en títulos y en el número. */
    MODERNO,
    /** Minimalista: líneas finas grises, sin fondos. */
    SUTIL,
    /** Cabecera y cabecera de tabla rellenas con el color primario. */
    CORPORATIVO,
    /** Monocromo: el color primario no se usa. */
    GRIS;

    public String nombre() { return name().toLowerCase(Locale.ROOT); }

    public static PlantillaPdf porNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return CLASICO;
        try { return valueOf(nombre.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new DomainException("PERSONALIZACION_INVALIDA", "Plantilla desconocida: " + nombre + " (clasico, moderno, sutil, corporativo, gris)"); }
    }
}
