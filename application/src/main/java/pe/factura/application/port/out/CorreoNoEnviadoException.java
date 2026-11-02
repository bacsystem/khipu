package pe.factura.application.port.out;

/**
 * El servidor de correo no aceptó un mensaje (#325-H3). El mensaje es solo la {@link Motivo clase de fallo}, en palabras para una persona: se puede mostrar o guardar donde lo ve el
 * cliente. El detalle —host y puerto del SMTP de la plataforma, respuesta cruda del servidor— va en la causa, que solo se registra en el log.
 */
public class CorreoNoEnviadoException extends RuntimeException {
    public enum Motivo {
        CONEXION("No se pudo conectar con el servidor de correo"),
        AUTENTICACION("El servidor de correo rechazó las credenciales de la plataforma"),
        DESTINATARIO_RECHAZADO("El servidor de correo rechazó el destinatario"),
        OTRO("El servidor de correo no aceptó el mensaje");

        private final String descripcion;

        Motivo(String descripcion) { this.descripcion = descripcion; }

        public String descripcion() { return descripcion; }
    }

    private final Motivo motivo;

    public CorreoNoEnviadoException(Motivo motivo, Throwable causa) {
        super(motivo.descripcion(), causa);
        this.motivo = motivo;
    }

    public Motivo motivo() { return motivo; }
}
