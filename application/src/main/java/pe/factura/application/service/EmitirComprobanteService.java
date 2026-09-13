package pe.factura.application.service;

import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public class EmitirComprobanteService implements EmitirComprobanteUseCase {
    public static final String ACCION_ENVIAR = "ENVIAR";

    private final ComprobanteRepository comprobantes;
    private final SerieRepository series;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final OutboxRepository outbox;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final EnviarDocumentoUseCase enviar;
    private final UnitOfWork uow;
    private final Clock clock;

    public EmitirComprobanteService(ComprobanteRepository comprobantes, SerieRepository series, TenantRepository tenants,
                                    DocumentStorage storage, OutboxRepository outbox, UblGenerator ubl, XsdValidator xsd,
                                    XmlSigner signer, EnviarDocumentoUseCase enviar, UnitOfWork uow, Clock clock) {
        this.comprobantes = comprobantes; this.series = series; this.tenants = tenants; this.storage = storage;
        this.outbox = outbox; this.ubl = ubl; this.xsd = xsd; this.signer = signer; this.enviar = enviar;
        this.uow = uow; this.clock = clock;
    }

    @Override
    public Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));

        Comprobante c = Comprobante.crearFactura(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(),
                cmd.tipoOperacion(), cmd.receptor(), cmd.items(), clock);

        Comprobante firmado = uow.ejecutar(() -> {
            long numero;
            if (cmd.correlativo() != null) {
                if (comprobantes.existe(tenantId, TipoDocumento.FACTURA, cmd.serie(), cmd.correlativo()))
                    throw new DomainException("DUPLICADO", "Ya existe " + cmd.serie() + "-" + cmd.correlativo());
                numero = cmd.correlativo();
            } else {
                numero = series.siguienteNumero(tenantId, TipoDocumento.FACTURA, cmd.serie());
            }
            c.asignarNumero(numero, tenant.ruc());

            String xml = ubl.generar(c, tenant);
            xsd.validar(xml, TipoDocumento.FACTURA);
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());

            String key = tenantId + "/" + c.fechaEmision().getYear() + "/" + String.format("%02d", c.fechaEmision().getMonthValue())
                    + "/" + c.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            c.firmar(firma.hash(), key);
            comprobantes.guardar(c);
            return c;
        });

        if (!cmd.enviarAutomatico()) return firmado;

        Comprobante enviado = enviar.enviar(tenantId, firmado.id());
        if (enviado.estado() == EstadoDocumento.ERROR_ENVIO) {
            uow.ejecutar(() -> outbox.programar(tenantId, ACCION_ENVIAR, enviado.id(), Backoff.siguiente(enviado.intentos(), clock.instant())));
        }
        return enviado;
    }
}
