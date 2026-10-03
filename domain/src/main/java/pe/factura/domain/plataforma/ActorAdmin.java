package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quién ejecuta una acción administrativa: un administrador con sesión (JWT) o, para herramientas internas,
 * la clave de plataforma ({@code X-Platform-Key}), que no identifica a ninguna persona. La IP es obligatoria:
 * una bitácora sin ella no permite explicar de dónde vino la acción.
 */
public record ActorAdmin(Tipo tipo, UUID administradorId, String ip) {
    public enum Tipo { ADMINISTRADOR, CLAVE_PLATAFORMA }

    public ActorAdmin {
        if (tipo == null) throw new DomainException("ACTOR_INVALIDO", "Tipo de actor requerido");
        if (ip == null || ip.isBlank()) throw new DomainException("ACTOR_INVALIDO", "IP de origen requerida");
        if (tipo == Tipo.ADMINISTRADOR && administradorId == null)
            throw new DomainException("ACTOR_INVALIDO", "Un administrador debe tener id");
        if (tipo == Tipo.CLAVE_PLATAFORMA && administradorId != null)
            throw new DomainException("ACTOR_INVALIDO", "La clave de plataforma no identifica a un administrador");
        ip = normalizarIp(ip);
    }

    public static ActorAdmin administrador(UUID administradorId, String ip) { return new ActorAdmin(Tipo.ADMINISTRADOR, administradorId, ip); }

    public static ActorAdmin clavePlataforma(String ip) { return new ActorAdmin(Tipo.CLAVE_PLATAFORMA, null, ip); }

    /**
     * La IP en una forma única, para que una bitácora de auditoría se pueda buscar (#208). Una IPv6 llega con grafías distintas
     * según la ruta: la JVM la escribe sin comprimir ({@code 2001:db8:0:0:0:0:0:1}, lo que Tomcat da para una conexión directa) y
     * el portal reenvía la forma comprimida ({@code 2001:db8::1}). Se deja siempre en la forma de la JVM, y una IPv4 mapeada en
     * IPv6 ({@code ::ffff:203.0.113.7}) en su IPv4, como hace la JVM. IPv4 y todo lo que no sea una IPv6 válida quedan tal cual.
     *
     * <p>No usa {@code InetAddress.getByName}: ante un texto que parece inválido consulta el DNS, y esto corre en la ruta de auditoría.
     */
    public static String normalizarIp(String ip) {
        if (ip == null) return null;
        String s = ip.trim();
        if (s.indexOf(':') < 0) return s;
        int[] g = gruposIpv6(s);
        if (g == null) return s;
        boolean ipv4Mapeada = g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0xffff;
        if (ipv4Mapeada) return (g[6] >> 8) + "." + (g[6] & 0xff) + "." + (g[7] >> 8) + "." + (g[7] & 0xff);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(i == 0 ? "" : ":").append(Integer.toHexString(g[i]));
        return sb.toString();
    }

    /** Los 8 grupos de 16 bits de una IPv6 (con {@code ::} y/o cola IPv4), o {@code null} si el texto no es una IPv6 válida. */
    private static int[] gruposIpv6(String s) {
        String texto = s;
        int ultimoDosPuntos = s.lastIndexOf(':');
        String cola = s.substring(ultimoDosPuntos + 1);
        if (cola.indexOf('.') >= 0) {
            int[] o = octetosIpv4(cola);
            if (o == null) return null;
            texto = s.substring(0, ultimoDosPuntos + 1) + Integer.toHexString(o[0] << 8 | o[1]) + ":" + Integer.toHexString(o[2] << 8 | o[3]);
        }
        String[] lados = texto.split("::", -1);
        if (lados.length > 2) return null;
        List<Integer> izquierda = grupos(lados[0]);
        List<Integer> derecha = lados.length == 2 ? grupos(lados[1]) : List.of();
        if (izquierda == null || derecha == null) return null;
        int[] resultado = new int[8];
        if (lados.length == 1) {
            if (izquierda.size() != 8) return null;
            for (int i = 0; i < 8; i++) resultado[i] = izquierda.get(i);
        } else {
            // `::` representa al menos un grupo de ceros.
            if (izquierda.size() + derecha.size() > 7) return null;
            for (int i = 0; i < izquierda.size(); i++) resultado[i] = izquierda.get(i);
            for (int i = 0; i < derecha.size(); i++) resultado[8 - derecha.size() + i] = derecha.get(i);
        }
        return resultado;
    }

    private static List<Integer> grupos(String lado) {
        List<Integer> grupos = new ArrayList<>();
        if (lado.isEmpty()) return grupos;
        for (String g : lado.split(":", -1)) {
            if (!g.matches("[0-9A-Fa-f]{1,4}")) return null;
            grupos.add(Integer.parseInt(g, 16));
        }
        return grupos;
    }

    private static int[] octetosIpv4(String texto) {
        String[] partes = texto.split("\\.", -1);
        if (partes.length != 4) return null;
        int[] octetos = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!partes[i].matches("\\d{1,3}")) return null;
            octetos[i] = Integer.parseInt(partes[i]);
            if (octetos[i] > 255) return null;
        }
        return octetos;
    }
}
