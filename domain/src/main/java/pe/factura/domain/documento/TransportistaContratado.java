package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;

/**
 * La empresa de transporte de una guía remitente en transporte público ({@code cac:ShipmentStage/cac:CarrierParty}): RUC, razón social y, opcional, su
 * número de registro del MTC.
 */
public record TransportistaContratado(String ruc, String razonSocial, String registroMtc) {
    public TransportistaContratado {
        ruc = TextoDeGuia.limpio(ruc);
        razonSocial = TextoDeGuia.limpio(razonSocial);
        registroMtc = TextoDeGuia.compacto(registroMtc);
    }

    void exigirValido() {
        if (ruc == null) throw new DomainException("TRANSPORTE_INVALIDO", "4160 - Falta el RUC del transportista");
        Ruc.exigirValido(ruc, "TRANSPORTE_INVALIDO", "4163 - El RUC del transportista");
        if (!TextoDeGuia.unaLinea(razonSocial, 3, 250))
            throw new DomainException("TRANSPORTE_INVALIDO", "4164 - La razón social del transportista tiene de 3 a 250 caracteres, sin saltos de línea");
        exigirRegistroMtc(registroMtc, false);
    }

    /** Número de registro del MTC: hasta 20 letras o dígitos (4392); {@code obligatorio} en la guía transportista (4391). */
    static void exigirRegistroMtc(String registro, boolean obligatorio) {
        if (registro == null) {
            if (obligatorio) throw new DomainException("TRANSPORTE_INVALIDO", "4391 - Falta el número de registro MTC del transportista");
            return;
        }
        if (!registro.matches("[A-Z0-9]{1,20}"))
            throw new DomainException("TRANSPORTE_INVALIDO", "4392 - El número de registro MTC del transportista son hasta 20 letras o dígitos; recibido " + registro);
    }
}
