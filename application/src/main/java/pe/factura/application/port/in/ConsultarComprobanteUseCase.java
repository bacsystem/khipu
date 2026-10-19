package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

public interface ConsultarComprobanteUseCase {
    Comprobante obtener(UUID tenantId, UUID id);                       // DomainException("NO_ENCONTRADO")
    /** Notas de crédito/débito emitidas sobre una factura de la empresa, en orden de emisión (vacío si no es factura o no tiene). */
    List<Comprobante> notasDe(UUID tenantId, Comprobante factura);
    /** Historial de intentos y cambios de estado del comprobante (#4), del más antiguo al más reciente; vacío si no hay. */
    List<pe.factura.domain.documento.EventoDocumento> eventos(UUID tenantId, Comprobante comprobante);
    List<Comprobante> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, Filtro filtro);

    /**
     * Filtros del listado (#2, #3): todos opcionales y combinables; {@code desde}/{@code hasta} acotan la fecha de emisión,
     * inclusive; {@code serie} es la serie exacta (F001, FC01…), en mayúsculas.
     */
    record Filtro(EstadoDocumento estado, java.time.LocalDate desde, java.time.LocalDate hasta, String serie, String tipo, String cliente, Long numero) {
        public static final Filtro NINGUNO = new Filtro(null, null, null, null);
        public static final java.util.Set<String> TIPOS = java.util.Set.of("01", "03", "07", "08");
        public Filtro(EstadoDocumento estado, java.time.LocalDate desde, java.time.LocalDate hasta) { this(estado, desde, hasta, null); }
        public Filtro(EstadoDocumento estado, java.time.LocalDate desde, java.time.LocalDate hasta, String serie) { this(estado, desde, hasta, serie, null, null, null); }
        public Filtro {
            if (desde != null && hasta != null && desde.isAfter(hasta))
                throw new pe.factura.domain.DomainException("RANGO_INVALIDO", "desde (" + desde + ") no puede ser posterior a hasta (" + hasta + ")");
            serie = serie == null || serie.isBlank() ? null : serie.strip().toUpperCase(java.util.Locale.ROOT);
            if (serie != null && !serie.matches("[A-Z][A-Z0-9]{3}"))
                throw new pe.factura.domain.DomainException("PARAMETRO_INVALIDO", "La serie tiene 4 caracteres: una letra y tres alfanuméricos (F001, FC01, B001…): " + serie);
            tipo = tipo == null || tipo.isBlank() ? null : tipo.strip();
            if (tipo != null && !TIPOS.contains(tipo))
                throw new pe.factura.domain.DomainException("PARAMETRO_INVALIDO", "tipo debe ser 01 (factura), 03 (boleta), 07 (nota de crédito) u 08 (nota de débito): " + tipo);
            cliente = cliente == null || cliente.isBlank() ? null : cliente.strip();
            if (cliente != null && cliente.length() > 100)
                throw new pe.factura.domain.DomainException("PARAMETRO_INVALIDO", "cliente admite hasta 100 caracteres");
            if (numero != null && numero < 1)
                throw new pe.factura.domain.DomainException("PARAMETRO_INVALIDO", "numero debe ser un entero positivo");
        }
    }
    byte[] xml(UUID tenantId, UUID id);                                // bytes del XML firmado
    byte[] cdr(UUID tenantId, UUID id);                                // ZIP del CDR; DomainException("SIN_CDR")
    byte[] cdrXml(UUID tenantId, UUID id);                             // XML dentro del ZIP del CDR
    /** Representación impresa (PDF con QR y hash) de un comprobante ya firmado; se genera una vez y se guarda junto al XML. */
    byte[] pdf(UUID tenantId, UUID id);
}
