package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;
import pe.factura.domain.tenant.Ruc;

/**
 * Persona o empresa de una guía de remisión que se identifica con un documento del catálogo 06: el destinatario de las dos guías y el remitente de la
 * guía transportista. Se valida al emitir ({@link #exigirValida}), no al rehidratar.
 */
public record ParteDeGuia(String tipoDoc, String numDoc, String nombre) {
    public ParteDeGuia {
        tipoDoc = TextoDeGuia.limpio(tipoDoc);
        numDoc = numDoc == null ? null : numDoc.strip().toUpperCase(java.util.Locale.ROOT);
        nombre = TextoDeGuia.limpio(nombre);
    }

    public boolean esRuc() { return "6".equals(tipoDoc); }

    /** Si es el contribuyente con ese RUC (el emisor, en las reglas 2554, 2555 y 2560). */
    public boolean esElRuc(String ruc) { return esRuc() && numDoc != null && numDoc.equals(ruc); }

    /**
     * Tipo de documento del catálogo 06; el RUC con su dígito verificador, el DNI de 8 dígitos y los demás de hasta 15 letras o dígitos; el nombre o
     * razón social de 3 a 250 caracteres en una línea. {@code reglas} son los códigos de SUNAT del rol: tipo, número y nombre (p. ej. 3383, 3384, 3387 del
     * remitente); sin ellos el mensaje no cita ninguno, porque la hoja de validaciones de la GRE no está en el repositorio.
     */
    void exigirValida(String rol, String codigoError, String reglaTipo, String reglaNumero, String reglaNombre) {
        if (tipoDoc == null || "-".equals(tipoDoc) || !CatalogoSunat.porId("06").map(c -> c.contiene(tipoDoc)).orElse(false))
            throw new DomainException(codigoError, cita(reglaTipo) + "El tipo de documento del " + rol + " es del catálogo 06 (6 RUC, 1 DNI, 4 carné de extranjería, 7 pasaporte…); recibido " + tipoDoc);
        if (esRuc()) Ruc.exigirValido(numDoc, codigoError, cita(reglaNumero) + "El RUC del " + rol);
        else if ("1".equals(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("\\d{8}")) throw new DomainException(codigoError, cita(reglaNumero) + "El DNI del " + rol + " tiene 8 dígitos");
        } else if (numDoc == null || !numDoc.matches("[A-Z0-9]{1,15}"))
            throw new DomainException(codigoError, cita(reglaNumero) + "El número de documento del " + rol + " tiene hasta 15 letras o dígitos");
        if (!TextoDeGuia.unaLinea(nombre, 3, 250))
            throw new DomainException(codigoError, cita(reglaNombre) + "El nombre o razón social del " + rol + " tiene de 3 a 250 caracteres, sin saltos de línea");
    }

    static String cita(String regla) { return regla == null ? "" : regla + " - "; }
}
