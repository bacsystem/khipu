package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Leyendas del catálogo 52 ({@code cbc:Note@languageLocaleID}). khipu genera solas las que dependen del contenido (1000 monto en
 * letras, 1002 gratuitas, 2000 percepción, 2006 detracción, 2007 IVAP); el emisor declara las de zona o régimen (2001–2005
 * Amazonía/itinerante/paquete turístico, 2008–2009 zona comercial de Tacna, 2010–2012). Regla 3027: el código debe existir en el catálogo.
 */
public final class Leyenda {
    private Leyenda() {}

    public static final Set<String> AUTOMATICAS = Set.of("1000", "1002", "2000", "2006", "2007");
    /** Las que exigen total exonerado (9997) mayor que cero, y la regla SUNAT que citar si no lo cumplen. */
    public static final Map<String, String> EXIGEN_EXONERADO = Map.of("2001", "3283", "2002", "3284", "2003", "3285", "2008", "3289");

    /** Normaliza y valida la lista que envía el emisor: códigos del catálogo 52, sin repetidos ni automáticas. */
    public static List<String> validar(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (String c : codigos) {
            String codigo = c == null ? "" : c.strip();
            if (CatalogoSunat.porId("52").flatMap(cat -> cat.entrada(codigo)).isEmpty())
                throw new DomainException("LEYENDA_INVALIDA", "3027 - La leyenda " + codigo + " no existe en el catálogo 52");
            if (AUTOMATICAS.contains(codigo))
                throw new DomainException("LEYENDA_INVALIDA", "La leyenda " + codigo + " la genera khipu según el contenido del comprobante: no la envíe");
            if (!out.contains(codigo)) out.add(codigo);
        }
        return List.copyOf(out);
    }

    /** Texto oficial de la leyenda, tal como va en el XML: la descripción del catálogo sin el prefijo «Leyenda» ni las comillas. */
    public static String texto(String codigo) {
        String d = CatalogoSunat.descripcion("52", codigo).orElse(codigo).strip();
        d = d.replaceFirst("^Leyenda:?\\s*", "");
        d = d.replaceAll("^[“\"”]+|[“\"”]+$", "");
        return d.strip();
    }
}
