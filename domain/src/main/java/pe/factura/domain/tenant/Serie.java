package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;

import java.util.UUID;

/** Serie de numeración de un tipo de comprobante; {@code establecimiento} es el código del anexo desde el que se emite ({@code 0000} = domicilio fiscal). */
public record Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa, String establecimiento) {

    public Serie {
        establecimiento = establecimiento == null || establecimiento.isBlank() ? Domicilio.ESTABLECIMIENTO_PRINCIPAL : establecimiento.strip();
        if (!establecimiento.matches("\\d{4}"))
            throw new DomainException("ESTABLECIMIENTO_INVALIDO", "3030 - El código del establecimiento de la serie son 4 dígitos (0000 = domicilio fiscal)");
    }

    public Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa) {
        this(tenantId, tipo, codigo, ultimoNumero, activa, Domicilio.ESTABLECIMIENTO_PRINCIPAL);
    }

    public boolean enDomicilioFiscal() { return Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(establecimiento); }
}
