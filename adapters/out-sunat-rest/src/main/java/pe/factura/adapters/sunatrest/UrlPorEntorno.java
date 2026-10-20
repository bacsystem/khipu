package pe.factura.adapters.sunatrest;

import pe.factura.domain.tenant.Entorno;

/** Base de una API de SUNAT según el entorno de la empresa, sin la barra final (las rutas se le agregan con la suya). */
public record UrlPorEntorno(String beta, String produccion) {
    public UrlPorEntorno {
        beta = sinBarraFinal(beta);
        produccion = sinBarraFinal(produccion);
    }

    public String para(Entorno e) { return e == Entorno.PRODUCCION ? produccion : beta; }

    private static String sinBarraFinal(String u) {
        if (u == null || u.isBlank()) throw new IllegalStateException("URL de la API de SUNAT vacía");
        String r = u.strip();
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }
}
