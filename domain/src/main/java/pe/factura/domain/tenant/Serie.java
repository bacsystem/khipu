package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;

import java.util.UUID;

/** Serie de numeración de un tipo de comprobante; {@code establecimiento} es el código del anexo desde el que se emite ({@code 0000} = domicilio fiscal). */
public record Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa, String establecimiento) {

    /**
     * Último correlativo que SUNAT admite. La regla 1001 define el ID del comprobante como
     * {@code [FB][A-Z0-9]{3}-[0-9]{1,8}}: ocho dígitos, no más. Pasado ese techo el comprobante se firma y pasa el
     * XSD, pero SUNAT lo rechaza con 1001 con el correlativo ya consumido, y el intento siguiente vuelve a pasarse:
     * la serie queda inservible y no hay endpoint para editarla. De ahí que el tope viva en el dominio.
     */
    public static final long NUMERO_MAXIMO = 99_999_999L;

    public Serie {
        establecimiento = establecimiento == null || establecimiento.isBlank() ? Domicilio.ESTABLECIMIENTO_PRINCIPAL : establecimiento.strip();
        if (!establecimiento.matches("\\d{4}"))
            throw new DomainException("ESTABLECIMIENTO_INVALIDO", "3030 - El código del establecimiento de la serie son 4 dígitos (0000 = domicilio fiscal)");
        if (ultimoNumero < 0 || ultimoNumero > NUMERO_MAXIMO)
            throw new DomainException("CORRELATIVO_INVALIDO",
                    "1001 - El correlativo de la serie va de 0 a " + NUMERO_MAXIMO + " (8 dígitos); llegó " + ultimoNumero);
    }

    /** Si el próximo número ya no cabe en ocho dígitos, la serie está agotada y hay que emitir con otra. */
    public boolean agotada() { return ultimoNumero >= NUMERO_MAXIMO; }

    public Serie(UUID tenantId, TipoDocumento tipo, String codigo, long ultimoNumero, boolean activa) {
        this(tenantId, tipo, codigo, ultimoNumero, activa, Domicilio.ESTABLECIMIENTO_PRINCIPAL);
    }

    public boolean enDomicilioFiscal() { return Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(establecimiento); }
}
