package pe.factura.domain.sire;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que la conciliación del RVIE (#322) compara de un comprobante, venga de khipu ({@link FilaRvie#importes()}) o de la propuesta de SUNAT
 * ({@link PropuestaRvie}): su identidad (tipo, serie, número) y los campos que deciden si las dos versiones dicen lo mismo. El número es texto porque el anexo lo
 * admite alfanumérico de hasta 20 (campo 9); uno solo de dígitos va sin ceros a la izquierda, para que {@code 00000125} y {@code 125} sean el mismo comprobante.
 */
public record ImportesRvie(String tipo, String serie, String numero, LocalDate fechaEmision, String moneda, String estado,
                           BigDecimal baseGravada, BigDecimal igv, BigDecimal exonerado, BigDecimal inafecto, BigDecimal total) {

    /** {@code 01-F001-125}: la clave del comprobante en el registro. */
    public String clave() { return tipo + "-" + serie + "-" + numero; }

    /**
     * Los campos comparables, con el nombre con que se informan y el valor ya escrito como en el archivo ({@link FormatoRvie}): dos importes iguales con distinta
     * escala ({@code 10} y {@code 10.00}) son el mismo.
     */
    Map<String, String> comparables() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fecha_emision", FormatoRvie.fecha(fechaEmision));
        m.put("moneda", Objects.toString(moneda, ""));
        m.put("estado", Objects.toString(estado, ""));
        m.put("base_gravada", FormatoRvie.importe(baseGravada));
        m.put("igv", FormatoRvie.importe(igv));
        m.put("exonerado", FormatoRvie.importe(exonerado));
        m.put("inafecto", FormatoRvie.importe(inafecto));
        m.put("total", FormatoRvie.importe(total));
        return m;
    }
}
