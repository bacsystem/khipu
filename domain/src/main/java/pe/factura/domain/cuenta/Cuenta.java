package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.util.UUID;

/** Unidad comercial: agrupa usuarios y empresas (tenants). */
public record Cuenta(UUID id, String nombre, String email) {
    public Cuenta {
        if (nombre == null || nombre.isBlank()) throw new DomainException("NOMBRE_REQUERIDO", "El nombre de la cuenta es obligatorio");
        email = Usuario.normalizarEmail(email);
    }
}
