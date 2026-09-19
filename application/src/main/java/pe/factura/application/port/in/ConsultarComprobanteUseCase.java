package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

public interface ConsultarComprobanteUseCase {
    Comprobante obtener(UUID tenantId, UUID id);                       // DomainException("NO_ENCONTRADO")
    /** Notas de crédito/débito emitidas sobre una factura de la empresa, en orden de emisión (vacío si no es factura o no tiene). */
    List<Comprobante> notasDe(UUID tenantId, Comprobante factura);
    List<Comprobante> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, Filtro filtro);

    /** Filtros del listado (#2): todos opcionales y combinables; {@code desde}/{@code hasta} acotan la fecha de emisión, inclusive. */
    record Filtro(EstadoDocumento estado, java.time.LocalDate desde, java.time.LocalDate hasta) {
        public static final Filtro NINGUNO = new Filtro(null, null, null);
        public static Filtro porEstado(EstadoDocumento estado) { return new Filtro(estado, null, null); }
        public Filtro {
            if (desde != null && hasta != null && desde.isAfter(hasta))
                throw new pe.factura.domain.DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
        }
    }
    byte[] xml(UUID tenantId, UUID id);                                // bytes del XML firmado
    byte[] cdr(UUID tenantId, UUID id);                                // ZIP del CDR; DomainException("SIN_CDR")
    byte[] cdrXml(UUID tenantId, UUID id);                             // XML dentro del ZIP del CDR
    /** Representación impresa (PDF con QR y hash) de un comprobante ya firmado; se genera una vez y se guarda junto al XML. */
    byte[] pdf(UUID tenantId, UUID id);
}
