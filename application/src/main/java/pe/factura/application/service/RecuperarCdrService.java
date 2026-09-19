package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recuperación de CDR (#36): si sendBill respondió pero la conexión se cortó antes de guardar la constancia, SUNAT tiene
 * el comprobante y khipu no. getStatusCdr lo cierra sin reenviar: con CDR se aplica como si acabara de llegar (ENVIADO →
 * ACEPTADO/RECHAZADO) y se guarda junto al XML; sin CDR no se toca nada y el outbox sigue su curso.
 */
@RequiredArgsConstructor
public class RecuperarCdrService implements RecuperarCdrUseCase {
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final SunatConsultaGateway consultas;
    private final CdrParser cdrParser;
    private final UnitOfWork uow;

    @Override
    public Comprobante recuperar(UUID tenantId, UUID comprobanteId) {
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        return recuperar(t, c);
    }

    private Comprobante recuperar(Tenant t, Comprobante c) {
        if (c.numero() == null || c.xmlKey() == null)
            throw new DomainException("SIN_FIRMA", "El comprobante aún no está firmado ni enviado: no puede tener CDR en SUNAT");
        boolean sinCdr = c.cdrKey() == null;
        boolean enCurso = c.estado() == EstadoDocumento.ENVIADO || c.estado() == EstadoDocumento.ERROR_ENVIO || c.estado() == EstadoDocumento.FIRMADO;
        if (!sinCdr && !enCurso)
            throw new DomainException("CDR_YA_DISPONIBLE", "El comprobante ya tiene su CDR (" + c.estado() + "); descárguelo con GET /v1/facturas/{id}/cdr");
        t.exigirCredencialesSol();
        SunatConsultaGateway.Consulta r = consultas.getStatusCdr(t, t.ruc(), c.tipo().codigo(), c.serie(), c.numero());
        if (!r.conCdr()) return c;   // SUNAT no lo tiene (o solo informa estado): nada que aplicar
        Cdr cdr = cdrParser.parsear(r.cdrZip());
        String cdrKey = c.xmlKey().substring(0, c.xmlKey().lastIndexOf('/') + 1) + "R-" + c.nombreArchivo() + ".zip";
        storage.guardar(cdrKey, r.cdrZip());
        if (enCurso) {
            if (c.estado() != EstadoDocumento.ENVIADO) c.marcarEnviado();
            c.aplicarCdr(cdr, cdrKey);
        } else {
            c.restaurarCdr(cdr, cdrKey);
        }
        uow.ejecutar(() -> comprobantes.guardar(c));
        return c;
    }

    @Override
    public List<Comprobante> recuperarPendientes() {
        List<Comprobante> recuperados = new ArrayList<>();
        for (Comprobante c : comprobantes.pendientesDeCdr()) {
            Tenant t = tenants.buscar(c.tenantId()).orElse(null);
            if (t == null || t.entorno() != Entorno.PRODUCCION || t.sol() == null) continue;   // e-beta no publica billConsultService
            try {
                Comprobante r = recuperar(t, c);
                if (r.cdrKey() != null) recuperados.add(r);
            } catch (SunatTransientException | DomainException e) {
                // Sin CDR o SUNAT caído: el outbox y la siguiente pasada se encargan.
            }
        }
        return recuperados;
    }
}
