package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

/**
 * Usuario y clave del Clave SOL secundario con el que se autentica el envío a SUNAT.
 *
 * <p>El usuario se normaliza acá y no en el servicio porque el error es silencioso y total: un espacio pegado al
 * copiar y pegar viaja dentro del {@code UsernameToken}, SUNAT rechaza la autenticación en <em>todos</em> los envíos,
 * y el portal sigue mostrando «CONFIGURADAS». La clave no se toca: un espacio al borde puede ser parte de la clave
 * real, y recortarla en silencio rompería una cuenta que funciona.
 */
public record CredencialesSol(String usuario, String clave) {

    public CredencialesSol {
        if (usuario == null || usuario.isBlank() || clave == null || clave.isBlank())
            throw new DomainException("CREDENCIALES_SOL_INVALIDAS", "Usuario y clave SOL son obligatorios");
        usuario = usuario.strip();
        if (usuario.chars().anyMatch(Character::isWhitespace))
            throw new DomainException("CREDENCIALES_SOL_INVALIDAS", "El usuario SOL no admite espacios");
    }

    /** SUNAT concatena RUC + usuario en el UsernameToken. */
    public String usernameToken(String ruc) { return ruc + usuario; }
}
