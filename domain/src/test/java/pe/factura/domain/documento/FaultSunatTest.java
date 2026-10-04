package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El fault de SUNAT que muestra la cola de errores (#196): su código y su mensaje, de donde cada clase los guarda. */
class FaultSunatTest {
    @Test void deUnRechazoSaleDelCdr() {
        FaultSunat f = FaultSunat.de("1033 - ya registrado (ignorado)", "1033", "El comprobante fue registrado previamente");

        assertThat(f.codigo()).isEqualTo("1033");
        assertThat(f.mensaje()).isEqualTo("El comprobante fue registrado previamente");
    }

    @Test void deUnErrorDeEnvioSaleDelUltimoError() {
        FaultSunat f = FaultSunat.de("0109 - El sistema no puede responder su solicitud", null, null);

        assertThat(f.codigo()).isEqualTo("0109");
        assertThat(f.mensaje()).isEqualTo("El sistema no puede responder su solicitud");
    }

    @Test void deUnFueraDePlazoSaleDelUltimoError() {
        FaultSunat f = FaultSunat.de("2108 - Presentación fuera de fecha: el plazo venció el 2026-09-13", null, null);

        assertThat(f.codigo()).isEqualTo("2108");
        assertThat(f.mensaje()).isEqualTo("Presentación fuera de fecha: el plazo venció el 2026-09-13");
    }

    @Test void unFalloDeInfraestructuraNoTieneCodigoDeSunat() {
        FaultSunat f = FaultSunat.de("INFRA - storage no disponible", null, null);

        assertThat(f.codigo()).isNull();
        assertThat(f.mensaje()).isEqualTo("INFRA - storage no disponible");
    }

    @Test void unTextoSinCodigoSeMuestraEntero() {
        assertThat(FaultSunat.de("timeout", null, null)).isEqualTo(new FaultSunat(null, "timeout"));
        assertThat(FaultSunat.de("12345 - cinco dígitos", null, null)).isEqualTo(new FaultSunat(null, "12345 - cinco dígitos"));
        assertThat(FaultSunat.de("123 - tres dígitos", null, null)).isEqualTo(new FaultSunat(null, "123 - tres dígitos"));
    }

    @Test void sinNingunDatoNoHayFault() {
        assertThat(FaultSunat.de(null, null, null)).isNull();
        assertThat(FaultSunat.de("", null, null)).isNull();
        assertThat(FaultSunat.de("   ", null, "  ")).isNull();
    }

    @Test void elCdrManda_sobreElUltimoError() {
        FaultSunat f = FaultSunat.de("0000 - HTTP 503", "1001", "Serie inválida");

        assertThat(f).isEqualTo(new FaultSunat("1001", "Serie inválida"));
    }

    @Test void unCdrSinDescripcionDejaSoloElCodigo() {
        assertThat(FaultSunat.de(null, "1001", null)).isEqualTo(new FaultSunat("1001", null));
    }
}
