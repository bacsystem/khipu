package pe.factura.domain.documento;

import java.util.Optional;

/**
 * Las tres clases de comprobante con problema que el backoffice junta en su cola de errores (#196). Un error de formato no es un estado propio: es un rechazo por fault de
 * SUNAT con código 1000–1999 (error del contenido o del emisor). A diferencia de un rechazo por validación (2000–3999), que es un resultado normal del emisor, este es una
 * falla que suele pedir mirar el comprobante o la configuración; y a diferencia de un error de envío, no cambia por reintentar.
 */
public enum ClaseDeError {
    /** SUNAT no contestó o falló el envío: se reintenta solo, y un administrador puede reintentar a mano o dejar de intentar. */
    ERROR_DE_ENVIO,
    /** SUNAT rechazó el comprobante con un fault 1000–1999: terminal, no se reintenta. */
    ERROR_DE_FORMATO,
    /** No llegó a SUNAT dentro del plazo de envío: terminal, hay que emitir de nuevo. */
    FUERA_DE_PLAZO;

    private static final int DESDE = 1000;
    private static final int HASTA = 1999;

    /** La clase de un comprobante; vacía si no está en la cola. {@code cdrCodigo} solo cuenta en un rechazo. */
    public static Optional<ClaseDeError> de(EstadoDocumento estado, String cdrCodigo) {
        return switch (estado) {
            case ERROR_ENVIO -> Optional.of(ERROR_DE_ENVIO);
            case FUERA_DE_PLAZO -> Optional.of(FUERA_DE_PLAZO);
            case RECHAZADO -> esCodigoDeFormato(cdrCodigo) ? Optional.of(ERROR_DE_FORMATO) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** Cuatro dígitos, de 1000 a 1999. */
    static boolean esCodigoDeFormato(String codigo) {
        if (codigo == null || codigo.length() != 4 || !codigo.chars().allMatch(c -> c >= '0' && c <= '9')) return false;
        int n = Integer.parseInt(codigo);
        return n >= DESDE && n <= HASTA;
    }

    /** Si un administrador puede actuar sobre ella (reintentar o descartar): solo un error de envío; las otras dos ya son terminales. */
    public boolean accionable() { return this == ERROR_DE_ENVIO; }
}
