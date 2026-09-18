package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

/**
 * Domicilio fiscal del emisor tal como va en {@code cac:AccountingSupplierParty/.../cac:RegistrationAddress}: ubigeo del
 * catálogo 13 (observación 4093 si no existe), dirección de 3 a 200 caracteres sin saltos de línea (4094), urbanización
 * opcional (4095), distrito/provincia/departamento de hasta 30 caracteres (4096–4098, se completan desde el ubigeo) y el
 * código del establecimiento anexo declarado en el RUC ({@code 0000} = domicilio fiscal; ERROR 3030 si falta).
 */
public record Domicilio(String ubigeo, String direccion, String urbanizacion, String distrito, String provincia, String departamento,
                        String codigoEstablecimiento) {

    public static final String ESTABLECIMIENTO_PRINCIPAL = "0000";
    public static final String PAIS = "PE";

    public Domicilio {
        if (ubigeo == null || !ubigeo.matches("\\d{6}") || !CatalogoSunat.porId("13").map(c -> c.contiene(ubigeo)).orElse(false))
            throw new DomainException("DOMICILIO_INVALIDO", "4093 - El ubigeo debe ser un código de 6 dígitos del catálogo 13 (INEI)");
        if (direccion == null || direccion.strip().length() < 3 || direccion.strip().length() > 200 || direccion.matches(".*[\\r\\n\\t].*"))
            throw new DomainException("DOMICILIO_INVALIDO", "4094 - La dirección debe tener de 3 a 200 caracteres en una sola línea");
        direccion = direccion.strip();
        urbanizacion = limpiar(urbanizacion, 25, "4095 - La urbanización admite hasta 25 caracteres");
        CatalogoSunat.Entrada ubi = CatalogoSunat.porId("13").flatMap(c -> c.entrada(ubigeo)).orElseThrow();
        distrito = limpiar(distrito == null ? ubi.extra().get("Distrito") : distrito, 30, "4098 - El distrito admite hasta 30 caracteres");
        provincia = limpiar(provincia == null ? ubi.extra().get("Provincia") : provincia, 30, "4096 - La provincia admite hasta 30 caracteres");
        departamento = limpiar(departamento == null ? ubi.extra().get("Departamento") : departamento, 30, "4097 - El departamento admite hasta 30 caracteres");
        codigoEstablecimiento = codigoEstablecimiento == null || codigoEstablecimiento.isBlank() ? ESTABLECIMIENTO_PRINCIPAL : codigoEstablecimiento.strip();
        if (!codigoEstablecimiento.matches("\\d{4}"))
            throw new DomainException("DOMICILIO_INVALIDO", "3030 - El código de establecimiento anexo son 4 dígitos (0000 para el domicilio fiscal)");
    }

    public static Domicilio de(String ubigeo, String direccion) { return new Domicilio(ubigeo, direccion, null, null, null, null, null); }

    private static String limpiar(String v, int max, String error) {
        if (v == null || v.isBlank()) return null;
        String s = v.strip();
        if (s.length() > max || s.matches(".*[\\r\\n\\t].*")) throw new DomainException("DOMICILIO_INVALIDO", error);
        return s;
    }
}
