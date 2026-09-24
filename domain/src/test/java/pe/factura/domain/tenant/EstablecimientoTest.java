package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstablecimientoTest {
    UUID tenant = UUID.randomUUID();
    Domicilio dom = Domicilio.de("150122", "Av. Larco 345");

    @Test void elDomicilioTomaElCodigoDelEstablecimiento() {
        Establecimiento e = new Establecimiento(tenant, " 0002 ", " Tienda Miraflores ", dom, true);
        assertThat(e.codigo()).isEqualTo("0002");
        assertThat(e.nombre()).isEqualTo("Tienda Miraflores");
        assertThat(e.domicilio().codigoEstablecimiento()).isEqualTo("0002");   // AddressTypeCode del XML
        assertThat(e.domicilio().distrito()).isEqualTo("MIRAFLORES");
        assertThat(e.desactivar().activo()).isFalse();
    }

    /**
     * El anexo reescribe el domicilio con su propio código, así que vuelve a pasar por las validaciones con el distrito ya
     * derivado. Con uno de los tres distritos largos del catálogo 13 esto fallaba y no se podía registrar el establecimiento.
     */
    @Test void unAnexoEnUnDistritoDeNombreLargoSeRegistra() {
        Establecimiento e = new Establecimiento(tenant, "0002", "Tienda Gregorio Albarracín", Domicilio.de("230110", "Av. Bolognesi 100"), true);
        assertThat(e.domicilio().distrito()).isEqualTo("CORONEL GREGORIO ALBARRACIN LA").hasSize(30);
        assertThat(e.domicilio().codigoEstablecimiento()).isEqualTo("0002");
    }

    @Test void codigoNombreYDomicilioSeValidan() {
        assertThatThrownBy(() -> new Establecimiento(tenant, "12", "A", dom, true)).isInstanceOf(DomainException.class).hasMessageContaining("3030");
        assertThatThrownBy(() -> new Establecimiento(tenant, "0000", "Principal", dom, true)).hasMessageContaining("domicilio fiscal");
        assertThatThrownBy(() -> new Establecimiento(tenant, "0001", " ", dom, true)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> new Establecimiento(tenant, "0001", "A\nB", dom, true)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> new Establecimiento(tenant, "0001", "A", null, true)).hasMessageContaining("domicilio");
    }

    @Test void laSerieSeAsignaAUnEstablecimiento() {
        Serie s = new Serie(tenant, pe.factura.domain.documento.TipoDocumento.FACTURA, "F002", 0, true, "0002");
        assertThat(s.establecimiento()).isEqualTo("0002");
        assertThat(s.enDomicilioFiscal()).isFalse();
        assertThat(new Serie(tenant, pe.factura.domain.documento.TipoDocumento.FACTURA, "F001", 0, true).enDomicilioFiscal()).isTrue();
        assertThatThrownBy(() -> new Serie(tenant, pe.factura.domain.documento.TipoDocumento.FACTURA, "F003", 0, true, "AB")).hasMessageContaining("3030");
    }
}
