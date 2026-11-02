package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Administrador de la plataforma: no pertenece a ninguna cuenta de cliente ni comparte el modelo de
 * {@code Rol} de {@code pe.factura.domain.cuenta} — es un concepto aparte a propósito, con su propia
 * sesión JWT (ver {@code AdministradorTokenEmisor}), para que un token de cliente nunca pueda pasar
 * por un token de administrador. La validación de email/password se duplica de Usuario en vez de
 * importarla: son dos dominios que no deben conocerse entre sí.
 */
public record Administrador(UUID id, String email, String passwordHash, boolean activo) {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Administrador {
        email = normalizarEmail(email);
        if (passwordHash == null || passwordHash.isBlank()) throw new DomainException("PASSWORD_REQUERIDO", "Hash de contraseña requerido");
    }

    static String normalizarEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new DomainException("EMAIL_INVALIDO", "Correo electrónico inválido");
        return email.trim().toLowerCase();
    }

    /** Misma política mínima que Usuario: 8+ caracteres, al menos una letra y un dígito. */
    public static void validarPassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*"))
            throw new DomainException("PASSWORD_DEBIL", "La contraseña debe tener al menos 8 caracteres, una letra y un dígito");
    }
}
