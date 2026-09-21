package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Unidad comercial: agrupa usuarios y empresas (tenants). {@code telefono}: celular de contacto en Perú, 9 dígitos que
 * empiezan con 9 (se acepta con prefijo +51/51 o con espacios/guiones y se normaliza sin ellos); opcional a nivel de
 * dominio (las cuentas existentes antes de este campo no lo tienen) pero exigido al registrarse (regla del formulario).
 */
public record Cuenta(UUID id, String nombre, String email, String telefono) {
    private static final Pattern TELEFONO = Pattern.compile("^9\\d{8}$");

    public Cuenta {
        if (nombre == null || nombre.isBlank()) throw new DomainException("NOMBRE_REQUERIDO", "El nombre de la cuenta es obligatorio");
        email = Usuario.normalizarEmail(email);
        telefono = normalizarTelefono(telefono);
    }

    /** Sin celular: {@code null} (cuentas anteriores a este campo). */
    public Cuenta(UUID id, String nombre, String email) {
        this(id, nombre, email, null);
    }

    static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String limpio = telefono.replaceAll("[\\s-]", "");
        if (limpio.startsWith("+51")) limpio = limpio.substring(3);
        else if (limpio.startsWith("51") && limpio.length() == 11) limpio = limpio.substring(2);
        if (!TELEFONO.matcher(limpio).matches())
            throw new DomainException("TELEFONO_INVALIDO", "El celular debe tener 9 dígitos y empezar con 9 (Perú): " + telefono);
        return limpio;
    }
}
