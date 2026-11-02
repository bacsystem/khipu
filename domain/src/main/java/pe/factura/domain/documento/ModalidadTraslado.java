package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;

/** Modalidad de traslado de la guía de remisión remitente (catálogo 18, {@code cac:ShipmentStage/cbc:TransportModeCode}). La guía transportista no la lleva. */
public enum ModalidadTraslado {
    /** La mercadería la lleva una empresa de transporte (la {@link TransportistaContratado} de la guía). */
    PUBLICO("01"),
    /** La lleva el propio remitente, en su vehículo y con su conductor. */
    PRIVADO("02");

    @Getter private final String codigo;

    ModalidadTraslado(String codigo) { this.codigo = codigo; }

    public static ModalidadTraslado porCodigo(String codigo) {
        for (ModalidadTraslado m : values()) if (m.codigo.equals(codigo)) return m;
        throw new DomainException("TRASLADO_INVALIDO", "La modalidad de traslado es 01 (transporte público) o 02 (transporte privado), catálogo 18; recibido " + codigo);
    }
}
