package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Un producto o servicio que la empresa guarda para no teclearlo en cada emisión (#320): descripción, unidad (catálogo 03), afectación del IGV (catálogo 07), precio unitario, y de forma opcional el código
 * interno y el código de producto SUNAT (catálogo 25). Se valida con las mismas reglas que la línea de un comprobante —se arma un {@link Item} de cantidad 1 y se le pide lo que SUNAT exige antes de numerar—
 * para que lo que se guarda siempre se pueda usar al emitir. El precio es el de la línea: con IGV en la gravada y sin impuesto en las demás.
 */
public record ProductoGuardado(UUID id, UUID tenantId, String codigo, String descripcion, String unidad, String tipoAfectacionIgv, BigDecimal precioUnitario, String codigoSunat,
                               Instant creadoEn, Instant actualizadoEn) {
    /** Las afectaciones que acepta una línea (catálogo 07): las mismas del enum, sin repetir la lista. */
    private static final Set<String> AFECTACIONES = Arrays.stream(TipoAfectacionIgv.values()).map(TipoAfectacionIgv::getCodigo).collect(Collectors.toUnmodifiableSet());
    /** El formato 12,10 del precio de una línea. */
    private static final int MAX_ENTEROS = 12;
    private static final int MAX_DECIMALES = 10;

    /** Los datos tal como llegan, ya normalizados y validados; sirve igual para crear que para actualizar. */
    public record Datos(String codigo, String descripcion, String unidad, String tipoAfectacionIgv, BigDecimal precioUnitario, String codigoSunat) {
        public Datos {
            codigo = limpioOpcional(codigo);
            descripcion = descripcion == null ? null : descripcion.strip();
            unidad = unidad == null ? null : unidad.strip().toUpperCase(Locale.ROOT);
            tipoAfectacionIgv = tipoAfectacionIgv == null ? null : tipoAfectacionIgv.strip();
            precioUnitario = escala(precioUnitario);
            codigoSunat = limpioOpcional(codigoSunat);
        }

        /** Lanza {@code ITEM_INVALIDO} con el mensaje de la regla de la línea que no se cumple. */
        public Datos validar() {
            if (tipoAfectacionIgv == null || !AFECTACIONES.contains(tipoAfectacionIgv))
                throw new DomainException("ITEM_INVALIDO", "La afectación del IGV debe ser 10–17, 20, 21, 30–37 o 40 (catálogo 07)");
            if (precioUnitario == null) throw new DomainException("ITEM_INVALIDO", "2369 - El precio unitario debe ser mayor que cero");
            new Item(codigo, descripcion, unidad, BigDecimal.ONE, precioUnitario, TipoAfectacionIgv.porCodigo(tipoAfectacionIgv), null, null, false, null, CodigoProductoSunat.de(codigoSunat), null).exigirValidoParaFactura();
            return this;
        }
    }

    public static ProductoGuardado nuevo(UUID tenantId, Datos datos, Instant ahora) {
        Datos d = datos.validar();
        return new ProductoGuardado(UUID.randomUUID(), tenantId, d.codigo(), d.descripcion(), d.unidad(), d.tipoAfectacionIgv(), d.precioUnitario(), d.codigoSunat(), ahora, ahora);
    }

    /** El mismo producto con otros datos; conserva el id y la fecha de alta. */
    public ProductoGuardado con(Datos datos, Instant ahora) {
        Datos d = datos.validar();
        return new ProductoGuardado(id, tenantId, d.codigo(), d.descripcion(), d.unidad(), d.tipoAfectacionIgv(), d.precioUnitario(), d.codigoSunat(), creadoEn, ahora);
    }

    /** Al menos dos decimales y los que traiga de más (sin ceros de relleno): {@code 100} y {@code 100.0000000000} son el mismo precio y se guardan y se muestran igual. */
    public static BigDecimal escala(BigDecimal precio) {
        if (precio == null) return null;
        BigDecimal sinCeros = precio.stripTrailingZeros();
        // Un exponente enorme (1e999999999 es un JSON válido) haría que setScale construyera un BigInteger de miles de millones de dígitos: lo que no cabe en 12,10 se deja como llegó y lo rechaza validar().
        if (sinCeros.scale() > MAX_DECIMALES || sinCeros.precision() - sinCeros.scale() > MAX_ENTEROS) return sinCeros;
        return sinCeros.setScale(Math.max(2, sinCeros.scale()));
    }

    private static String limpioOpcional(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
