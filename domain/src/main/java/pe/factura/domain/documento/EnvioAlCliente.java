package pe.factura.domain.documento;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El envío automático de un comprobante a su cliente (#325): cuando SUNAT lo acepta y la empresa tiene activado «enviar automáticamente», el PDF, el XML y el CDR salen por correo
 * al {@link Comprobante#correoCliente() correo del comprobante}. Uno por comprobante, como mucho: el envío se anota en la misma transacción que el cambio de estado (outbox) y un
 * segundo cambio a aceptado, o una segunda réplica, no anota otro.
 *
 * <p>Estados: {@link Estado#PENDIENTE} hasta que sale o se agota; {@link Estado#ENVIADO} cuando el servidor de correo lo recibió; {@link Estado#FALLIDO} tras
 * {@link #MAXIMO_INTENTOS} intentos o un fallo que reintentar no arregla (adjuntos demasiado grandes); {@link Estado#OMITIDO} cuando no se debía mandar al llegar su turno (el
 * servidor no tiene correo configurado, o el comprobante ya no está aceptado: se anuló antes de salir).
 */
public record EnvioAlCliente(UUID documentoId, String destinatario, Estado estado, int intentos, String ultimoError, Instant siguienteIntento, Instant enviadoEn, Instant creadoEn) {
    public enum Estado { PENDIENTE, ENVIADO, FALLIDO, OMITIDO }

    public static final int MAXIMO_INTENTOS = 5;
    /** La espera tras el intento n (1 a 4): 1 min, 5 min, 30 min, 2 h; el quinto es el último, unas 2 h 36 min después del primero. */
    static final List<Duration> ESPERAS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
    /**
     * Lo más que pesan juntos los adjuntos. Muchos servidores de correo rechazan mensajes de más de 10 MB y el adjunto crece un tercio al codificarse en base64: 7 MB deja margen.
     * Un comprobante normal pesa decenas de KB; solo uno con cientos de líneas y un logo pesado se acerca.
     */
    public static final long TOPE_ADJUNTOS = 7L * 1024 * 1024;

    /**
     * Si un cambio de estado pide el envío: el paso a {@link EstadoDocumento#ACEPTADO} o {@link EstadoDocumento#ACEPTADO_CON_OBS} desde otro estado. Vale igual para la factura
     * (CDR propio) que para la boleta informada en un resumen diario: SUNAT la acepta al aceptar el resumen y desde ese momento es válida.
     */
    public static boolean correspondeA(EventoDocumento e) {
        return e.estadoNuevo().esFinalAceptado() && e.estadoAnterior() != e.estadoNuevo();
    }

    /** Cuándo reintentar tras el intento {@code intento} fallido (1, 2…), o {@code null} si fue el último y el envío queda {@link Estado#FALLIDO}. */
    public static Instant siguienteIntento(int intento, Instant ahora) {
        if (intento >= MAXIMO_INTENTOS) return null;
        return ahora.plus(ESPERAS.get(Math.min(intento, ESPERAS.size()) - 1));
    }
}
