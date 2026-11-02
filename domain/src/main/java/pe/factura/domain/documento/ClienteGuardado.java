package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un cliente que la empresa guarda para no teclearlo en cada emisión (#319): el documento (catálogo 06), el nombre, y de forma opcional la dirección, el país y el correo. Se valida
 * con las mismas reglas que el comprador de una boleta —DNI de 8 dígitos, RUC con dígito verificador, los demás documentos hasta 15 caracteres, nombre de 3 a 1500 caracteres—
 * para que lo que se guarda siempre se pueda usar al emitir; «sin documento» no se guarda, porque no identifica a nadie.
 */
public record ClienteGuardado(UUID id, UUID tenantId, String tipoDoc, String numDoc, String razonSocial, String direccion, String pais, String email, Instant creadoEn, Instant actualizadoEn) {
    public static final int DIRECCION_MAXIMA = 300;
    public static final int EMAIL_MAXIMO = 150;
    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** Los datos tal como llegan, ya normalizados y validados; sirve igual para crear que para actualizar. */
    public record Datos(String tipoDoc, String numDoc, String razonSocial, String direccion, String pais, String email) {
        public Datos {
            tipoDoc = limpio(tipoDoc);
            numDoc = limpio(numDoc);
            razonSocial = limpio(razonSocial);
            direccion = limpioOpcional(direccion);
            pais = limpioOpcional(pais) == null ? null : limpioOpcional(pais).toUpperCase(Locale.ROOT);
            email = limpioOpcional(email) == null ? null : limpioOpcional(email).toLowerCase(Locale.ROOT);
        }

        /** Lanza {@code RECEPTOR_INVALIDO} con el mensaje de la regla que no se cumple. */
        public Datos validar() {
            if (Receptor.SIN_DOCUMENTO.equals(tipoDoc)) throw new DomainException("RECEPTOR_INVALIDO", "Un cliente sin documento no se guarda: no identifica a nadie");
            new Receptor(tipoDoc, numDoc, razonSocial, direccion, pais).exigirValidoParaBoleta();
            if (direccion != null && (direccion.length() > DIRECCION_MAXIMA || direccion.chars().anyMatch(Character::isISOControl)))
                throw new DomainException("RECEPTOR_INVALIDO", "La dirección admite hasta " + DIRECCION_MAXIMA + " caracteres, sin saltos de línea");
            if (email != null && (email.length() > EMAIL_MAXIMO || !EMAIL.matcher(email).matches()))
                throw new DomainException("RECEPTOR_INVALIDO", "El correo del cliente no es válido (hasta " + EMAIL_MAXIMO + " caracteres)");
            return this;
        }
    }

    public static ClienteGuardado nuevo(UUID tenantId, Datos datos, Instant ahora) {
        Datos d = datos.validar();
        return new ClienteGuardado(UUID.randomUUID(), tenantId, d.tipoDoc(), d.numDoc(), d.razonSocial(), d.direccion(), d.pais(), d.email(), ahora, ahora);
    }

    /** El mismo cliente con otros datos (también puede cambiar el documento); conserva el id y la fecha de alta. */
    public ClienteGuardado con(Datos datos, Instant ahora) {
        Datos d = datos.validar();
        return new ClienteGuardado(id, tenantId, d.tipoDoc(), d.numDoc(), d.razonSocial(), d.direccion(), d.pais(), d.email(), creadoEn, ahora);
    }

    private static String limpio(String s) { return s == null ? null : s.strip(); }

    private static String limpioOpcional(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
