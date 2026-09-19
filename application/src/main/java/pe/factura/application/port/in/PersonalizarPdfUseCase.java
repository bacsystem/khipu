package pe.factura.application.port.in;

import pe.factura.domain.tenant.PersonalizacionPdf;

import java.util.UUID;

/** Diseño de la representación impresa de la empresa (plantilla, color, logo, textos) y su vista previa. */
public interface PersonalizarPdfUseCase {
    PersonalizacionPdf obtener(UUID tenantId);
    /** Guarda plantilla, color, pie y observaciones por defecto; el logo se conserva (se gestiona con cargarLogo/borrarLogo). */
    PersonalizacionPdf actualizar(UUID tenantId, PersonalizacionPdf diseño);
    /** PNG o JPEG de hasta 200 KB (DomainException("LOGO_INVALIDO")); reemplaza el anterior. */
    PersonalizacionPdf cargarLogo(UUID tenantId, byte[] logo);
    PersonalizacionPdf borrarLogo(UUID tenantId);
    /** Bytes del logo actual; DomainException("NO_ENCONTRADO") si no hay. */
    byte[] logo(UUID tenantId);
    /** PDF de una factura de ejemplo con el diseño indicado (sin guardarlo) y el logo actual de la empresa. */
    byte[] vistaPrevia(UUID tenantId, PersonalizacionPdf diseño);
}
