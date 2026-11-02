package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemitenteDeCorreoTest {

    private static void rechaza(String nombre, String email, String responderA, String mensaje) {
        assertThatThrownBy(() -> RemitenteDeCorreo.de(nombre, email, responderA)).isInstanceOf(DomainException.class).hasMessageContaining(mensaje).extracting("codigo").isEqualTo("REMITENTE_INVALIDO");
    }

    @Test void unRemitenteCompletoSeGuardaTalCual() {
        RemitenteDeCorreo r = RemitenteDeCorreo.de("khipu", "no-responder@khipu.pe", "soporte@khipu.pe");

        assertThat(r).isEqualTo(new RemitenteDeCorreo("khipu", "no-responder@khipu.pe", "soporte@khipu.pe"));
    }

    @Test void soloElCorreoEsObligatorio() {
        RemitenteDeCorreo r = RemitenteDeCorreo.de(null, "no-responder@khipu.pe", null);

        assertThat(r.nombre()).isNull();
        assertThat(r.responderA()).isNull();
    }

    @Test void losEspaciosSeQuitanYLoVacioEsNada() {
        RemitenteDeCorreo r = RemitenteDeCorreo.de("  khipu  ", "  no-responder@khipu.pe ", "   ");

        assertThat(r).isEqualTo(new RemitenteDeCorreo("khipu", "no-responder@khipu.pe", null));
        assertThat(RemitenteDeCorreo.de("", "a@khipu.pe", "").nombre()).isNull();
    }

    @Test void sinCorreoNoHayRemitente() {
        rechaza("khipu", null, null, "obligatorio");
        rechaza("khipu", "   ", null, "obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba.pe", "a@", "@khipu.pe", "a@khipu", "a b@khipu.pe", "a@khipu.p", "a@@khipu.pe", "a@khipu..pe", "a..b@khipu.pe", "<a@khipu.pe>", "a@khipu.pe, b@khipu.pe",
            "a@khipu.pe;b@khipu.pe", "Khipu <a@khipu.pe>", "áé@khipu.pe", "a@khípu.pe", "a@khipu.pe\nBcc: x@y.pe", "a@khipu.pe\r\nBcc: x@y.pe", "\"a\"@khipu.pe", "a@-.pe", "a@-khipu.pe", "a@khipu-.pe"})
    void unCorreoQueNoEsUnaSolaDireccionSimpleSeRechaza(String malo) {
        rechaza(null, malo, null, "correo del remitente no es una dirección");
        rechaza(null, "ok@khipu.pe", malo, "correo para las respuestas no es una dirección");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@khipu.pe", "no-responder@khipu.pe", "a.b+c_d%e@sub.dominio.com.pe", "A@KHIPU.PE", "x1@khipu-dev.pe"})
    void unaDireccionSimpleEnAsciiSeAcepta(String bueno) {
        assertThat(RemitenteDeCorreo.de(null, bueno, null).email()).isEqualTo(bueno);
    }

    @Test void laDireccionTieneTopes() {
        assertThat(RemitenteDeCorreo.de(null, "a".repeat(64) + "@khipu.pe", null)).isNotNull();
        rechaza(null, "a".repeat(65) + "@khipu.pe", null, "no es una dirección");
        rechaza(null, "a@" + "b".repeat(250) + ".pe", null, "no es una dirección");
    }

    /** 254 caracteres es lo más largo que admite una dirección: ni uno más. */
    @Test void elTopeDeLaDireccionEsDe254Caracteres() {
        String dominio = ("b".repeat(63) + ".").repeat(2);
        String de254 = "a".repeat(64) + "@" + dominio + "c".repeat(58) + ".pe";
        String de255 = "a".repeat(64) + "@" + dominio + "c".repeat(59) + ".pe";

        assertThat(de254).hasSize(RemitenteDeCorreo.MAX_EMAIL);
        assertThat(RemitenteDeCorreo.de(null, de254, null).email()).isEqualTo(de254);
        rechaza(null, de255, null, "no es una dirección");
    }

    @Test void elNombreTieneUnTopeYNoLlevaControlesNiCaracteresDeCabecera() {
        assertThat(RemitenteDeCorreo.de("n".repeat(RemitenteDeCorreo.MAX_NOMBRE), "a@khipu.pe", null).nombre()).hasSize(RemitenteDeCorreo.MAX_NOMBRE);
        rechaza("n".repeat(RemitenteDeCorreo.MAX_NOMBRE + 1), "a@khipu.pe", null, "hasta 100 caracteres");
        for (String malo : new String[]{"khipu\nBcc: x@y.pe", "khi\rpu", "khi\u0000pu", "khipu <x@y.pe>", "\"khipu\"", "khi\\pu", "khipu>"})
            rechaza(malo, "a@khipu.pe", null, "nombre no puede llevar");
    }

    @Test void elTopeDelNombreSeCuentaDespuesDeRecortar() {
        assertThat(RemitenteDeCorreo.de("  " + "n".repeat(RemitenteDeCorreo.MAX_NOMBRE) + "  ", "a@khipu.pe", null).nombre()).hasSize(RemitenteDeCorreo.MAX_NOMBRE);
    }

    @Test void elNombreAceptaTildesYEspacios() {
        assertThat(RemitenteDeCorreo.de("Facturación Perú · khipu", "a@khipu.pe", null).nombre()).isEqualTo("Facturación Perú · khipu");
    }
}
