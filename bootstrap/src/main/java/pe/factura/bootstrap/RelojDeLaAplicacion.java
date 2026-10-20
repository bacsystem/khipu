package pe.factura.bootstrap;

import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.application.port.in.EmitirAgenteCommand;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirLiquidacionCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.SunatApiValidezGateway;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatConsultaGateway;
import pe.factura.application.port.out.SunatGreGateway;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * El reloj del backend y su desplazamiento (#363). Los plazos de SUNAT dependen de la fecha (la boleta pasada del 7.º día se informa en un resumen fuera de plazo, que la norma prevé observar con la 4443; el
 * resumen diario con gratuitas cambia de formato el 2027-01-01) y no hay otra forma de probarlos en un ambiente de pruebas que mover el reloj. {@code RELOJ_DESPLAZAMIENTO}
 * (una {@link Duration} ISO-8601: {@code -P8D} atrasa ocho días, {@code PT6912345S} adelanta esos segundos) se suma al reloj del sistema; vacío, no hay desplazamiento
 * y el reloj es exactamente el de siempre.
 * <p>
 * Un reloj movido en producción emitiría comprobantes con fechas falsas y consumiría numeración real, así que la salvaguarda no depende de que alguien se acuerde de
 * quitar la variable. Lo que distingue producción de pruebas no es la URL de SUNAT (la configuración siempre lleva las dos) sino el {@link Entorno} de cada empresa, que
 * decide a cuál se envía ({@code SunatUrls.para}). Con el reloj movido: (1) la aplicación no arranca si ya hay empresas en PRODUCCION
 * ({@link #exigirSinEmpresasEnProduccion}); (2) si una empresa pasa a PRODUCCION después de arrancar, nada que use el reloj con ella avanza, y se rechaza con
 * {@value #CODIGO}: ni emite ({@link #proteger(EmitirComprobanteUseCase, TenantRepository, Duration)}), ni envía o cierra por plazo
 * ({@link #proteger(EnviarDocumentoUseCase, TenantRepository, Duration)}), ni numera y firma bajas o resúmenes ({@link #proteger(TenantRepository, Duration)}), ni corre el barrido
 * de plazos ({@link #proteger(ControlarPlazoEnvioUseCase, LongSupplier, Consumer, Duration)}); y ninguna llamada suya llega a SUNAT, ni de envío
 * ({@link #proteger(SunatBillingGateway, Duration)}) ni de consulta ({@link #proteger(SunatConsultaGateway, Duration)}), que son las últimas barreras; y (3) el arranque lo dice
 * con un WARN imposible de pasar por alto ({@link #alArrancar}). Lo único que sigue yendo a las URL de producción es el sondeo anónimo del monitor del backoffice.
 */
public final class RelojDeLaAplicacion {
    /** El código con que se rechaza emitir o enviar para una empresa de PRODUCCION mientras el reloj está movido. */
    public static final String CODIGO = "RELOJ_DESPLAZADO";
    static final String VARIABLE = "RELOJ_DESPLAZAMIENTO";
    /** Hasta dónde se puede mover el reloj, hacia atrás o hacia adelante: más allá no hay prueba de plazos que hacer, solo un valor mal escrito que rompería el reloj en su primer uso. */
    static final Duration MAXIMO = Duration.ofDays(3650);

    private RelojDeLaAplicacion() {}

    /** Vacío: sin desplazamiento. Un valor que no es una duración ISO-8601 impide arrancar, diciendo cuál: un reloj mal movido es peor que uno parado. */
    public static Duration desplazamiento(String configurado) {
        if (configurado == null || configurado.isBlank()) return Duration.ZERO;
        String valor = configurado.strip();
        Duration d;
        try {
            d = Duration.parse(valor);
        } catch (DateTimeException e) {
            throw new IllegalStateException(VARIABLE + " debe ser una duración ISO-8601 con días, horas, minutos o segundos (p. ej. -P8D o PT90M), no «" + valor + "»", e);
        }
        if (d.compareTo(MAXIMO) > 0 || d.compareTo(MAXIMO.negated()) < 0)
            throw new IllegalStateException(VARIABLE + " mueve el reloj más de " + MAXIMO.toDays() + " días (" + valor + "): debe quedar entre -P" + MAXIMO.toDays() + "D y P" + MAXIMO.toDays() + "D");
        return d;
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
                + "!!! RELOJ DESPLAZADO (" + VARIABLE + "=" + desplazamiento + ", " + legible(desplazamiento) + "): el backend cree que es " + reloj.instant().atZone(reloj.getZone()).toOffsetDateTime() + " (" + reloj.getZone() + ")\n"
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
            @Override public Emision emitirNota(UUID tenantId, EmitirNotaCommand cmd, Idempotencia idempotencia) {
                exigirNoProduccion(tenantId);
                return emision.emitirNota(tenantId, cmd, idempotencia);
            }
            @Override public Emision emitirLiquidacionCompra(UUID tenantId, EmitirLiquidacionCommand cmd, Idempotencia idempotencia) {
                exigirNoProduccion(tenantId);
                return emision.emitirLiquidacionCompra(tenantId, cmd, idempotencia);
            }
            @Override public Emision emitirDeAgente(UUID tenantId, EmitirAgenteCommand cmd, Idempotencia idempotencia) {
                exigirNoProduccion(tenantId);
                return emision.emitirDeAgente(tenantId, cmd, idempotencia);
            }
            /** Calcular no emite nada ni toca a SUNAT: se deja pasar aunque la empresa esté en producción. */
            @Override public Comprobante calcularFactura(UUID tenantId, EmitirFacturaCommand cmd) { return emision.calcularFactura(tenantId, cmd); }
            @Override public Comprobante calcularLiquidacionCompra(UUID tenantId, EmitirLiquidacionCommand cmd) { return emision.calcularLiquidacionCompra(tenantId, cmd); }
            @Override public Comprobante calcularDeAgente(UUID tenantId, EmitirAgenteCommand cmd) { return emision.calcularDeAgente(tenantId, cmd); }
            private void exigirNoProduccion(UUID tenantId) { RelojDeLaAplicacion.exigirNoProduccion(tenants, tenantId, "emitir"); }
        };
    }

    /** Sin desplazamiento, la misma pasarela. Con él, nada de una empresa en PRODUCCION llega a la SUNAT de producción. */
    public static SunatBillingGateway proteger(SunatBillingGateway sunat, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return sunat;
        return new SunatBillingGateway() {
            @Override public byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) { exigirNoProduccion(tenant); return sunat.sendBill(tenant, nombreArchivo, xmlFirmado); }
            @Override public String sendSummary(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) { exigirNoProduccion(tenant); return sunat.sendSummary(tenant, nombreArchivo, xmlFirmado); }
            @Override public EstadoTicket getStatus(Tenant tenant, String ticket) { exigirNoProduccion(tenant); return sunat.getStatus(tenant, ticket); }
            @Override public EstadoTicket getStatus(Tenant tenant, String nombreArchivo, String ticket) { exigirNoProduccion(tenant); return sunat.getStatus(tenant, nombreArchivo, ticket); }
            private void exigirNoProduccion(Tenant tenant) { if (tenant.entorno() == Entorno.PRODUCCION) throw rechazo("enviar a SUNAT"); }
        };
    }

    /**
     * Sin desplazamiento, el mismo envío. Con él, ni envía ni cierra por plazo lo de una empresa en PRODUCCION: el envío compara la fecha de emisión con el reloj
     * y cierra como FUERA_DE_PLAZO (estado terminal) antes de llegar a la pasarela, y los resúmenes diarios de las boletas viejas se numeran y firman en el camino.
     */
    public static EnviarDocumentoUseCase proteger(EnviarDocumentoUseCase envio, TenantRepository tenants, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return envio;
        return (tenantId, comprobanteId) -> {
            exigirNoProduccion(tenants, tenantId, "enviar");
            return envio.enviar(tenantId, comprobanteId);
        };
    }

    /**
     * Sin desplazamiento, el mismo repositorio. Con él, cargar una empresa de PRODUCCION falla: la comunicación de baja y el resumen diario cargan la empresa antes de
     * numerar, firmar y guardar con la fecha del reloj, así que no empiezan. (Solo lo recibe quien numera con el reloj; el resto de la aplicación ve las empresas tal cual.)
     */
    public static TenantRepository proteger(TenantRepository tenants, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return tenants;
        return new TenantRepository() {
            @Override public Optional<Tenant> buscar(UUID id) {
                Optional<Tenant> empresa = tenants.buscar(id);
                if (empresa.filter(t -> t.entorno() == Entorno.PRODUCCION).isPresent()) throw rechazo("emitir ni dar de baja");
                return empresa;
            }
            @Override public void guardar(Tenant t) { tenants.guardar(t); }
            @Override public Optional<Tenant> buscarPorRuc(String ruc) { return tenants.buscarPorRuc(ruc); }
            @Override public List<Tenant> listarPorCuenta(UUID cuentaId) { return tenants.listarPorCuenta(cuentaId); }
            @Override public void asignarCuenta(UUID tenantId, UUID cuentaId) { tenants.asignarCuenta(tenantId, cuentaId); }
            @Override public Optional<UUID> cuentaDe(UUID tenantId) { return tenants.cuentaDe(tenantId); }
        };
    }

    /**
     * Sin desplazamiento, el mismo barrido. Con él, el barrido (que cierra por plazo lo de TODAS las empresas con el reloj, en un estado terminal) no corre mientras haya
     * una empresa en PRODUCCION, y lo dice. En una base de pruebas, sin ellas, corre normal: es parte de lo que se quiere probar.
     */
    public static ControlarPlazoEnvioUseCase proteger(ControlarPlazoEnvioUseCase plazos, LongSupplier empresasEnProduccion, Consumer<String> warn, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return plazos;
        return () -> {
            long enProduccion = empresasEnProduccion.getAsLong();
            if (enProduccion == 0) return plazos.marcarVencidos();
            warn.accept(VARIABLE + "=" + desplazamiento + " mueve el reloj y hay " + enProduccion + " empresa(s) en PRODUCCION: el barrido de plazos no corre para no cerrar sus "
                    + "comprobantes con una fecha falsa. Quita la variable y reinicia");
            return List.of();
        };
    }

    /** Sin desplazamiento, la misma pasarela. Con él, ni una consulta de una empresa en PRODUCCION llega a los servicios de consulta de SUNAT (que solo existen en producción). */
    public static SunatConsultaGateway proteger(SunatConsultaGateway sunat, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return sunat;
        return new SunatConsultaGateway() {
            @Override public Consulta getStatusCdr(Tenant t, String rucEmisor, String tipo, String serie, long numero) { exigirNoProduccion(t); return sunat.getStatusCdr(t, rucEmisor, tipo, serie, numero); }
            @Override public Consulta getStatus(Tenant t, String rucEmisor, String tipo, String serie, long numero) { exigirNoProduccion(t); return sunat.getStatus(t, rucEmisor, tipo, serie, numero); }
            @Override public Consulta validar(Tenant t, String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, LocalDate fechaEmision, BigDecimal importeTotal) {
                exigirNoProduccion(t);
                return sunat.validar(t, rucEmisor, tipo, serie, numero, tipoDocReceptor, numDocReceptor, fechaEmision, importeTotal);
            }
            private void exigirNoProduccion(Tenant t) { if (t.entorno() == Entorno.PRODUCCION) throw rechazo("consultar a SUNAT"); }
        };
    }

    /** Lo mismo para la consulta de validez por la API REST de SUNAT (#318). */
    public static SunatApiValidezGateway proteger(SunatApiValidezGateway sunat, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return sunat;
        return (t, rucEmisor, tipo, serie, numero, fechaEmision, importeTotal) -> {
            if (t.entorno() == Entorno.PRODUCCION) throw rechazo("consultar a SUNAT");
            return sunat.validar(t, rucEmisor, tipo, serie, numero, fechaEmision, importeTotal);
        };
    }

    /** Lo mismo para la guía de remisión por la API REST de SUNAT (#39): con el reloj movido, ninguna guía de una empresa en PRODUCCION llega a SUNAT. */
    public static SunatGreGateway proteger(SunatGreGateway sunat, Duration desplazamiento) {
        if (!desplazado(desplazamiento)) return sunat;
        return new SunatGreGateway() {
            @Override public boolean disponible(Entorno entorno) { return sunat.disponible(entorno); }
            @Override public Recepcion enviar(Tenant t, String nombreArchivo, byte[] xmlFirmado) { exigirNoProduccion(t); return sunat.enviar(t, nombreArchivo, xmlFirmado); }
            @Override public EstadoDelTicket consultar(Tenant t, String ticket) { exigirNoProduccion(t); return sunat.consultar(t, ticket); }
            private void exigirNoProduccion(Tenant t) { if (t.entorno() == Entorno.PRODUCCION) throw rechazo("enviar a SUNAT"); }
        };
    }

    private static void exigirNoProduccion(TenantRepository tenants, UUID tenantId, String accion) {
        if (tenants.buscar(tenantId).filter(t -> t.entorno() == Entorno.PRODUCCION).isPresent()) throw rechazo(accion);
    }

    private static DomainException rechazo(String accion) {
        return new DomainException(CODIGO, "No se puede " + accion + " para una empresa en PRODUCCION: el reloj del backend está desplazado (" + VARIABLE
                + ") y el comprobante saldría con una fecha falsa. Quita la variable y reinicia, o usa una empresa en BETA");
    }
}
