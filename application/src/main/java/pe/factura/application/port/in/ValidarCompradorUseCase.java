package pe.factura.application.port.in;

import pe.factura.domain.documento.Receptor;

import java.util.List;

/**
 * El comprador con RUC frente al padrón de SUNAT (#378). SUNAT valida el RUC del receptor en factura, boleta, nota de crédito y nota de débito con la misma regla:
 * si no existe, la rechaza (1083); si no está activo (4013) o no está habido (4014), la acepta con una observación.
 */
public interface ValidarCompradorUseCase {
    /** Lo que SUNAT observará del comprobante aunque lo acepte. {@code codigo}: el de SUNAT (4013, 4014). */
    record Aviso(String codigo, String mensaje) { }

    /**
     * Rechaza con {@code RECEPTOR_INVALIDO} (1083) un RUC que el padrón no tiene. No hace nada si el comprador no tiene RUC, ni si el padrón no se puede consultar
     * con confianza (nunca se cargó o está atrasado): un padrón viejo rechazaría ventas que SUNAT sí aceptaría.
     */
    void exigirQueExista(Receptor receptor);

    /** Las observaciones que SUNAT pondrá al comprobante por el estado del RUC; vacío si el comprador no tiene RUC, si está en regla o si el padrón no es confiable. */
    List<Aviso> avisosDe(Receptor receptor);
}
