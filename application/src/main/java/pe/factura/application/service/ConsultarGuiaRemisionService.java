package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarGuiaRemisionUseCase;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.application.port.out.GuiaPdfGenerator;
import pe.factura.application.port.out.GuiaRemisionRepository;
import pe.factura.application.port.out.GuiaRemisionRepository.Filtro;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Consulta de las guías de remisión de una empresa y de sus archivos (#39). */
@RequiredArgsConstructor
public class ConsultarGuiaRemisionService implements ConsultarGuiaRemisionUseCase {
    /** Versión del diseño del PDF de la guía, parte de su clave en storage: subirla regenera los PDF ya guardados (ver ConsultarComprobanteService.VERSION_PDF). */
    static final int VERSION_PDF = 1;

    private final GuiaRemisionRepository guias;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final GuiaPdfGenerator pdf;

    @Override public GuiaRemision obtener(UUID tenantId, UUID id) {
        return guias.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Guía de remisión no encontrada"));
    }

    @Override public List<GuiaRemision> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina) { return guias.listar(tenantId, filtro == null ? Filtro.NINGUNO : filtro, pagina, porPagina); }

    @Override public long contar(UUID tenantId, Filtro filtro) { return guias.contar(tenantId, filtro == null ? Filtro.NINGUNO : filtro); }

    @Override public byte[] xml(UUID tenantId, UUID id) { return storage.leer(obtener(tenantId, id).xmlKey()); }

    @Override public byte[] cdr(UUID tenantId, UUID id) {
        GuiaRemision g = obtener(tenantId, id);
        if (g.cdrKey() == null) throw new DomainException("SIN_CDR", "La guía aún no tiene CDR");
        return storage.leer(g.cdrKey());
    }

    @Override public byte[] cdrXml(UUID tenantId, UUID id) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(cdr(tenantId, id)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) if (!e.isDirectory() && e.getName().toLowerCase().endsWith(".xml")) return in.readAllBytes();
        } catch (IOException e) {
            throw new DomainException("CDR_CORRUPTO", "No se pudo leer el ZIP del CDR", e);
        }
        throw new DomainException("CDR_CORRUPTO", "El ZIP del CDR no contiene un XML");
    }

    /**
     * El PDF vive junto al XML firmado; se genera la primera vez y se guarda. La clave lleva la versión del diseño, la huella de la personalización de la empresa
     * y si ya tiene el QR del CDR: el que se pidió antes de que SUNAT respondiera (sin QR) se regenera cuando llega el CDR.
     */
    @Override public byte[] pdf(UUID tenantId, UUID id) {
        GuiaRemision g = obtener(tenantId, id);
        if (g.xmlKey() == null) throw new DomainException("SIN_FIRMA", "La guía aún no está firmada: no tiene representación impresa");
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Empresa no encontrada"));
        String key = g.xmlKey().replaceFirst("\\.xml$", "-v" + VERSION_PDF + "-" + t.personalizacionPdf().huella() + (g.enlaceConsulta() == null ? "" : "-qr") + ".pdf");
        if (storage.existe(key)) return storage.leer(key);
        byte[] bytes = pdf.generar(g, t, g.enlaceConsulta(), PersonalizarPdfService.logoDe(storage, t.personalizacionPdf()));
        storage.guardar(key, bytes);
        return bytes;
    }
}
