package pe.factura.domain.documento;

/**
 * Lo que distingue a una guía remitente de una transportista. Es el único lugar del que sale el tipo de la guía ({@link #tipo()}): no hay una bandera
 * aparte que pueda contradecir a estos datos (una guía «09» sin motivo o una «31» sin remitente no se pueden construir).
 */
public sealed interface DatosDeGuia permits DatosDeGuia.DeRemitente, DatosDeGuia.DeTransportista {
    TipoDocumento tipo();

    /**
     * Guía remitente (09): la emite quien envía los bienes. Motivo del catálogo 20 con su descripción ({@code cbc:HandlingCode} y
     * {@code cbc:HandlingInstructions}), modalidad del catálogo 18 y, en transporte público, la empresa de transporte.
     */
    record DeRemitente(String motivo, String descripcionMotivo, ModalidadTraslado modalidad, TransportistaContratado transportista) implements DatosDeGuia {
        public DeRemitente {
            motivo = TextoDeGuia.limpio(motivo);
            descripcionMotivo = TextoDeGuia.limpio(descripcionMotivo);
        }
        @Override public TipoDocumento tipo() { return TipoDocumento.GUIA_REMITENTE; }
    }

    /**
     * Guía transportista (31): la emite la empresa de transporte. Lleva al remitente de los bienes ({@code cac:DespatchParty}) y el número de registro
     * del MTC del propio transportista emisor ({@code cac:DespatchSupplierParty/.../cbc:CompanyID}); no lleva motivo ni modalidad.
     */
    record DeTransportista(ParteDeGuia remitente, String registroMtc) implements DatosDeGuia {
        public DeTransportista { registroMtc = TextoDeGuia.compacto(registroMtc); }
        @Override public TipoDocumento tipo() { return TipoDocumento.GUIA_TRANSPORTISTA; }
    }
}
