package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;
import pe.factura.domain.tenant.Ruc;

/**
 * Punto de partida o de llegada de la guía de remisión: ubigeo del catálogo 13, dirección detallada y, si es un establecimiento declarado en el RUC de
 * alguien, su código de 4 dígitos ({@code 0000} el domicilio fiscal) y el RUC al que pertenece (regla 3410), que va en el {@code listID} del
 * {@code cbc:AddressTypeCode}. Los establecimientos propios del emisor se pueden indicar solo con el código: el servicio completa el ubigeo y la dirección
 * desde los establecimientos anexos de la empresa (#80).
 */
public record PuntoDeTraslado(String ubigeo, String direccion, String codigoEstablecimiento, String rucEstablecimiento) {
    public PuntoDeTraslado {
        ubigeo = TextoDeGuia.limpio(ubigeo);
        direccion = TextoDeGuia.limpio(direccion);
        codigoEstablecimiento = TextoDeGuia.limpio(codigoEstablecimiento);
        rucEstablecimiento = TextoDeGuia.limpio(rucEstablecimiento);
    }

    public static PuntoDeTraslado de(String ubigeo, String direccion) { return new PuntoDeTraslado(ubigeo, direccion, null, null); }

    public boolean esEstablecimiento() { return codigoEstablecimiento != null; }

    /** El mismo punto con el RUC del establecimiento, si trae código y no trae RUC: el de quien lo declara por defecto (el emisor o el remitente). */
    PuntoDeTraslado conRucPorDefecto(String ruc) {
        return esEstablecimiento() && rucEstablecimiento == null && ruc != null ? new PuntoDeTraslado(ubigeo, direccion, codigoEstablecimiento, ruc) : this;
    }

    /** {@code partida}: reglas del punto de partida (3363, 2577, 4184, 3365); si no, las del de llegada (3368, 2574, 4179, 3369). */
    void exigirValido(boolean partida) {
        String cual = partida ? "punto de partida" : "punto de llegada";
        if (ubigeo == null || !ubigeo.matches("\\d{6}") || !CatalogoSunat.porId("13").map(c -> c.contiene(ubigeo)).orElse(false))
            throw new DomainException("TRASLADO_INVALIDO", (partida ? "3363" : "3368") + " - El ubigeo del " + cual + " es un código de 6 dígitos del catálogo 13 (INEI); recibido " + ubigeo);
        if (direccion == null)
            throw new DomainException("TRASLADO_INVALIDO", (partida ? "2577" : "2574") + " - Falta la dirección detallada del " + cual);
        if (!TextoDeGuia.unaLinea(direccion, 3, 500))
            throw new DomainException("TRASLADO_INVALIDO", (partida ? "4184" : "4179") + " - La dirección del " + cual + " tiene de 3 a 500 caracteres, sin saltos de línea");
        if (codigoEstablecimiento != null && !codigoEstablecimiento.matches("\\d{4}"))
            throw new DomainException("TRASLADO_INVALIDO", (partida ? "3365" : "3369") + " - El código de establecimiento del " + cual + " son 4 dígitos (0000 el domicilio fiscal); recibido " + codigoEstablecimiento);
        if (codigoEstablecimiento != null) {
            if (rucEstablecimiento == null) throw new DomainException("TRASLADO_INVALIDO", "3410 - Indique el RUC al que pertenece el establecimiento " + codigoEstablecimiento + " del " + cual);
            Ruc.exigirValido(rucEstablecimiento, "TRASLADO_INVALIDO", "3410 - El RUC del establecimiento del " + cual);
        } else if (rucEstablecimiento != null) {
            throw new DomainException("TRASLADO_INVALIDO", "El RUC del establecimiento del " + cual + " va con su código de establecimiento");
        }
    }
}
