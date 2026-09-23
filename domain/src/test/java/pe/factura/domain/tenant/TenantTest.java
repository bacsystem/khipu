package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Datos del emisor que viajan al XML sin pasar por ninguna otra validación: RUC (n11), razón social (1037/4338, hoja
 * `Factura2_0` filas 49-50), nombre comercial (4092) y cuenta de detracciones (3034). La certificación del alta encontró
 * que la razón social solo comprobaba «no vacía», así que una pegada desde una planilla —con tabulador— se guardaba y
 * SUNAT rechazaba después TODOS los comprobantes de esa empresa, sin forma de corregirla.
 */
class TenantTest {
    private static Tenant conRazonSocial(String razonSocial) {
        return new Tenant(UUID.randomUUID(), "20100066603", razonSocial, Entorno.BETA, null, null);
    }

    @Test void laRazonSocialNoAdmiteTabuladoresNiSaltosDeLinea() {
        for (String crudo : new String[]{"ACME SAC\tEIRL", "ACME\nSAC", "ACME\r\nSAC", "ACME\u000bSAC"}) {
            assertThatThrownBy(() -> conRazonSocial(crudo))
                    .isInstanceOf(DomainException.class).hasMessageContaining("4338");
        }
    }

    @Test void laRazonSocialAdmiteHasta1500CaracteresYSeRecorta() {
        // Sin mínimo de 3: esa regla es del receptor (2022). Para el emisor SUNAT solo fija `an..1500` (filas 49-50).
        assertThatThrownBy(() -> conRazonSocial("a".repeat(1501))).hasMessageContaining("4338");
        assertThatThrownBy(() -> conRazonSocial("   ")).hasMessageContaining("Razón social requerida");
        assertThatThrownBy(() -> conRazonSocial(null)).hasMessageContaining("Razón social requerida");
        // Los espacios de los extremos se quitan: es lo que va al XML.
        assertThat(conRazonSocial("  Comercial Andina SAC  ").razonSocial()).isEqualTo("Comercial Andina SAC");
        assertThat(conRazonSocial("a".repeat(1500)).razonSocial()).hasSize(1500);
    }

    @Test void elRucDelEmisorSonOnceDigitos() {
        assertThatThrownBy(() -> new Tenant(UUID.randomUUID(), "2010006660", "Comercial Andina SAC", Entorno.BETA, null, null))
                .hasMessageContaining("RUC inválido");
        assertThatThrownBy(() -> new Tenant(UUID.randomUUID(), "2010006660X", "Comercial Andina SAC", Entorno.BETA, null, null))
                .hasMessageContaining("RUC inválido");
    }

    @Test void elNombreComercialSigueLaMismaRegla_4092() {
        assertThatThrownBy(() -> new Tenant(UUID.randomUUID(), "20100066603", "Comercial Andina SAC", Entorno.BETA, null, null, null, null, "ACME\tSAC"))
                .hasMessageContaining("4092");
        assertThat(new Tenant(UUID.randomUUID(), "20100066603", "Comercial Andina SAC", Entorno.BETA, null, null, null, null, "  ACME  ").nombreComercial()).isEqualTo("ACME");
    }

    @Test void laCuentaDeDetraccionesSonDigitosYGuiones_3034() {
        assertThatThrownBy(() -> new Tenant(UUID.randomUUID(), "20100066603", "Comercial Andina SAC", Entorno.BETA, null, null, null, "cuenta mala", null))
                .hasMessageContaining("3034");
        assertThat(new Tenant(UUID.randomUUID(), "20100066603", "Comercial Andina SAC", Entorno.BETA, null, null, null, "00-000-123456", null).cuentaDetracciones())
                .isEqualTo("00-000-123456");
    }
}
