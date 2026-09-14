package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.util.UUID;
import java.util.regex.Pattern;

public record Usuario(UUID id, UUID cuentaId, String email, String passwordHash, Rol rol, boolean activo) {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Usuario {
        email = normalizarEmail(email);
        if (passwordHash == null || passwordHash.isBlank()) throw new DomainException("PASSWORD_REQUERIDO", "Hash de contraseña requerido");
        if (rol == null) throw new DomainException("ROL_REQUERIDO", "Rol requerido");
    }

    static String normalizarEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new DomainException("EMAIL_INVALIDO", "Correo electrónico inválido");
        return email.trim().toLowerCase();
    }

    /** Política mínima de contraseña: 8+ caracteres, al menos una letra y un dígito. */
    public static void validarPassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*"))
            throw new DomainException("PASSWORD_DEBIL", "La contraseña debe tener al menos 8 caracteres, una letra y un dígito");
    }

    public Usuario desactivar() { return new Usuario(id, cuentaId, email, passwordHash, rol, false); }
    public Usuario conPasswordHash(String hash) { return new Usuario(id, cuentaId, email, hash, rol, activo); }
    public boolean esAdmin() { return rol == Rol.ADMIN; }
}
