package pe.factura.adapters.rest;

import pe.factura.domain.cuenta.Permiso;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Qué permiso pide cada petición del portal (#21), por método y ruta ya normalizada ({@link RutaRequest}). Lo aplica {@link JwtFilter} con el rol del usuario en la base.
 * <p>
 * Cerrado por defecto: una escritura que no está en {@link #EMISION} es configuración (solo {@code ADMIN}), así que un endpoint nuevo que escribe nace reservado al
 * administrador hasta que alguien decida otra cosa. Leer es de todos, salvo lo que expone la gestión de la cuenta (las API keys y el equipo).
 * <p>
 * Las peticiones con {@code X-Api-Key} no pasan por aquí: la key es de la empresa (un integrador), no de un usuario, y solo un {@code ADMIN} puede crearla o revocarla
 * desde el portal. Quien tiene una key actúa como la empresa, igual que antes de los roles.
 */
final class PermisoDeRuta {
    private PermisoDeRuta() {}

    private static final Set<String> LECTURAS = Set.of("GET", "HEAD", "OPTIONS");

    /** Lo que también hace un {@code EMISOR}: emitir y lo que se hace con un comprobante ya emitido, y los clientes y productos con los que se emite. */
    private static final List<Pattern> EMISION = List.of(
            Pattern.compile("/v1/facturas"),
            Pattern.compile("/v1/facturas/calcular"),
            Pattern.compile("/v1/facturas/[^/]+/(enviar|correo|baja|cdr/recuperar)"),
            Pattern.compile("/v1/notas"),
            // #327: la liquidación de compra es un comprobante que emite la empresa (y su cálculo previo, como el de la factura).
            Pattern.compile("/v1/liquidaciones-compra(/calcular)?"),
            // #40: los comprobantes de retención y de percepción (y su cálculo); la reversión es la baja de /v1/facturas/{id}/baja.
            Pattern.compile("/v1/(retenciones|percepciones)(/calcular)?"),
            // #39: la guía de remisión se emite, se valida sin emitir (como /calcular) y se envía o se consulta su ticket ante SUNAT.
            Pattern.compile("/v1/guias-remision(/validar|/[^/]+/(enviar|ticket))?"),
            Pattern.compile("/v1/clientes(/.*)?"),
            Pattern.compile("/v1/productos(/.*)?"));

    /** Lecturas que no son de todos: las API keys de la empresa y el equipo de la cuenta. */
    private static final List<Pattern> LECTURA_RESERVADA = List.of(
            Pattern.compile("/v1/empresa/api-keys(/.*)?"));

    private static final Pattern EQUIPO = Pattern.compile("/v1/cuenta/(usuarios|invitaciones)(/.*)?");

    static Permiso requerido(String metodo, String ruta) {
        if (EQUIPO.matcher(ruta).matches()) return Permiso.GESTIONAR_EQUIPO;
        if (LECTURAS.contains(metodo)) return coincide(LECTURA_RESERVADA, ruta) ? Permiso.CONFIGURAR : Permiso.LEER;
        return coincide(EMISION, ruta) ? Permiso.EMITIR : Permiso.CONFIGURAR;
    }

    private static boolean coincide(List<Pattern> patrones, String ruta) {
        return patrones.stream().anyMatch(p -> p.matcher(ruta).matches());
    }
}
