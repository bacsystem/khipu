package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.EnviarAlClienteUseCase;
import pe.factura.application.port.out.BajaRepository;
import pe.factura.application.port.out.CorreoNoEnviadoException;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.EnvioAlClienteRepository;
import pe.factura.application.port.out.EnvioAlClienteRepository.Tomado;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EnvioAlCliente;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El envío automático del comprobante aceptado a su cliente (#325). El envío lo anota el repositorio de comprobantes en la misma transacción en que el comprobante pasa a aceptado
 * (outbox transaccional, uno por comprobante); aquí se manda, fuera de cualquier transacción, y se anota el resultado.
 *
 * <p><b>Una sola vez.</b> Cada pasada toma un lote con bloqueo ({@link #BLOQUEO}) y, justo antes de mandar cada envío, renueva su bloqueo con la marca con que lo tomó
 * ({@link EnvioAlClienteRepository#renovar}): si mientras atendía a los anteriores el bloqueo venció y otra réplica lo retomó, la marca ya no coincide y no se manda. El resultado
 * también se anota solo con la marca vigente. Lo único que no se puede evitar es que el servidor de correo reciba el mensaje y la base caiga antes de anotarlo: pasado el bloqueo
 * se manda de nuevo. Es preferible a perderlo, y SMTP no ofrece cómo saber si un mensaje ya se entregó.
 *
 * <p><b>La cuenta del bloqueo.</b> Cada envío tarda como mucho lo que los tiempos del SMTP ({@code MAIL_TIMEOUT_MS}, 10 s por defecto, para conectar, leer y escribir: ~30 s) más
 * armar los adjuntos. Un lote de {@link #LOTE} queda en ~2,5 min en el peor caso, la mitad del bloqueo inicial; y como cada envío renueva el suyo justo antes de salir, un SMTP más
 * lento que eso solo hace que otra réplica atienda lo que quedó, nunca que se mande dos veces.
 *
 * <p><b>Cuándo no se manda</b> (queda {@link EnvioAlCliente.Estado#OMITIDO} con el motivo, sin reintentos): el servidor no tiene correo configurado ({@link CorreoSender#entregaDeVerdad()}
 * falso: si se dejara esperando, al configurarse saldrían de golpe comprobantes de días atrás que el emisor quizá ya mandó a mano), la empresa desactivó el envío automático, el
 * comprobante ya no está aceptado o tiene una baja en curso (si SUNAT la acepta, el cliente habría recibido un comprobante anulado; si la rechaza, se puede enviar a mano).
 *
 * <p><b>Lo que ve el cliente.</b> {@code ultimo_error} guarda la clase de fallo ({@link CorreoNoEnviadoException.Motivo}), nunca el mensaje crudo de la excepción: llevaría el
 * host del SMTP de la plataforma o la clave del storage. El detalle queda en el log.
 */
@Slf4j
@RequiredArgsConstructor
public class EnviarAlClienteService implements EnviarAlClienteUseCase {
    static final int LOTE = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(5);
    static final String SIN_SMTP = "El servidor no tiene correo configurado: no se envió. Descargue el PDF y envíelo por su cuenta.";
    static final String DESACTIVADO = "La empresa desactivó el envío automático: no se envió.";
    static final String BAJA_EN_CURSO = "El comprobante tiene una baja en curso: no se envió. Si SUNAT la rechaza, envíelo a mano.";
    static final String SIN_ADJUNTOS = "No se pudieron leer los archivos del comprobante para adjuntarlos";

    private final EnvioAlClienteRepository envios;
    private final ConsultarComprobanteUseCase consultar;
    private final TenantRepository tenants;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final Clock clock;
    private final BajaRepository bajas;

    @Override public Preferencia preferencia(UUID tenantId) {
        return new Preferencia(envios.automatico(tenantId), correo.entregaDeVerdad());
    }

    @Override public Preferencia fijarPreferencia(UUID tenantId, boolean automatico) {
        if (automatico && !correo.entregaDeVerdad())
            throw new DomainException("CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: no se puede activar el envío automático a los clientes. Contacte a soporte.");
        uow.ejecutar(() -> envios.fijarAutomatico(tenantId, automatico));
        return preferencia(tenantId);
    }

    @Override public Optional<EnvioAlCliente> envioDe(UUID tenantId, UUID documentoId) {
        return envios.deComprobante(tenantId, documentoId);
    }

    @Override public int enviarPendientes() {
        List<Tomado> lote = uow.ejecutar(() -> envios.tomarVencidos(LOTE, BLOQUEO));
        for (Tomado t : lote) {
            try {
                atender(t);
            } catch (RuntimeException e) {
                // Lo que falle al anotar el resultado no frena al resto del lote: el envío vuelve a tomarse al vencer su bloqueo.
                log.error("No se pudo procesar el envío al cliente del comprobante {}", t.documentoId(), e);
            }
        }
        return lote.size();
    }

    private void atender(Tomado t) {
        if (!correo.entregaDeVerdad()) {
            omitir(t, SIN_SMTP);
            return;
        }
        if (!envios.automatico(t.tenantId())) {
            omitir(t, DESACTIVADO);
            return;
        }
        CorreoDeComprobante mail;
        try {
            Comprobante c = consultar.obtener(t.tenantId(), t.documentoId());
            // Se anotó al aceptarse; si se anuló antes de que llegara su turno, ya no vale y no se manda.
            if (!c.estado().esFinalAceptado()) {
                omitir(t, "El comprobante ya no está aceptado (" + c.estado() + "): no se envió");
                return;
            }
            if (bajas.deComprobante(t.tenantId(), t.documentoId()).stream().anyMatch(ComunicacionBaja::pendiente)) {
                omitir(t, BAJA_EN_CURSO);
                return;
            }
            Tenant tenant = tenants.buscar(t.tenantId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
            mail = CorreoDeComprobante.de(c, tenant, null, consultar);
        } catch (RuntimeException e) {
            // Un archivo que no se pudo leer del storage puede ser pasajero: se reintenta. El detalle (la clave del storage) solo va al log.
            log.warn("Envío al cliente del comprobante {}: no se pudieron preparar los adjuntos", t.documentoId(), e);
            fallar(t, SIN_ADJUNTOS);
            return;
        }
        if (mail.pesoDeAdjuntos() > EnvioAlCliente.TOPE_ADJUNTOS) {
            // Reintentar no lo achica: queda fallido con el motivo, y el emisor lo descarga y lo manda por otro medio.
            registrar(t, uow.ejecutar(() -> envios.registrarFallo(t, "Los adjuntos pesan " + megas(mail.pesoDeAdjuntos()) + " MB y el tope es " + megas(EnvioAlCliente.TOPE_ADJUNTOS)
                    + " MB: no se envió. Descargue los archivos y envíelos por su cuenta.", clock.instant(), null)));
            return;
        }
        Optional<Tomado> vigente = uow.ejecutar(() -> envios.renovar(t, BLOQUEO));
        if (vigente.isEmpty()) {
            log.warn("Envío al cliente del comprobante {}: otra instancia lo retomó mientras esta atendía el lote; no se manda", t.documentoId());
            return;
        }
        Tomado renovado = vigente.get();
        try {
            correo.enviar(renovado.destinatario(), mail.asunto(), mail.cuerpo(), mail.adjuntos());
        } catch (CorreoNoEnviadoException e) {
            log.warn("Envío al cliente del comprobante {}: el correo no salió", renovado.documentoId(), e);
            fallar(renovado, e.motivo().descripcion());
            return;
        } catch (RuntimeException e) {
            log.warn("Envío al cliente del comprobante {}: el correo no salió", renovado.documentoId(), e);
            fallar(renovado, CorreoNoEnviadoException.Motivo.OTRO.descripcion());
            return;
        }
        Instant ahora = clock.instant();
        registrar(renovado, uow.ejecutar(() -> envios.registrarEnvio(renovado, ahora)));
    }

    private void fallar(Tomado t, String motivo) {
        Instant ahora = clock.instant();
        int intento = t.intentos() + 1;
        Instant siguiente = EnvioAlCliente.siguienteIntento(intento, ahora);
        log.warn("Envío al cliente del comprobante {}: intento {} fallido ({}){}", t.documentoId(), intento, motivo, siguiente == null ? "; no se reintenta más" : "");
        registrar(t, uow.ejecutar(() -> envios.registrarFallo(t, motivo, ahora, siguiente)));
    }

    private void omitir(Tomado t, String motivo) {
        Instant ahora = clock.instant();
        registrar(t, uow.ejecutar(() -> envios.omitir(t, motivo, ahora)));
    }

    private static void registrar(Tomado t, boolean anotado) {
        if (!anotado) log.warn("Envío al cliente del comprobante {}: ya no estaba tomado por esta instancia; no se anota el resultado", t.documentoId());
    }

    private static String megas(long bytes) { return java.math.BigDecimal.valueOf(bytes).divide(java.math.BigDecimal.valueOf(1024L * 1024), 1, java.math.RoundingMode.HALF_UP).toPlainString(); }
}
