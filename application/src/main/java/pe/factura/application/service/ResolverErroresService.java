package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.ResolverErroresUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.application.port.out.ColaDeErroresRepository.Ubicacion;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.util.UUID;

/**
 * Las acciones del administrador sobre la cola de errores (#196). El reintento es el envío de siempre ({@link EnviarDocumentoUseCase}, con sus reglas de estado y de plazo):
 * acá solo se ubica la empresa del comprobante y se deja la bitácora. El descarte es un cambio de estado del dominio ({@code Comprobante.descartar}) con el envío sacado del
 * outbox, todo en una transacción con la bitácora.
 */
@RequiredArgsConstructor
public class ResolverErroresService implements ResolverErroresUseCase {
    private final ColaDeErroresRepository cola;
    private final EnviarDocumentoUseCase enviar;
    private final ComprobanteRepository comprobantes;
    private final OutboxRepository outbox;
    private final TenantRepository tenants;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public Reintento reintentar(ActorAdmin actor, UUID comprobanteId) {
        Ubicacion u = ubicar(comprobanteId);
        Comprobante c;
        try {
            // Fuera de la transacción: el envío a SUNAT es lento y externo, y ya confirma su propio resultado.
            c = enviar.enviar(u.tenantId(), comprobanteId);
        } catch (DomainException e) {
            anotar(actor, AccionAdmin.REINTENTAR_ENVIO_COMPROBANTE, u, "comprobante=" + u.nombreArchivo() + " no se pudo=" + e.codigo());
            throw e;
        }
        anotar(actor, AccionAdmin.REINTENTAR_ENVIO_COMPROBANTE, u, "comprobante=" + u.nombreArchivo() + " resultado=" + c.estado());
        Cdr cdr = c.cdr();
        return new Reintento(comprobanteId, c.estado(), c.intentos(), FaultSunat.de(c.ultimoError(), cdr == null ? null : cdr.codigo(), cdr == null ? null : cdr.descripcion()));
    }

    @Override public Descarte descartar(ActorAdmin actor, UUID comprobanteId, String motivo) {
        String m = motivo == null ? "" : motivo.strip();
        if (m.isEmpty()) throw new DomainException("MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante");
        if (m.length() > MAX_MOTIVO) throw new DomainException("MOTIVO_LARGO", "El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
        Ubicacion u = ubicar(comprobanteId);
        uow.ejecutar(() -> {
            // La fila se bloquea y el guardado es condicional: si un envío en paralelo la resolvió en el medio, no se pisa su resultado.
            Comprobante c = comprobantes.bloquear(u.tenantId(), comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            c.descartar(m);
            comprobantes.guardar(c);
            outbox.completarPorAgregado(comprobanteId, EnviarDocumentoService.ACCION_ENVIAR);
            auditoria.registrar(registro(actor, AccionAdmin.DESCARTAR_COMPROBANTE, u, "comprobante=" + u.nombreArchivo() + " motivo=" + m));
        });
        return new Descarte(comprobanteId, EstadoDocumento.DESCARTADO);
    }

    private Ubicacion ubicar(UUID comprobanteId) {
        return comprobanteId == null ? fallar() : cola.ubicar(comprobanteId).orElseGet(ResolverErroresService::fallar);
    }

    private static Ubicacion fallar() { throw new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"); }

    private void anotar(ActorAdmin actor, AccionAdmin accion, Ubicacion u, String detalle) {
        uow.ejecutar(() -> auditoria.registrar(registro(actor, accion, u, detalle)));
    }

    /** La cuenta dueña, para que la acción aparezca también en la bitácora de la cuenta; vacía en las empresas de integración. */
    private RegistroAuditoria registro(ActorAdmin actor, AccionAdmin accion, Ubicacion u, String detalle) {
        return RegistroAuditoria.de(actor, accion, tenants.cuentaDe(u.tenantId()).orElse(null), u.tenantId(), detalle, clock.instant());
    }
}
