package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.application.port.out.PdfGenerator;
import pe.factura.application.port.out.EmisorDeSerieRepository;
import pe.factura.application.port.out.EmisorFirmado;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.CodigoQr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RequiredArgsConstructor
public class ConsultarComprobanteService implements ConsultarComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final PdfGenerator pdf;
    private final EmisorDeSerieRepository emisorDeSerie;
    private final EmisorFirmado emisorFirmado;

    public Comprobante obtener(UUID tenantId, UUID id) {
        return comprobantes.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }
    public List<Comprobante> notasDe(UUID tenantId, Comprobante factura) {
        return factura.tipo() == TipoDocumento.FACTURA && factura.numero() != null ? comprobantes.notasDe(tenantId, factura.serie(), factura.numero()) : List.of();
    }
    public List<pe.factura.domain.documento.EventoDocumento> eventos(UUID tenantId, Comprobante c) { return comprobantes.eventosDe(tenantId, c.id()); }
    public List<Comprobante> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina) { return comprobantes.listar(tenantId, filtro == null ? Filtro.NINGUNO : filtro, pagina, porPagina); }
    public long contar(UUID tenantId, Filtro filtro) { return comprobantes.contar(tenantId, filtro == null ? Filtro.NINGUNO : filtro); }
    public byte[] xml(UUID tenantId, UUID id) { return storage.leer(obtener(tenantId, id).xmlKey()); }
    public byte[] cdr(UUID tenantId, UUID id) {
        Comprobante c = obtener(tenantId, id);
        if (c.cdrKey() == null) throw new DomainException("SIN_CDR", "El comprobante aún no tiene CDR");
        return storage.leer(c.cdrKey());
    }
    /**
     * Versión del diseño de la representación impresa, parte de la clave del PDF en storage. Súbala cuando cambie una plantilla
     * de {@code adapters/out-pdf}: los PDF ya generados quedan con la versión anterior y el siguiente {@link #pdf} regenera con la nueva
     * sin tocar storage a mano.
     *
     * <p>La 2 es la que corrige el emisor. Hasta la 1 el PDF se armaba con los datos fiscales de <em>hoy</em>, así que
     * los que quedaron en caché después de que una empresa mudara su domicilio o editara un anexo tienen una dirección
     * que no es la del XML firmado. Subir la versión los regenera solos en la próxima descarga.
     */
    static final int VERSION_PDF = 2;

    /**
     * El PDF vive junto al XML firmado (misma clave, sufijo {@code -v<versión>-<huella del diseño>.pdf}); si falta —o nunca se pidió—
     * se genera y se guarda. La huella hace que un cambio de plantilla, color, logo o textos regenere el siguiente PDF sin tocar los ya emitidos.
     */
    public byte[] pdf(UUID tenantId, UUID id) {
        Comprobante c = obtener(tenantId, id);
        if (c.xmlKey() == null) throw new DomainException("SIN_FIRMA", "El comprobante aún no está firmado: no tiene representación impresa");
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        String key = c.xmlKey().replaceFirst("\\.xml$", "-v" + VERSION_PDF + "-" + t.personalizacionPdf().huella() + ".pdf");
        if (storage.existe(key)) return storage.leer(key);
        // La identidad la manda el XML firmado: es lo que SUNAT recibió, y así la impresa no puede contradecirlo por
        // más que después cambien el domicilio fiscal, el de un anexo o la serie. Si el XML no se puede leer se cae al
        // emisor de la serie, que es lo que se hacía antes: peor que exacto, pero mejor que no poder imprimir.
        Tenant emisor = emisorFirmado.leer(storage.leer(c.xmlKey()))
                .map(e -> e.sobre(t))
                .orElseGet(() -> EmisorDeSerie.paraImprimir(emisorDeSerie, t, c));
        byte[] bytes = pdf.generar(c, emisor, CodigoQr.contenido(c, emisor.ruc()), PersonalizarPdfService.logoDe(storage, t.personalizacionPdf()));
        storage.guardar(key, bytes);
        return bytes;
    }
    public byte[] cdrXml(UUID tenantId, UUID id) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(cdr(tenantId, id)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith(".xml")) return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new DomainException("CDR_CORRUPTO", "No se pudo leer el ZIP del CDR", e);
        }
        throw new DomainException("CDR_CORRUPTO", "El ZIP del CDR no contiene un XML");
    }
}
