package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.MonitorearEmisionUseCase;
import pe.factura.application.port.in.VigilarOperacionUseCase;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.CanalDeAlertas;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.MonitorDeEmisionRepository;
import pe.factura.application.port.out.VigiladoRepository;
import pe.factura.application.port.out.VigiladoRepository.EstadoAlerta;
import pe.factura.domain.plan.CicloMensual;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * El vigilante de la operación (#341). En cada pasada evalúa los problemas que hoy fallan en silencio y, por cada uno, avisa **una vez** cuando aparece, lo repite cada
 * {@link Umbrales#repetirCada()} mientras siga y avisa cuando se resuelve. Lo que se avisó queda en {@code alerta_operador}; si ningún canal pudo entregar el aviso, no se
 * registra y la siguiente pasada lo reintenta. Todas las alertas salen además por el log (nivel ERROR), de modo que sin canal configurado Railway las muestra igual.
 */
@Slf4j
@RequiredArgsConstructor
public class VigilarOperacionService implements VigilarOperacionUseCase {
    /** La clave con la que {@code deploy/respaldo/respaldar.sh} anota su último respaldo bueno en {@code latido_operativo}. */
    public static final String LATIDO_RESPALDO = "respaldo";

    /**
     * {@code repetirCada}: cada cuánto se vuelve a avisar de un problema que sigue. {@code errorDeEnvioDesde} y {@code errorDeEnvioCantidad}: cuántos comprobantes llevan al menos
     * ese tiempo en ERROR_ENVIO para alertar. {@code resumenesSinRespuesta}: lo que puede tardar SUNAT en contestar un ticket. {@code respaldoMaximo}: lo más que puede pasar
     * sin un respaldo bueno; cero apaga la alerta (el respaldo es un cron aparte que no todo entorno tiene). {@code fueraDePlazoVentana}: cuánto atrás se cuentan los
     * comprobantes que pasaron a FUERA_DE_PLAZO.
     */
    public record Umbrales(Duration repetirCada, Duration errorDeEnvioDesde, long errorDeEnvioCantidad, Duration resumenesSinRespuesta, Duration respaldoMaximo,
                           Duration fueraDePlazoVentana) {}

    private record Problema(String clave, boolean activo, String asunto, String detalle, String resuelto) {
        Problema(String clave, boolean activo, String asunto, String detalle) { this(clave, activo, asunto, detalle, null); }
    }

    private record Chequeo(String clave, Supplier<Problema> evaluar) {}

    private static final DateTimeFormatter FECHA_LIMA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CicloMensual.ZONA);

    private final MonitorDeEmisionRepository monitor;
    private final AvisosRepository avisos;
    private final VigiladoRepository vigilado;
    private final CorreoSender correo;
    private final List<CanalDeAlertas> canales;
    private final Clock clock;
    private final Umbrales umbrales;

    @Override public Resultado vigilar() {
        // Los microsegundos son lo que guarda Postgres: un instante con más precisión no volvería a ser igual al leerlo, y la reserva compara por igualdad.
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Map<String, EstadoAlerta> registradas = vigilado.alertas();
        int avisadas = 0, resueltas = 0;
        List<String> fallos = new ArrayList<>();
        for (Chequeo chequeo : chequeos(ahora)) {
            Problema p;
            try {
                p = chequeo.evaluar().get();
            } catch (RuntimeException e) {
                // Un chequeo que no se puede evaluar (una consulta que falla) no calla a los demás.
                log.error("El vigilante no pudo evaluar {}", chequeo.clave(), e);
                fallos.add(chequeo.clave());
                continue;
            }
            EstadoAlerta previa = registradas.get(p.clave());
            boolean estabaActiva = previa != null && previa.activa();
            try {
                if (p.activo()) {
                    if (estabaActiva && previa.ultimoEnvio().plus(umbrales.repetirCada()).isAfter(ahora)) continue;
                    EstadoAlerta nueva = new EstadoAlerta(true, estabaActiva ? previa.desde() : ahora, ahora);
                    String seguia = estabaActiva ? "\n\nSigue sin resolverse desde " + lima(previa.desde()) + "." : "";
                    if (enviar(p, previa, nueva, "ALERTA · " + p.asunto(), p.detalle() + seguia, fallos)) avisadas++;
                } else if (estabaActiva) {
                    EstadoAlerta nueva = new EstadoAlerta(false, previa.desde(), ahora);
                    String cuerpo = p.resuelto() != null ? p.resuelto() : "Dejó de cumplirse la condición que había disparado la alerta (activa desde " + lima(previa.desde()) + ").";
                    if (enviar(p, previa, nueva, "RESUELTO · " + p.asunto(), cuerpo, fallos)) resueltas++;
                }
            } catch (RuntimeException e) {
                log.error("El vigilante no pudo registrar la alerta {}", p.clave(), e);
                fallos.add(p.clave());
            }
        }
        return new Resultado(avisadas, resueltas, List.copyOf(fallos));
    }

    /**
     * Reserva el cambio de estado de la alerta (solo una instancia del backend lo logra con el mismo estado previo), avisa y, si ningún canal entregó, devuelve la alerta a como
     * estaba para que la siguiente pasada lo reintente. Si el proceso muere entre reservar y avisar, el aviso se pierde hasta el siguiente intervalo de repetición.
     */
    private boolean enviar(Problema p, EstadoAlerta previa, EstadoAlerta nueva, String asunto, String cuerpo, List<String> fallos) {
        if (!vigilado.reclamarAlerta(p.clave(), previa, nueva)) return false;
        if (avisar(asunto, cuerpo)) return true;
        vigilado.restaurarAlerta(p.clave(), nueva, previa);
        fallos.add(p.clave());
        return false;
    }

    private List<Chequeo> chequeos(Instant ahora) {
        return List.of(
                new Chequeo("OUTBOX_ATASCADO", () -> outbox(ahora)),
                new Chequeo("ENVIOS_CON_ERROR", () -> erroresDeEnvio(ahora)),
                new Chequeo("FUERA_DE_PLAZO", () -> fueraDePlazo(ahora)),
                new Chequeo("RESUMENES_SIN_RESPUESTA", () -> resumenes(ahora)),
                new Chequeo("BOLETAS_POR_VENCER", this::boletas),
                new Chequeo("RESPALDO_ATRASADO", () -> respaldo(ahora)),
                new Chequeo("CORREO_DESHABILITADO", this::correo),
                new Chequeo("CERTIFICADOS_EN_RIESGO", this::certificados),
                new Chequeo("CREDENCIALES_SOL", this::credencialesSol));
    }

    private Problema outbox(Instant ahora) {
        MonitorDeEmisionRepository.Cola cola = monitor.cola(ahora);
        Duration espera = cola.vencidoDesde() == null ? Duration.ZERO : Duration.between(cola.vencidoDesde(), ahora);
        boolean activo = cola.vencidoDesde() != null && espera.compareTo(MonitorearEmisionUseCase.UMBRAL_DE_ALERTA) > 0;
        return new Problema("OUTBOX_ATASCADO", activo, "El outbox de envíos a SUNAT no avanza",
                cola.vencidos() + " envío(s) vencido(s) sin que nadie los tome, el más viejo hace " + espera.toMinutes() + " min (" + cola.pendientes() + " pendientes en total). "
                        + "El trabajo que vacía la cola probablemente no corre: revise el servicio y el log del backend.");
    }

    private Problema erroresDeEnvio(Instant ahora) {
        long n = vigilado.enErrorDeEnvioDesde(ahora.minus(umbrales.errorDeEnvioDesde()));
        return new Problema("ENVIOS_CON_ERROR", n >= umbrales.errorDeEnvioCantidad(), "Comprobantes que no logran enviarse a SUNAT",
                n + " comprobante(s) llevan más de " + umbrales.errorDeEnvioDesde().toMinutes() + " min sin poder enviarse (ERROR_ENVIO). Suele ser SUNAT caída o credenciales SOL vencidas; "
                        + "el detalle por empresa está en el backoffice (cola de errores).");
    }

    private Problema fueraDePlazo(Instant ahora) {
        long n = vigilado.fueraDePlazoDesde(ahora.minus(umbrales.fueraDePlazoVentana()));
        return new Problema("FUERA_DE_PLAZO", n > 0, "Comprobantes que pasaron a FUERA_DE_PLAZO",
                n + " comprobante(s) pasaron a FUERA_DE_PLAZO en las últimas " + umbrales.fueraDePlazoVentana().toHours() + " h: SUNAT ya no los recibe y el cliente tiene que volver a emitir.",
                "No hubo más comprobantes que pasaran a FUERA_DE_PLAZO en las últimas " + umbrales.fueraDePlazoVentana().toHours() + " h (los anteriores siguen en ese estado; están en la cola de errores del backoffice).");
    }

    private Problema resumenes(Instant ahora) {
        MonitorDeEmisionRepository.ResumenesPendientes r = monitor.resumenesSinRespuesta(ahora.minus(umbrales.resumenesSinRespuesta()));
        return new Problema("RESUMENES_SIN_RESPUESTA", r.cantidad() > 0, "Resúmenes diarios o bajas sin respuesta de SUNAT",
                r.cantidad() + " resumen(es) diario(s) o comunicación(es) de baja con ticket llevan más de " + umbrales.resumenesSinRespuesta().toHours() + " h sin respuesta; el más viejo, desde "
                        + lima(r.masViejoDesde()) + ".");
    }

    private Problema boletas() {
        MonitorDeEmisionRepository.BoletasPendientes b = monitor.boletasSinInformar(hoy());
        return new Problema("BOLETAS_POR_VENCER", b.desdeElSeptimoDia() > 0, "Boletas a punto de perder el plazo del resumen diario",
                b.desdeElSeptimoDia() + " boleta(s) están en su 7.º día o lo pasaron sin informarse a SUNAT (" + b.desdeElQuintoDia() + " desde el 5.º); la más antigua se emitió el "
                        + b.masAntiguaEmitidaEl() + ".");
    }

    private Problema respaldo(Instant ahora) {
        Duration maximo = umbrales.respaldoMaximo();
        if (maximo.isZero()) return new Problema("RESPALDO_ATRASADO", false, "El respaldo diario de Postgres no corrió", "");
        Optional<Instant> ultimo = vigilado.ultimoLatido(LATIDO_RESPALDO);
        boolean activo = ultimo.isEmpty() || Duration.between(ultimo.get(), ahora).compareTo(maximo) > 0;
        String cuando = ultimo.map(u -> "El último respaldo bueno fue el " + lima(u) + ", hace más de " + maximo.toHours() + " h.").orElse("Todavía no hay ningún respaldo bueno registrado.");
        return new Problema("RESPALDO_ATRASADO", activo, "El respaldo diario de Postgres no corrió", cuando + " Revise el cron de deploy/respaldo en Railway.");
    }

    private Problema correo() {
        return new Problema("CORREO_DESHABILITADO", !correo.entregaDeVerdad(), "El correo de la plataforma no entrega",
                "MAIL_HABILITADO=false o sin SMTP: los correos de recuperación de contraseña, avisos a clientes y comprobantes se escriben en el log y no salen. Vea deploy/README.md.");
    }

    private Problema certificados() {
        long n = avisos.contarCertificados(hoy());
        return new Problema("CERTIFICADOS_EN_RIESGO", n > 0, "Certificados digitales vencidos o por vencer",
                n + " empresa(s) tienen el certificado vencido o por vencer. El portal ya se lo avisa al cliente; la lista está en el backoffice (avisos).");
    }

    private Problema credencialesSol() {
        long n = avisos.contarCredencialesSol();
        return new Problema("CREDENCIALES_SOL", n > 0, "Empresas con credenciales SOL rechazadas por SUNAT",
                n + " empresa(s) tienen envíos fallando porque SUNAT rechaza su usuario SOL; el cliente tiene que actualizarlo.");
    }

    private static String lima(Instant i) { return i == null ? "—" : FECHA_LIMA.format(i) + " (Lima)"; }

    private LocalDate hoy() { return LocalDate.now(clock.withZone(CicloMensual.ZONA)); }

    /** {@code true} si el aviso quedó entregado: salió por algún canal externo o no hay ninguno configurado (queda en el log). */
    private boolean avisar(String asunto, String cuerpo) {
        log.error("{}: {}", asunto, cuerpo);
        List<CanalDeAlertas> usables = canales.stream().filter(CanalDeAlertas::configurado).toList();
        if (usables.isEmpty()) return true;
        boolean entregada = false;
        for (CanalDeAlertas canal : usables) {
            try {
                canal.enviar(asunto, cuerpo);
                entregada = true;
            } catch (RuntimeException e) {
                log.error("El canal de alertas {} no pudo entregar «{}»", canal.nombre(), asunto, e);
            }
        }
        return entregada;
    }
}
