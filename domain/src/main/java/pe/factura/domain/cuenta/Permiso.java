package pe.factura.domain.cuenta;

/**
 * Lo que un usuario del portal puede hacer en su cuenta (#21). Cada rol tiene un conjunto fijo ({@link Rol#puede(Permiso)}); qué permiso pide cada ruta lo decide el
 * adaptador REST.
 */
public enum Permiso {
    /** Mirar: comprobantes, empresa, series, plan, consumo. Lo tiene todo rol. */
    LEER,
    /** Emitir, enviar, anular y compartir comprobantes, y mantener los clientes y productos guardados con los que se emite. */
    EMITIR,
    /** Cambiar la configuración: empresas, datos fiscales, certificado, credenciales SOL, series, establecimientos, API keys, webhooks y personalización del PDF. */
    CONFIGURAR,
    /** Invitar usuarios, cambiarles el rol y desactivarlos. */
    GESTIONAR_EQUIPO
}
