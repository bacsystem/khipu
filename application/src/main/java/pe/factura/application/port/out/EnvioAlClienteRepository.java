package pe.factura.application.port.out;

import pe.factura.domain.documento.EnvioAlCliente;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El envío automático de los comprobantes a sus clientes (#325). El envío se anota solo, en la misma transacción que el comprobante pasa a aceptado (lo hace el repositorio de
 * comprobantes, como los webhooks): aquí están la preferencia de la empresa y lo que necesita quien los manda.
 */
public interface EnvioAlClienteRepository {
    /** Si la empresa activó «enviar automáticamente» el comprobante aceptado a su cliente. */
    boolean automatico(UUID tenantId);

    void fijarAutomatico(UUID tenantId, boolean automatico);

    /** Un envío tomado por esta instancia hasta {@code bloqueo}: las escrituras de su resultado solo valen si sigue tomado con esa marca. */
    record Tomado(UUID tenantId, UUID documentoId, String destinatario, int intentos, Instant bloqueo) {}

    /** Hasta {@code limite} envíos pendientes cuyo turno llegó, tomados por {@code bloqueo} (otra instancia no los toma mientras tanto). Dentro de una transacción. */
    List<Tomado> tomarVencidos(int limite, Duration bloqueo);

    /**
     * Justo antes de mandarlo (#325-H1): extiende el bloqueo por {@code bloqueo} desde ahora, solo si el envío sigue pendiente y tomado con la marca de {@code t}. Vacío si otra instancia
     * lo retomó (venció el bloqueo mientras esta atendía otro): entonces no se manda, para no mandarlo dos veces.
     */
    Optional<Tomado> renovar(Tomado t, Duration bloqueo);

    /** El servidor de correo lo recibió. {@code false} si ya no estaba tomado con esa marca (otra instancia lo volvió a tomar). */
    boolean registrarEnvio(Tomado t, Instant ahora);

    /** Un intento fallido: vuelve a pendiente para {@code siguiente}, o queda {@link EnvioAlCliente.Estado#FALLIDO} si {@code siguiente} es {@code null}. */
    boolean registrarFallo(Tomado t, String error, Instant ahora, Instant siguiente);

    /** No se manda, y no se reintenta: {@link EnvioAlCliente.Estado#OMITIDO} con el motivo. */
    boolean omitir(Tomado t, String motivo, Instant ahora);

    /** El envío de un comprobante de la empresa, si se anotó. */
    Optional<EnvioAlCliente> deComprobante(UUID tenantId, UUID documentoId);
}
