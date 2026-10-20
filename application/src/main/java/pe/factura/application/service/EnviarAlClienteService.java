package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.EnviarAlClienteUseCase;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.EnvioAlClienteRepository;
import pe.factura.application.port.out.EnvioAlClienteRepository.Tomado;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
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
 * <p><b>Una sola vez.</b> Cada pasada toma los envíos con bloqueo ({@link #BLOQUEO}) y el resultado solo se escribe si el envío sigue tomado con la misma marca: dos réplicas no
 * mandan el mismo a la vez y una instancia lenta no pisa a la que lo volvió a tomar. Lo único que no se puede evitar es el caso en que el servidor de correo ya recibió el mensaje
 * y la base cae antes de anotarlo: pasado el bloqueo se manda de nuevo. Es preferible a perderlo, y SMTP no ofrece cómo saber si un mensaje ya se entregó.
 *
 * <p><b>Sin SMTP</b> ({@link CorreoSender#entregaDeVerdad()} falso) lo pendiente se omite con el motivo, no se da por enviado; tampoco se deja esperando, porque al configurarse el
 * correo saldrían de golpe comprobantes de días atrás que el emisor quizá ya mandó a mano.
 */
@Slf4j
@RequiredArgsConstructor
public class EnviarAlClienteService implements EnviarAlClienteUseCase {
    static final int LOTE = 20;
    /** Sobra para armar los adjuntos y mandar un lote entero con el tope de tiempo del SMTP. */
    static final Duration BLOQUEO = Duration.ofMinutes(10);
    static final String SIN_SMTP = "El servidor no tiene correo configurado: no se envió. Descargue el PDF y envíelo por su cuenta.";

    private final EnvioAlClienteRepository envios;
    private final ConsultarComprobanteUseCase consultar;
    private final TenantRepository tenants;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final Clock clock;

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
        CorreoDeComprobante mail;
        try {
            Comprobante c = consultar.obtener(t.tenantId(), t.documentoId());
            // Se anotó al aceptarse; si se anuló antes de que llegara su turno, ya no vale y no se manda.
            if (!c.estado().esFinalAceptado()) {
                omitir(t, "El comprobante ya no está aceptado (" + c.estado() + "): no se envió");
                return;
            }
            Tenant tenant = tenants.buscar(t.tenantId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
            mail = CorreoDeComprobante.de(c, tenant, null, consultar);
        } catch (RuntimeException e) {
            // Un archivo que no se pudo leer del storage puede ser pasajero: se reintenta como un fallo del correo.
            fallar(t, "No se pudieron preparar los adjuntos: " + e.getMessage());
            return;
        }
        if (mail.pesoDeAdjuntos() > EnvioAlCliente.TOPE_ADJUNTOS) {
            // Reintentar no lo achica: queda fallido con el motivo, y el emisor lo descarga y lo manda por otro medio.
            registrar(t, uow.ejecutar(() -> envios.registrarFallo(t, "Los adjuntos pesan " + megas(mail.pesoDeAdjuntos()) + " MB y el tope es " + megas(EnvioAlCliente.TOPE_ADJUNTOS)
                    + " MB: no se envió. Descargue los archivos y envíelos por su cuenta.", clock.instant(), null)));
            return;
        }
        try {
            correo.enviar(t.destinatario(), mail.asunto(), mail.cuerpo(), mail.adjuntos());
        } catch (RuntimeException e) {
            fallar(t, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return;
        }
        Instant ahora = clock.instant();
        registrar(t, uow.ejecutar(() -> envios.registrarEnvio(t, ahora)));
    }

    private void fallar(Tomado t, String error) {
        Instant ahora = clock.instant();
        int intento = t.intentos() + 1;
        Instant siguiente = EnvioAlCliente.siguienteIntento(intento, ahora);
        log.warn("Envío al cliente del comprobante {}: intento {} fallido ({}){}", t.documentoId(), intento, error, siguiente == null ? "; no se reintenta más" : "");
        registrar(t, uow.ejecutar(() -> envios.registrarFallo(t, error, ahora, siguiente)));
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
