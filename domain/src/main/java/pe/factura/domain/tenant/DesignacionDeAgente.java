package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;

/**
 * Si SUNAT designó a la empresa agente de retención o de percepción del IGV (#40): solo entonces emite comprobantes de retención (20) o de percepción (40). La designación es una
 * resolución de SUNAT que figura en sus padrones de agentes; khipu no la consulta: la registra el administrador de la plataforma después de verificarla
 * ({@code POST /v1/admin/empresas/{id}/agente}). SUNAT sí la cruza: rechaza la retención de quien no es agente de retención (2617, error) y observa la percepción de quien no es agente
 * de percepción (4285); e-beta no aplica ninguna de las dos (2026-10-20 aceptó retenciones y percepciones de una empresa de prueba que no es agente).
 */
public record DesignacionDeAgente(boolean retencion, boolean percepcion) {
    public static final DesignacionDeAgente NINGUNA = new DesignacionDeAgente(false, false);

    public boolean designadoPara(TipoDocumento tipo) {
        return switch (tipo) {
            case RETENCION -> retencion;
            case PERCEPCION -> percepcion;
            default -> true;
        };
    }

    /** Lo que pide emitir un 20 o un 40 (o crear su serie): la designación de ese tipo. */
    public void exigirPara(TipoDocumento tipo) {
        if (designadoPara(tipo)) return;
        if (tipo == TipoDocumento.RETENCION)
            throw new DomainException("AGENTE_NO_DESIGNADO", "2617 - La empresa no está registrada como agente de retención: SUNAT rechazaría el comprobante. Si SUNAT la designó, pida al soporte "
                    + "de khipu que la habilite con la resolución de designación");
        throw new DomainException("AGENTE_NO_DESIGNADO", "4285 - La empresa no está registrada como agente de percepción: SUNAT observaría el comprobante y la percepción no tendría sustento. "
                + "Si SUNAT la designó, pida al soporte de khipu que la habilite con la resolución de designación");
    }
}
