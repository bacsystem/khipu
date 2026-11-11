package pe.factura.application.port.out;

import pe.factura.domain.tenant.Entorno;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Lo que ya ocupa un tope del plan de una cuenta (#18, #613): los documentos de **todas** sus empresas que consumieron o pueden llegar a consumir
 * ({@code EstadoDocumento.ocupaElTope}), emitidos en un entorno, por su fecha de emisión. En Producción es el tope de documentos (#580); en Beta, el de pruebas (#613):
 * cada entorno tiene su bolsa y uno no ocupa la del otro.
 */
public interface TopeDeDocumentosRepository {
    /**
     * Bloquea la cuenta hasta el fin de la transacción y devuelve cuántos documentos del {@code entorno} ocupan su tope en el mes {@code mes}, o en toda la vida de la
     * cuenta si {@code mes} es nulo (un tope {@code TOTAL}, #613). Con el bloqueo, dos emisiones simultáneas de la misma cuenta (aunque sean de empresas distintas)
     * no pueden ver las dos el último lugar libre. Tiene que correr dentro de la transacción que emite.
     */
    long ocupadosBloqueando(UUID cuentaId, Entorno entorno, YearMonth mes);
}
