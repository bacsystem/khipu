package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos sectoriales por ítem de una venta de recursos hidrobiológicos con detracción (tipo de operación 1002, campos
 * 107–112 de la hoja Factura2_0): van como {@code cac:AdditionalItemProperty} con los conceptos 3001–3006 del catálogo 55.
 * SUNAT los exige todos en cada línea (3063, 3130–3135) y observa los formatos (4280/4281). Se valida solo al emitir
 * ({@link Detraccion#validarDatosSectoriales}), no al rehidratar: un comprobante ya persistido no se revalida contra
 * una regla que pudo cambiar después (#89).
 */
public record Hidrobiologico(String matricula, String nombreEmbarcacion, String especie, String lugarDescarga, LocalDate fechaDescarga, BigDecimal cantidad) {
    public Hidrobiologico {
        matricula = matricula == null ? null : matricula.strip();
        nombreEmbarcacion = nombreEmbarcacion == null ? null : nombreEmbarcacion.strip();
        especie = especie == null ? null : especie.strip();
        lugarDescarga = lugarDescarga == null ? null : lugarDescarga.strip();
    }

    void exigirValido() {
        exigirTexto(matricula, "matricula", 15, "3063");
        exigirTexto(nombreEmbarcacion, "nombre_embarcacion", 100, "3130");
        exigirTexto(especie, "especie", 150, "3131");
        exigirTexto(lugarDescarga, "lugar_descarga", 100, "3132");
        if (fechaDescarga == null) throw new DomainException("DETRACCION_INVALIDA", "3134 - hidrobiologico.fecha_descarga es obligatoria (recursos hidrobiológicos, concepto 3005)");
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.scale() > 2 || cantidad.precision() - cantidad.scale() > 12)
            throw new DomainException("DETRACCION_INVALIDA", "3133 - hidrobiologico.cantidad es obligatoria: positiva, hasta 12 enteros y 2 decimales (concepto 3006, 4281)");
    }

    private static void exigirTexto(String v, String campo, int max, String regla) {
        if (v == null || v.isBlank()) throw new DomainException("DETRACCION_INVALIDA", regla + " - hidrobiologico." + campo + " es obligatorio (recursos hidrobiológicos, catálogo 55)");
        if (v.length() > max || v.chars().anyMatch(Character::isISOControl))
            throw new DomainException("DETRACCION_INVALIDA", "4280 - hidrobiologico." + campo + " admite hasta " + max + " caracteres sin saltos de línea");
    }
}
