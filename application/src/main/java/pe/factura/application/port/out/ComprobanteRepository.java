package pe.factura.application.port.out;

import pe.factura.application.port.in.ConsultarComprobanteUseCase;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository {
    void guardar(Comprobante c); // insert o update por id
    Optional<Comprobante> buscar(UUID tenantId, UUID id);
    /** Como buscar, pero deja la fila bloqueada hasta el fin de la transacción (p. ej. para dar de baja sin carreras). */
    Optional<Comprobante> bloquear(UUID tenantId, UUID id);
    /**
     * Hasta {@code limite} boletas (y notas de boleta) de la empresa emitidas el {@code fechaEmision} que SUNAT todavía no tiene (FIRMADO o ERROR_ENVIO), sin un resumen diario en curso y
     * sin un resumen de varias líneas rechazado antes (#301: ese se aísla de a una), ordenadas por serie y número y con las filas bloqueadas —las que otra transacción ya tiene
     * bloqueadas se saltan—. Para armar con ellas un solo resumen diario; el llamador filtra las que todavía no van solo en el resumen ({@code soloPorResumen}).
     */
    List<Comprobante> bloquearPorInformarDeLaFecha(UUID tenantId, LocalDate fechaEmision, UUID excluido, int limite);
    Optional<Comprobante> buscarPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    /** Como buscarPorNumero, pero deja la fila bloqueada hasta el fin de la transacción (serializa a quienes dependen de ese comprobante). */
    Optional<Comprobante> bloquearPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    /** Suma de los montos ya regularizados de una factura de anticipo en facturas finales no rechazadas ni inválidas. */
    BigDecimal montoRegularizado(UUID tenantId, String serieAnticipo, long numeroAnticipo);
    /** Notas de crédito/débito emitidas sobre la factura serie-número (cualquier estado), en orden de emisión. */
    List<Comprobante> notasDe(UUID tenantId, String serieFactura, long numeroFactura);
    /**
     * Comprobantes de cualquier empresa aún no enviados (FIRMADO o ERROR_ENVIO) emitidos hasta {@code fechaEmisionMaxima} inclusive, para el control del plazo. Solo
     * los que pueden vencer: no la boleta ni la nota B que va en el resumen diario, que se informa fuera de plazo (#331).
     */
    List<Comprobante> pendientesQueVencenEmitidosHasta(LocalDate fechaEmisionMaxima);
    /** Comprobantes firmados de cualquier empresa sin CDR en el storage: ENVIADO, ERROR_ENVIO, o aceptados/rechazados que lo perdieron. */
    List<Comprobante> pendientesDeCdr();
    /** Historial de cambios de estado del comprobante (#4), en orden cronológico; vacío si no tiene. */
    List<pe.factura.domain.documento.EventoDocumento> eventosDe(UUID tenantId, UUID comprobanteId);
    /** Comprobantes ya firmados (con XML en el storage) de cualquier empresa emitidos entre las dos fechas inclusive, para verificar la integridad del storage. */
    List<Comprobante> firmadosEmitidosEntre(java.time.LocalDate desde, java.time.LocalDate hasta);
    List<Comprobante> listar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro);
    /** #321: ver {@link ConsultarComprobanteUseCase#listarPorLlave}. */
    ConsultarComprobanteUseCase.PaginaPorLlave listarPorLlave(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, java.util.Set<EstadoDocumento> excluidos,
                                                              ConsultarComprobanteUseCase.Llave despues, int limite);
    long contar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, java.util.Set<EstadoDocumento> excluidos);
}
