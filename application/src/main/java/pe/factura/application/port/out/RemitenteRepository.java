package pe.factura.application.port.out;

import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** El remitente que un administrador fijó para los correos de la plataforma (#199). Sin uno guardado, sale el de la configuración del servidor (`MAIL_REMITENTE`). */
public interface RemitenteRepository {
    record Guardado(RemitenteDeCorreo remitente, Instant actualizadoEn) {}

    Optional<Guardado> buscar();

    /** Crea o reemplaza el remitente. {@code por} es el administrador que lo cambió. */
    void guardar(RemitenteDeCorreo remitente, Instant ahora, UUID por);

    /** Quita el remitente propio: se vuelve al de la configuración del servidor. {@code true} si había uno. */
    boolean quitar();
}
