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
 * ACEPTADO/RECHAZADO) y se guarda junto al XML; sin CDR no se toca nada y el outbox sigue su curso. Solo para lo que SUNAT consulta por getStatusCdr
 * ({@link Comprobante#consultableEnGetStatusCdr()}): la boleta y las notas B se confirman por su resumen diario (#357).
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
        // Después de «ya lo tiene» (una boleta aceptada con su CDR se descarga, no se manda a buscar a otro lado) y antes de hablar con SUNAT (#357).
        // #327: la liquidación de compra tampoco está en getStatusCdr (manual del programador: 01, 07 y 08). Su único canal es sendBill: reenviarla devuelve su CDR.
        if (c.esLiquidacionCompra())
            throw new DomainException("CDR_NO_CONSULTABLE", "SUNAT no consulta por getStatusCdr las liquidaciones de compra (el servicio cubre facturas y sus notas): su constancia llega en el CDR del envío. "
                    + "Si quedó en ERROR_ENVIO, reintente el envío con POST /v1/facturas/{id}/enviar (reenvía el mismo XML)");
        // #40: tampoco la retención ni la percepción (manual del programador: getStatusCdr cubre 01, 07 y 08); su constancia llega en el CDR del envío al servicio de otros CPE.
        if (c.esDeAgente())
            throw new DomainException("CDR_NO_CONSULTABLE", "SUNAT no consulta por getStatusCdr los comprobantes de retención ni de percepción (el servicio cubre facturas y sus notas): su constancia "
                    + "llega en el CDR del envío. Si quedó en ERROR_ENVIO, reintente el envío con POST /v1/facturas/{id}/enviar (reenvía el mismo XML)");
        if (!c.consultableEnGetStatusCdr())
            throw new DomainException("CDR_NO_CONSULTABLE", "SUNAT no consulta por getStatusCdr las boletas ni las notas de boleta (serie B): su constancia llega en el CDR del envío individual o en el del resumen diario "
                    + "que las informa. Si se informó en un resumen, véalo en GET /v1/facturas/{id}/bajas y consulte su ticket con GET /v1/bajas/{id}; si se envió sola y quedó en ERROR_ENVIO, "
                    + "reintente el envío con POST /v1/facturas/{id}/enviar (reenvía el mismo XML). Por esta vía solo se recupera el CDR de facturas y sus notas (serie F)");
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
    public Barrido recuperarPendientes() {
        List<Comprobante> recuperados = new ArrayList<>();
        List<Fallo> fallos = new ArrayList<>();
        for (Comprobante c : comprobantes.pendientesDeCdr()) {
            if (!c.consultableEnGetStatusCdr()) continue;   // la consulta del repositorio ya lo excluye; esto es por si cambia
            try {
                // Dentro del manejo: leer la empresa descifra sus credenciales y puede fallar para una sola; no por eso se deja sin recuperar a las demás.
                Tenant t = tenants.buscar(c.tenantId()).orElse(null);
                if (t == null || t.entorno() != Entorno.PRODUCCION || t.sol() == null) continue;   // e-beta no publica billConsultService
                Comprobante r = recuperar(t, c);
                if (r.cdrKey() != null) recuperados.add(r);
            } catch (RuntimeException e) {
                // Un fault definitivo de SUNAT, SUNAT caído, un storage roto o un CDR ilegible en este comprobante no dejan sin recuperar a los demás: la siguiente pasada
                // lo reintenta y el fallo viaja al llamador con su comprobante, para que quede en el log y no se pierda en silencio.
                fallos.add(new Fallo(c, e));
            }
        }
        return new Barrido(recuperados, fallos);
    }
}
