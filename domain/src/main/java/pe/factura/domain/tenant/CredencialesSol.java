package pe.factura.domain.tenant;
public record CredencialesSol(String usuario, String clave) {
    /** SUNAT concatena RUC + usuario en el UsernameToken. */
    public String usernameToken(String ruc) { return ruc + usuario; }
}
