package pe.factura.bootstrap;

import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * El reloj del backend y su desplazamiento (#363). Los plazos de SUNAT dependen de la fecha (la boleta pasada del 7.º día se informa con la observación 4443; el
 * resumen diario con gratuitas cambia de formato el 2027-01-01) y no hay otra forma de probarlos en un ambiente de pruebas que mover el reloj. {@code RELOJ_DESPLAZAMIENTO}
 * (una {@link Duration} ISO-8601: {@code -P8D} atrasa ocho días, {@code PT6912345S} adelanta esos segundos) se suma al reloj del sistema; vacío, no hay desplazamiento
 * y el reloj es exactamente el de siempre.
 * <p>
 * Un reloj movido en producción emitiría comprobantes con fechas falsas y consumiría numeración real, así que la salvaguarda no depende de que alguien se acuerde de
 * quitar la variable. Lo que distingue producción de pruebas no es la URL de SUNAT (la configuración siempre lleva las dos) sino el {@link Entorno} de cada empresa, que
 * decide a cuál se envía ({@code SunatUrls.para}). Con el reloj movido: (1) la aplicación no arranca si ya hay empresas en PRODUCCION
 * ({@link #exigirSinEmpresasEnProduccion}); (2) si una empresa pasa a PRODUCCION después de arrancar, ni emite ({@link #proteger(EmitirComprobanteUseCase, TenantRepository, Duration)})
 * ni se envía nada suyo a SUNAT ({@link #proteger(SunatBillingGateway, Duration)}); y (3) el arranque lo dice con un WARN imposible de pasar por alto
 * ({@link #alArrancar}).
 */
public final class RelojDeLaAplicacion {
    /** El código con que se rechaza emitir o enviar para una empresa de PRODUCCION mientras el reloj está movido. */
    public static final String CODIGO = "RELOJ_DESPLAZADO";
    static final String VARIABLE = "RELOJ_DESPLAZAMIENTO";

    private RelojDeLaAplicacion() {}

    /** Vacío: sin desplazamiento. Un valor que no es una duración ISO-8601 impide arrancar, diciendo cuál: un reloj mal movido es peor que uno parado. */
    public static Duration desplazamiento(String configurado) {
        if (configurado == null || configurado.isBlank()) return Duration.ZERO;
        String valor = configurado.strip();
        try {
            return Duration.parse(valor);
        } catch (DateTimeException e) {
            throw new IllegalStateException(VARIABLE + " debe ser una duración ISO-8601 con días, horas, minutos o segundos (p. ej. -P8D o PT90M), no «" + valor + "»", e);
        }
    }

    /** Sin desplazamiento, el reloj del sistema tal cual (el mismo que antes de #363); con él, ese reloj corrido, que sigue andando. */
    public static Clock crear(ZoneId zona, Duration desplazamiento) {
        Clock sistema = Clock.system(zona);
        return desplazado(desplazamiento) ? Clock.offset(sistema, desplazamiento) : sistema;
    }

    /** «-8 d 0 h 0 min 0 s»: {@link Duration#toString} da {@code PT-192H}, que es válido pero no se lee de un vistazo en un log. */
    static String legible(Duration d) {
        Duration abs = d.abs();
        return (d.isNegative() ? "-" : "+") + abs.toDays() + " d " + abs.toHoursPart() + " h " + abs.toMinutesPart() + " min " + abs.toSecondsPart() + " s";
    }

    public static boolean desplazado(Duration desplazamiento) { return !desplazamiento.isZero(); }

    /** El arranque se aborta: con el reloj movido, una empresa en PRODUCCION es una que emitiría y enviaría con una fecha falsa. */
    public static void exigirSinEmpresasEnProduccion(Duration desplazamiento, long empresasEnProduccion) {
        if (desplazado(desplazamiento) && empresasEnProduccion > 0)
            throw new IllegalStateException(VARIABLE + "=" + desplazamiento + " mueve el reloj, pero hay " + empresasEnProduccion + " empresa(s) en PRODUCCION: emitirían y enviarían "
                    + "a SUNAT con una fecha falsa. El desplazamiento es solo para el ambiente de pruebas; quita la variable (o pasa esas empresas a BETA) y vuelve a arrancar");
    }

    /**
     * Lo que se hace una vez, al arrancar: sin desplazamiento, nada (ni se consulta la base); con él, se comprueba que no haya empresas en PRODUCCION y se deja el
     * WARN. {@code empresasEnProduccion} es perezoso a propósito: el reloj normal no debe tocar la base solo por esta comprobación.
     */
    public static void alArrancar(Duration desplazamiento, Clock reloj, LongSupplier empresasEnProduccion, Consumer<String> warn) {
        if (!desplazado(desplazamiento)) return;
        exigirSinEmpresasEnProduccion(desplazamiento, empresasEnProduccion.getAsLong());
        warn.accept(avisoAlArrancar(desplazamiento, reloj));
    }

    /** Las líneas del WARN de arranque: la hora que el backend cree que es y qué hacer. */
    static String avisoAlArrancar(Duration desplazamiento, Clock reloj) {
        return "\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
                + "!!! RELOJ DESPLAZADO (" + VARIABLE + "=" + desplazamiento + ", " + legible(desplazamiento) + "): el backend cree que es " + reloj.instant() + " (" + reloj.getZone() + ")\n"
                + "!!! Los comprobantes salen con esa fecha. SOLO PARA PRUEBAS: nunca en producción.\n"
                + "!!! Para volver a la hora real, quita " + VARIABLE + " y reinicia.\n"
                + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!";
    }

    /** Sin desplazamiento, la misma emisión. Con él, no emite para una empresa en PRODUCCION (que alguien pasó a PRODUCCION con la aplicación ya arrancada). */
    public static EmitirComprobanteUseCase proteger(EmitirComprobanteUseCase emision, TenantRepository tenants, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return emision;
        return new EmitirComprobanteUseCase() {
            @Override public Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia idempotencia) {
                exigirNoProduccion(tenantId);
                return emision.emitirFactura(tenantId, cmd, idempotencia);
            }
            @Override public Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd) {
                exigirNoProduccion(tenantId);
                return emision.emitirNota(tenantId, cmd);
            }
            private void exigirNoProduccion(UUID tenantId) {
                if (tenants.buscar(tenantId).filter(t -> t.entorno() == Entorno.PRODUCCION).isPresent()) throw rechazo("emitir");
            }
        };
    }

    /** Sin desplazamiento, la misma pasarela. Con él, nada de una empresa en PRODUCCION llega a la SUNAT de producción. */
    public static SunatBillingGateway proteger(SunatBillingGateway sunat, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return sunat;
        return new SunatBillingGateway() {
            @Override public byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) { exigirNoProduccion(tenant); return sunat.sendBill(tenant, nombreArchivo, xmlFirmado); }
            @Override public String sendSummary(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) { exigirNoProduccion(tenant); return sunat.sendSummary(tenant, nombreArchivo, xmlFirmado); }
            @Override public EstadoTicket getStatus(Tenant tenant, String ticket) { exigirNoProduccion(tenant); return sunat.getStatus(tenant, ticket); }
            private void exigirNoProduccion(Tenant tenant) { if (tenant.entorno() == Entorno.PRODUCCION) throw rechazo("enviar a SUNAT"); }
        };
    }

    private static DomainException rechazo(String accion) {
        return new DomainException(CODIGO, "No se puede " + accion + " para una empresa en PRODUCCION: el reloj del backend está desplazado (" + VARIABLE
                + ") y el comprobante saldría con una fecha falsa. Quita la variable y reinicia, o usa una empresa en BETA");
    }
}
