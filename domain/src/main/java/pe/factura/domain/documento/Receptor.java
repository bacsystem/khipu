package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;

/** Adquirente. Se valida al emitir ({@link #exigirValidoParaFactura()}), no al rehidratar: los comprobantes ya emitidos no se tocan. */
public record Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion) {
    public boolean esRuc() { return "6".equals(tipoDoc) && numDoc != null && numDoc.matches("\\d{11}"); }

    /** Factura: RUC con dígito verificador (2017), razón social de 3 a 1500 caracteres sin saltos de línea (2021/2022). */
    void exigirValidoParaFactura() {
        if (!esRuc()) throw new DomainException("RECEPTOR_INVALIDO", "2017 - La factura requiere un receptor con RUC (tipo_doc 6, 11 dígitos)");
        Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2017 - Receptor");
        if (razonSocial == null || razonSocial.strip().length() < 3 || razonSocial.strip().length() > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RECEPTOR_INVALIDO", "2022 - La razón social del receptor tiene de 3 a 1500 caracteres, sin saltos de línea");
    }
}
