package pe.factura.domain.tenant;
import pe.factura.domain.documento.TipoDocumento;
import java.util.UUID;
public record Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa) {}
