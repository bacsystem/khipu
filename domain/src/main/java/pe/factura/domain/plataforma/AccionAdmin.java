package pe.factura.domain.plataforma;

/**
 * Acciones del administrador que deja la bitácora. Cada acción nueva del backoffice (suspender una cuenta,
 * impersonar, cambiar de plan…) agrega su valor aquí y escribe su registro en la misma transacción que la acción.
 */
public enum AccionAdmin {
    CREAR_TENANT,
    CREAR_ADMINISTRADOR,
    /** Alta asistida de un cliente: cuenta, empresa, serie y API key en un solo paso (#188). */
    CREAR_CUENTA,
    /** Login completo, con el segundo factor ya verificado (#177). El detalle dice si se usó un código de recuperación. */
    INICIAR_SESION,
    /** Primera configuración del segundo factor, que también inicia la sesión (#177). */
    CONFIGURAR_SEGUNDO_FACTOR,
    /** Corta el portal y la emisión por API de una cuenta y de todas sus empresas, sin borrar nada (#182). El detalle lleva el motivo, si lo hubo. */
    SUSPENDER_CUENTA,
    /** Devuelve una cuenta suspendida al estado en que estaba (#182). */
    REACTIVAR_CUENTA,
    /** El administrador mandó a un usuario el correo para restablecer su contraseña (#183). El detalle dice a qué usuario; nunca lleva el enlace ni el token. */
    ENVIAR_RESTABLECIMIENTO,
    /** El administrador reenvió a un usuario el correo para verificar su dirección (#183). Mismo detalle que el restablecimiento. */
    REENVIAR_VERIFICACION,
    /** Baja lógica de un cliente que se fue: sale de los listados y del cobro, pero se conserva todo lo que la ley obliga a conservar (#201). El detalle lleva el motivo, si lo hubo. */
    DAR_DE_BAJA_CUENTA,
    /** Revierte la baja de una cuenta (#201). */
    REPONER_CUENTA,
    /** Cambia el entorno de una empresa (BETA ↔ PRODUCCION), que decide contra qué URLs de SUNAT emite (#187). El detalle dice de cuál a cuál. */
    CAMBIAR_ENTORNO_EMPRESA,
    /** Revoca una API key concreta de una empresa (#187). El detalle lleva su prefijo, nunca la clave. */
    REVOCAR_API_KEY_EMPRESA,
    /** Prueba la conexión de una empresa con SUNAT usando sus credenciales SOL (#187). El detalle lleva el resultado, nunca las credenciales. */
    PROBAR_CONEXION_EMPRESA
}
