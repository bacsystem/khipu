package pe.factura.application.port.out;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository {
    void guardar(Comprobante c); // insert o update por id
    Optional<Comprobante> buscar(UUID tenantId, UUID id);
    /** Como buscar, pero deja la fila bloqueada hasta el fin de la transacción (p. ej. para dar de baja sin carreras). */
    Optional<Comprobante> bloquear(UUID tenantId, UUID id);
    Optional<Comprobante> buscarPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    /** Como buscarPorNumero, pero deja la fila bloqueada hasta el fin de la transacción (serializa a quienes dependen de ese comprobante). */
    Optional<Comprobante> bloquearPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    /** Suma de los montos ya regularizados de una factura de anticipo en facturas finales no rechazadas ni inválidas. */
    BigDecimal montoRegularizado(UUID tenantId, String serieAnticipo, long numeroAnticipo);
    /** Notas de crédito/débito emitidas sobre la factura serie-número (cualquier estado), en orden de emisión. */
    List<Comprobante> notasDe(UUID tenantId, String serieFactura, long numeroFactura);
    /** Comprobantes de cualquier empresa aún no enviados (FIRMADO o ERROR_ENVIO) emitidos hasta {@code fechaEmisionMaxima} inclusive, para el control del plazo. */
    List<Comprobante> pendientesDeEnvioEmitidosHasta(java.time.LocalDate fechaEmisionMaxima);
    /** Comprobantes firmados de cualquier empresa sin CDR en el storage: ENVIADO, ERROR_ENVIO, o aceptados/rechazados que lo perdieron. */
    List<Comprobante> pendientesDeCdr();
    /** Comprobantes ya firmados (con XML en el storage) de cualquier empresa emitidos entre las dos fechas inclusive, para verificar la integridad del storage. */
    List<Comprobante> firmadosEmitidosEntre(java.time.LocalDate desde, java.time.LocalDate hasta);
    List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina);
    long contar(UUID tenantId, EstadoDocumento estado);
}
