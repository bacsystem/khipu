package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

/**
 * Credenciales de la «API SUNAT» (#318): el {@code client_id} y el {@code client_secret} que el contribuyente genera en SOL, opción «Credenciales de API SUNAT»
 * («Id» y «Clave» de la aplicación registrada). Con ellas se pide el token OAuth2 de {@code api-seguridad.sunat.gob.pe}; las usan la consulta integrada de
 * validez y, más adelante, la GRE y el SIRE (que además piden el usuario y la clave SOL: esos ya están en {@link CredencialesSol}).
 *
 * <p>Como en {@link CredencialesSol}, el identificador se normaliza (un espacio pegado al copiar haría fallar todos los tokens) y el secreto no se toca. El
 * {@code toString} nunca muestra el secreto: un record lo imprimiría entero en cualquier log o mensaje que concatene la empresa.
 */
public record CredencialesApiSunat(String clientId, String clientSecret) {
    /** Holgado: SUNAT entrega un UUID (36) y una clave en base64 (~24); un valor mucho más largo es un pegado equivocado. */
    static final int LARGO_MAXIMO = 200;

    public CredencialesApiSunat {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new DomainException("CREDENCIALES_API_SUNAT_INVALIDAS", "El client_id y el client_secret de la API SUNAT son obligatorios");
        clientId = clientId.strip();
        if (clientId.chars().anyMatch(Character::isWhitespace))
            throw new DomainException("CREDENCIALES_API_SUNAT_INVALIDAS", "El client_id de la API SUNAT no admite espacios");
        // El client_id va en la ruta de la URL del token: ni barras ni nada que la cambie de recurso.
        if (!clientId.matches("[A-Za-z0-9._~-]+"))
            throw new DomainException("CREDENCIALES_API_SUNAT_INVALIDAS", "El client_id de la API SUNAT solo admite letras, dígitos, guiones, puntos y guiones bajos");
        if (clientId.length() > LARGO_MAXIMO || clientSecret.length() > LARGO_MAXIMO)
            throw new DomainException("CREDENCIALES_API_SUNAT_INVALIDAS", "El client_id y el client_secret de la API SUNAT admiten hasta " + LARGO_MAXIMO + " caracteres");
    }

    @Override public String toString() { return "CredencialesApiSunat[clientId=" + clientId + ", clientSecret=***]"; }
}
