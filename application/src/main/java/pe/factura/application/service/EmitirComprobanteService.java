package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class EmitirComprobanteService implements EmitirComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final SerieRepository series;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final EnviarDocumentoUseCase enviar;
    private final UnitOfWork uow;
    private final Clock clock;


    @Override
    public Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));
        // Si se va a enviar de inmediato, las credenciales SOL deben existir antes de consumir un número
        // o persistir el documento: de lo contrario quedaría un FIRMADO huérfano con numeración gastada.
        if (cmd.enviarAutomatico()) tenant.exigirCredencialesSol();
        List<Anticipo> anticipos = cmd.anticipos() == null ? List.of() : cmd.anticipos();
        anticipos.forEach(a -> validarFacturaDeAnticipo(tenantId, cmd, a));
        // La cuenta de detracciones puede omitirse en la factura si la empresa la tiene configurada.
        Detraccion detraccion = cmd.detraccion() != null && cmd.detraccion().sinCuenta() ? cmd.detraccion().conCuenta(tenant.cuentaDetracciones()) : cmd.detraccion();

        Comprobante c = Comprobante.crearFactura(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(),
                cmd.tipoOperacion(), cmd.receptor(), cmd.items(), cmd.formaPago(), cmd.descuentoGlobal(), detraccion, cmd.retencionIgv(), cmd.percepcion(), anticipos, clock);

        Comprobante firmado = uow.ejecutar(() -> {
            long numero;
            if (cmd.correlativo() != null) {
                if (comprobantes.existe(tenantId, TipoDocumento.FACTURA, cmd.serie(), cmd.correlativo()))
                    throw new DomainException("DUPLICADO", "Ya existe " + cmd.serie() + "-" + cmd.correlativo());
                // La serie avanza hasta el correlativo explícito para que la siguiente emisión automática no lo reutilice.
                series.avanzarHasta(tenantId, TipoDocumento.FACTURA, cmd.serie(), cmd.correlativo());
                numero = cmd.correlativo();
            } else {
                numero = series.siguienteNumero(tenantId, TipoDocumento.FACTURA, cmd.serie());
            }
            c.asignarNumero(numero, tenant.ruc());

            String xml = ubl.generar(c, tenant);
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());
            xsd.validar(firma.xmlFirmado(), TipoDocumento.FACTURA);

            String key = tenantId + "/" + c.fechaEmision().getYear() + "/" + String.format("%02d", c.fechaEmision().getMonthValue())
                    + "/" + c.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            c.firmar(firma.hash(), key);
            comprobantes.guardar(c);
            return c;
        });

        if (!cmd.enviarAutomatico()) return firmado;
        // EnviarDocumentoService persiste el resultado y, si queda en ERROR_ENVIO, programa el reintento
        // en el outbox dentro de la misma transacción.
        return enviar.enviar(tenantId, firmado.id());
    }

    /**
     * La factura de anticipo debe ser de esta empresa y estar aceptada por SUNAT (regla 3218), al mismo cliente y en la misma
     * moneda (2071), y el monto que se regulariza no puede superar lo que aquella facturó en esa afectación.
     */
    private void validarFacturaDeAnticipo(UUID tenantId, EmitirFacturaCommand cmd, Anticipo a) {
        Comprobante origen = comprobantes.buscarPorNumero(tenantId, TipoDocumento.FACTURA, a.serie(), a.numero())
                .orElseThrow(() -> new DomainException("ANTICIPO_INVALIDO", "3218 - La factura de anticipo " + a.comprobante() + " no existe en esta empresa"));
        if (origen.estado() != EstadoDocumento.ACEPTADO && origen.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("ANTICIPO_INVALIDO", "3218 - La factura de anticipo " + a.comprobante() + " no está aceptada por SUNAT (estado " + origen.estado() + ")");
        if (!origen.moneda().equals(cmd.moneda()))
            throw new DomainException("ANTICIPO_INVALIDO", "2071 - La factura de anticipo " + a.comprobante() + " es en " + origen.moneda() + " y esta factura en " + cmd.moneda());
        if (!origen.receptor().numDoc().equals(cmd.receptor().numDoc()))
            throw new DomainException("ANTICIPO_INVALIDO", "La factura de anticipo " + a.comprobante() + " fue emitida a otro cliente (RUC " + origen.receptor().numDoc() + ")");
        BigDecimal facturado = switch (a.afectacion()) {
            case GRAVADO -> origen.totales().gravado();
            case EXONERADO -> origen.totales().exonerado();
            case INAFECTO -> origen.totales().inafecto();
        };
        if (a.monto().compareTo(facturado) > 0)
            throw new DomainException("ANTICIPO_INVALIDO", "El anticipo " + a.comprobante() + " (" + a.monto() + ") supera el valor de venta "
                    + a.afectacion().name().toLowerCase() + " de esa factura (" + facturado + ")");
    }
}
