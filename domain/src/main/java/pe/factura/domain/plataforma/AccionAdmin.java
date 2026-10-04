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
    PROBAR_CONEXION_EMPRESA,
    /** Un administrador abrió una sesión de soporte como un usuario de un cliente: solo lectura, 15 minutos (#184). El detalle dice a qué usuario y por cuánto tiempo; nunca lleva el token. */
    IMPERSONAR_USUARIO,
    /** Alta de un plan (#190). El detalle dice su nombre, precio y límites. */
    CREAR_PLAN,
    /** Cambio de nombre, precio o límites de un plan (#190). El detalle dice qué cambió, de qué a qué y, para los límites, desde cuándo. */
    EDITAR_PLAN,
    /** Saca un plan de la oferta sin tocar a las cuentas que ya lo tienen (#190). */
    DESACTIVAR_PLAN,
    /** Devuelve un plan a la oferta (#190). */
    ACTIVAR_PLAN,
    /** Borra un plan que nadie usó nunca (#190). */
    ELIMINAR_PLAN,
    /** Cambia el plan de una cuenta (#191). El detalle dice de cuál a cuál, si sube, baja o renueva, cuándo entra y con qué vencimiento y gracia; la cuenta va en `cuenta_id`. */
    CAMBIAR_PLAN,
    /** Registra a mano un pago de una cuenta y, si se pidió, extiende el vencimiento de su suscripción (#194). El detalle dice el periodo, el monto, el medio y el nuevo vencimiento; la cuenta va en `cuenta_id`. */
    REGISTRAR_PAGO,
    /** Reintenta a mano el envío de un comprobante en error (#196). El detalle dice el comprobante y cómo terminó el intento (su nuevo estado o por qué no se pudo); la empresa va en `tenant_id`. */
    REINTENTAR_ENVIO_COMPROBANTE,
    /** Descarta un comprobante en error de envío: deja de intentarse y queda terminal (#196). El detalle dice el comprobante y el motivo del administrador; la empresa va en `tenant_id`. */
    DESCARTAR_COMPROBANTE
}
