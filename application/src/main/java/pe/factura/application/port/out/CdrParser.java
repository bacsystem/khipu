package pe.factura.application.port.out;

import pe.factura.domain.documento.Cdr;

public interface CdrParser {
    Cdr parsear(byte[] cdrZip);
}
