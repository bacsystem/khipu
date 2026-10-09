package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * La IP del cliente para el límite de intentos por IP (#261), o {@code null} si no se conoce.
 * <p>
 * Solo se conoce con proxies de confianza configurados ({@code TRUSTED_PROXIES}, #208): entonces {@code getRemoteAddr()} ya es la
 * del navegador (Tomcat la reescribe), salvo que la petición llegue de un proxy sin reenviarla, y ahí sigue siendo la del proxy. Sin
 * configurar, todas las peticiones del portal llegan con la IP del servidor de Next: contar por ella dejaría que veinte fallos de
 * cualquiera bloquearan el login de todos los clientes. En esos dos casos no se cuenta por IP y queda solo el límite por correo.
 */
@Component
public class IpDelCliente {
    private final Pattern proxies;

    public IpDelCliente(@Value("${app.trusted-proxies:}") String proxies) {
        this.proxies = proxies == null || proxies.isBlank() ? null : Pattern.compile(proxies.strip());
    }

    public String de(HttpServletRequest req) {
        if (proxies == null) return null;
        String ip = req.getRemoteAddr();
        return ip == null || proxies.matcher(ip).matches() ? null : ip;
    }
}
