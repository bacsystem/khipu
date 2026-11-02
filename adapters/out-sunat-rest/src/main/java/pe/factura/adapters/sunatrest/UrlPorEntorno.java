package pe.factura.adapters.sunatrest;

import pe.factura.domain.tenant.Entorno;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Base de una API de SUNAT según el entorno de la empresa, sin la barra final (las rutas se le agregan con la suya). Se valida al construir —es decir, al
 * arrancar—: una URL sin esquema ({@code api.sunat.gob.pe}) fallaría recién en la primera consulta, con un 500 y sin fallback.
 */
public record UrlPorEntorno(String beta, String produccion) {
    public UrlPorEntorno {
        beta = validada(beta, "beta");
        produccion = validada(produccion, "producción");
    }

    public String para(Entorno e) { return e == Entorno.PRODUCCION ? produccion : beta; }

    private static String validada(String u, String cual) {
        if (u == null || u.isBlank()) throw new IllegalStateException("URL de la API de SUNAT (" + cual + ") vacía");
        String r = u.strip();
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        URI uri;
        try {
            uri = new URI(r);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("URL de la API de SUNAT (" + cual + ") inválida: " + r, e);
        }
        boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getHost().isBlank())
            throw new IllegalStateException("URL de la API de SUNAT (" + cual + ") inválida: " + r + " (debe ser http(s)://servidor[/ruta])");
        return r;
    }
}
