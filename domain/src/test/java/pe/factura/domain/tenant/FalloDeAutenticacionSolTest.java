package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuándo el último error de un envío dice que SUNAT no acepta las credenciales SOL (#197). Un error de envío guarda «código - mensaje»; el 401 de la autenticación HTTP
 * llega como «0000 - SUNAT respondió HTTP 401 …». Solo esos son un problema de credenciales: un servicio caído o un timeout no lo son.
 */
class FalloDeAutenticacionSolTest {
    @Test void loscodigosDeUsuarioYClaveDeSunatSonUnFalloDeCredenciales() {
        for (String codigo : new String[]{"0102", "0103", "0104", "0105", "0106", "0111"})
            assertThat(FalloDeAutenticacionSol.esUno(codigo + " - Usuario o contraseña incorrectos")).as(codigo).isTrue();
    }

    @Test void unHttp401TambienLoEs() {
        assertThat(FalloDeAutenticacionSol.esUno("0000 - SUNAT respondió HTTP 401 en 2 intentos (revisar credenciales SOL/URL)")).isTrue();
    }

    @Test void unServicioCaidoOUnTimeoutNoSonUnFalloDeCredenciales() {
        for (String error : new String[]{"0109 - El sistema no puede responder su solicitud", "0100 - Servicio no disponible", "0000 - SUNAT respondió HTTP 503", "0000 - SUNAT respondió HTTP 500",
                "0000 - SUNAT respondió HTTP 400 (revisar credenciales/URL)", "INFRA - storage no disponible", "Read timed out", "0127 - El ticket no existe"})
            assertThat(FalloDeAutenticacionSol.esUno(error)).as(error).isFalse();
    }

    @Test void elCodigoTieneQueIrAlPrincipioYSeguidoDeSuGuion() {
        assertThat(FalloDeAutenticacionSol.esUno("El error 0102 apareció en el log")).isFalse();
        assertThat(FalloDeAutenticacionSol.esUno("01020 - otro")).isFalse();
        assertThat(FalloDeAutenticacionSol.esUno("0102")).isFalse();
        assertThat(FalloDeAutenticacionSol.esUno(" 0102 - con espacio")).isFalse();
    }

    @Test void sinTextoNoHayFallo() {
        assertThat(FalloDeAutenticacionSol.esUno(null)).isFalse();
        assertThat(FalloDeAutenticacionSol.esUno("")).isFalse();
    }

    @Test void losCodigosSonCuatroDigitosDeLaFamiliaDeAutenticacion() {
        assertThat(FalloDeAutenticacionSol.CODIGOS).allSatisfy(c -> assertThat(c).matches("01\\d\\d"));
        assertThat(FalloDeAutenticacionSol.CODIGOS).doesNotHaveDuplicates();
    }
}
