package pe.factura.application.port.in;

import java.util.List;

/**
 * Avisarles a los clientes que tienen boletas sin informar a SUNAT (#356). Lo corre el sistema solo, sin un administrador: el plazo es de días y no habría quién lo pidiera a
 * tiempo. Como mucho un correo por empresa y por motivo cada {@code MotivoDeAviso.ENFRIAMIENTO}: el del 5.º día, y otro cuando la más antigua llega al 7.º, el último del resumen
 * diario. Sin envío de correos habilitado en el servidor no hace nada ni deja rastro, y lo reintenta cuando lo haya.
 */
public interface AvisarBoletasSinInformarUseCase {
    /**
     * {@code enviados}: los correos que salieron. {@code fallos}: uno por empresa a la que no se pudo avisar (el correo falló); se reintentan en la próxima corrida, porque la
     * reserva se anula. Un fallo no frena a las demás empresas.
     */
    record Resultado(int enviados, List<String> fallos) {}

    /** {@code urlPortal} es la base del enlace del correo. */
    Resultado avisar(String urlPortal);
}
