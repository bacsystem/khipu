package pe.factura.adapters.sunat;

import pe.factura.domain.tenant.Entorno;

public record SunatUrls(String beta, String produccion) {
    public String para(Entorno e) { return e == Entorno.PRODUCCION ? produccion : beta; }
}
