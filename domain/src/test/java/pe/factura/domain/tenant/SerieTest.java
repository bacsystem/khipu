package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerieTest {
    UUID tenant = UUID.randomUUID();

    Serie serieCon(long ultimoNumero) {
        return new Serie(tenant, TipoDocumento.FACTURA, "F001", ultimoNumero, true);
    }

    @Test void elEstablecimientoPorDefectoEsElDomicilioFiscal() {
        Serie s = serieCon(0);
        assertThat(s.establecimiento()).isEqualTo(Domicilio.ESTABLECIMIENTO_PRINCIPAL);
        assertThat(s.enDomicilioFiscal()).isTrue();
        assertThat(new Serie(tenant, TipoDocumento.FACTURA, "F002", 0, true, " 0002 ").enDomicilioFiscal()).isFalse();
        assertThatThrownBy(() -> new Serie(tenant, TipoDocumento.FACTURA, "F003", 0, true, "12")).hasMessageContaining("3030");
    }

    /**
     * La regla 1001 define el ID como {@code [FB][A-Z0-9]{3}-[0-9]{1,8}}. Un correlativo de nueve dígitos se firma y
     * pasa el XSD, pero SUNAT lo rechaza con el número ya consumido, y no hay endpoint para corregir la serie.
     */
    @Test void elCorrelativoNoPasaDeOchoDigitos() {
        assertThat(serieCon(Serie.NUMERO_MAXIMO).ultimoNumero()).isEqualTo(99_999_999L);

        assertThatThrownBy(() -> serieCon(Serie.NUMERO_MAXIMO + 1))
                .isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("CORRELATIVO_INVALIDO");
        assertThatThrownBy(() -> serieCon(100_000_000L)).hasMessageContaining("1001");
        assertThatThrownBy(() -> serieCon(-1)).extracting("codigo").isEqualTo("CORRELATIVO_INVALIDO");
    }

    @Test void laSerieSeAgotaAlLlegarAlTope() {
        assertThat(serieCon(Serie.NUMERO_MAXIMO - 1).agotada()).isFalse();
        assertThat(serieCon(Serie.NUMERO_MAXIMO).agotada()).isTrue();
    }
}
