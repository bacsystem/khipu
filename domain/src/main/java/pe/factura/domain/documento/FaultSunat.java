package pe.factura.domain.documento;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El fault de SUNAT de un comprobante con problema (#196): su código y su mensaje. Un rechazo los guarda en el CDR; un error de envío y un fuera de plazo, en el último
 * error, como «código - mensaje». Un fallo nuestro («INFRA - …») o un texto libre no tienen código de SUNAT: se muestran enteros.
 */
public record FaultSunat(String codigo, String mensaje) {
    private static final Pattern CON_CODIGO = Pattern.compile("^(\\d{4}) - (.*)$", Pattern.DOTALL);

    /** El fault, o {@code null} si no hay nada que mostrar. El CDR manda sobre el último error. */
    public static FaultSunat de(String ultimoError, String cdrCodigo, String cdrDescripcion) {
        if (cdrCodigo != null && !cdrCodigo.isBlank()) return new FaultSunat(cdrCodigo, cdrDescripcion);
        if (ultimoError == null || ultimoError.isBlank()) return null;
        Matcher m = CON_CODIGO.matcher(ultimoError);
        return m.matches() ? new FaultSunat(m.group(1), m.group(2)) : new FaultSunat(null, ultimoError);
    }
}
