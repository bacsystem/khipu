package pe.factura.domain.webhook;

import pe.factura.domain.DomainException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * La URL a la que khipu entrega los webhooks (#19). Aquí solo se valida la forma; que el nombre no apunte a una red interna lo comprueba quien la resuelve
 * (el adaptador de salida), porque depende del DNS del momento.
 */
public final class UrlDeWebhook {
    private UrlDeWebhook() {}

    public static final int MAXIMO = 500;

    /**
     * La URL normalizada (sin espacios en los extremos). Debe ser {@code https} (o {@code http} si {@code permitirHttp}), traer el servidor, no llevar usuario ni
     * contraseña ni fragmento y medir hasta {@value #MAXIMO} caracteres. Lanza {@code WEBHOOK_INVALIDO}.
     */
    public static String validar(String url, boolean permitirHttp) {
        if (url == null || url.isBlank()) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook es obligatoria");
        String limpia = url.strip();
        if (limpia.length() > MAXIMO) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook no puede pasar de " + MAXIMO + " caracteres");
        URI uri;
        try {
            uri = new URI(limpia);
        } catch (URISyntaxException e) {
            throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook no es válida");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        boolean esquemaOk = "https".equals(esquema) || (permitirHttp && "http".equals(esquema));
        if (!esquemaOk) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook debe empezar con https://");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook debe traer el servidor, por ejemplo https://mi-sistema.pe/khipu");
        if (uri.getRawUserInfo() != null) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook no puede llevar usuario ni contraseña; use el secreto de firma");
        if (uri.getRawFragment() != null) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook no puede llevar fragmento (#)");
        return limpia;
    }
}
