package pe.factura.application.service;

import pe.factura.application.port.out.RechazoDeCredencialesGreRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * La marca de credenciales de la GRE rechazadas por SUNAT (#39, H3), como la de la clave SOL de las facturas (#107): mientras valga no se pide otro token. Vale
 * mientras las credenciales de la empresa sean las rechazadas (su huella) y no haya pasado {@link #PAUSA}: cambiarlas la levanta sola, y un rechazo pasajero del
 * servicio de token se vuelve a probar a la hora.
 */
final class CredencialesDeGre {
    static final Duration PAUSA = Duration.ofHours(1);

    private CredencialesDeGre() {}

    /** SHA-256 de las credenciales API SUNAT y SOL de la empresa, sin guardarlas: identifica cuáles rechazó SUNAT. */
    static String huella(Tenant t) {
        String api = t.apiSunat() == null ? "" : t.apiSunat().clientId() + "\u0000" + t.apiSunat().clientSecret();
        String sol = t.sol() == null ? "" : t.sol().usuario() + "\u0000" + t.sol().clave();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((t.ruc() + "\u0000" + api + "\u0001" + sol).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** 422 si las credenciales actuales de la empresa son las que SUNAT rechazó hace menos de una hora: no se pide token. */
    static void exigirNoRechazadas(RechazoDeCredencialesGreRepository rechazos, Tenant t, Clock clock) {
        rechazos.buscar(t.id())
                .filter(r -> r.huella().equals(huella(t)) && r.en().isAfter(clock.instant().minus(PAUSA)))
                .ifPresent(r -> {
                    throw new DomainException("CREDENCIALES_GRE_RECHAZADAS", "SUNAT rechazó las credenciales API SUNAT o la clave SOL de la empresa para la guía de remisión (" + r.motivo()
                            + "): corríjalas en la empresa; khipu no vuelve a pedir el token con las mismas para no bloquear la clave SOL (se reintenta pasada una hora)");
                });
    }
}
