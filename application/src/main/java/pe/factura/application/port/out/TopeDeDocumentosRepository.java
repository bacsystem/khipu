package pe.factura.application.port.out;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Lo que ya ocupa el tope de documentos del plan de una cuenta en un mes (#18): los documentos de **todas** sus empresas que consumieron o pueden llegar a consumir
 * ({@code EstadoDocumento.ocupaElTope}), por su fecha de emisión, dentro del mes calendario.
 */
public interface TopeDeDocumentosRepository {
    /**
     * Bloquea la cuenta hasta el fin de la transacción y devuelve cuántos documentos ocupan su tope en el mes. Con el bloqueo, dos emisiones simultáneas de la
     * misma cuenta (aunque sean de empresas distintas) no pueden ver las dos el último lugar libre. Tiene que correr dentro de la transacción que emite.
     */
    long ocupadosBloqueando(UUID cuentaId, YearMonth mes);
}
