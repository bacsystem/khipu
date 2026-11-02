package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TotpRfc6238Test {
    /** El secreto ASCII «12345678901234567890» de los vectores de prueba del RFC 6238 (apéndice B), en Base32. */
    static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    final TotpRfc6238 totp = new TotpRfc6238();

    /** RFC 6238, apéndice B, SHA-1: los 8 dígitos del RFC, de los que la app muestra los 6 de la derecha. */
    @Test void coincideConLosVectoresDelRfc() {
        verifica(59L, "287082");
        verifica(1111111109L, "081804");
        verifica(1111111111L, "050471");
        verifica(1234567890L, "005924");
        verifica(2000000000L, "279037");
    }

    private void verifica(long segundos, String codigo) {
        assertThat(totp.paso(SECRETO_RFC, codigo, Instant.ofEpochSecond(segundos))).as("T=%d", segundos).hasValue(segundos / 30);
    }

    /** El reloj del teléfono puede ir un poco adelantado o atrasado: se acepta un paso (30 s) hacia cada lado, no más. */
    @Test void toleraUnPasoDeRelojHaciaCadaLado() {
        Instant t = Instant.ofEpochSecond(1111111111L);
        long paso = 1111111111L / 30;
        assertThat(totp.paso(SECRETO_RFC, "050471", t.plusSeconds(30))).hasValue(paso);
        assertThat(totp.paso(SECRETO_RFC, "050471", t.minusSeconds(30))).hasValue(paso);
        assertThat(totp.paso(SECRETO_RFC, "050471", t.plusSeconds(60))).isEmpty();
        assertThat(totp.paso(SECRETO_RFC, "050471", t.minusSeconds(60))).isEmpty();
    }

    @Test void unCodigoEquivocadoOMalFormadoNoCoincide() {
        Instant t = Instant.ofEpochSecond(59L);
        for (String malo : new String[]{"287083", "28708", "2870820", "abcdef", "", " 287082"})
            assertThat(totp.paso(SECRETO_RFC, malo, t)).as("«%s»", malo).isEmpty();
        assertThat(totp.paso(SECRETO_RFC, null, t)).isEmpty();
        assertThat(totp.paso("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJR", "287082", t)).as("otro secreto").isEmpty();
    }

    @Test void elSecretoSeLeeSinImportarMayusculasNiEspacios() {
        assertThat(totp.paso("gezd gnbv gy3t qojq gezd gnbv gy3t qojq", "287082", Instant.ofEpochSecond(59L))).hasValue(1L);
    }

    @Test void cadaSecretoNuevoEsBase32De160BitsYDistinto() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String s = totp.nuevoSecreto();
            assertThat(s).matches("[A-Z2-7]{32}");
            vistos.add(s);
        }
        assertThat(vistos).hasSize(50);
    }

    /** El formato que leen Google Authenticator, Authy, 1Password y compañía (Key Uri Format). */
    @Test void laUriLlevaEmisorCuentaYParametros() {
        assertThat(totp.uri(SECRETO_RFC, "ana@khipu.pe"))
                .isEqualTo("otpauth://totp/khipu:ana%40khipu.pe?secret=" + SECRETO_RFC + "&issuer=khipu&algorithm=SHA1&digits=6&period=30");
    }

    @Test void unSecretoNuevoProduceCodigosQueElMismoVerificador() {
        String s = totp.nuevoSecreto();
        Instant ahora = Instant.parse("2026-10-03T15:00:10Z");
        String codigo = totp.codigo(s, ahora.getEpochSecond() / 30);
        assertThat(totp.paso(s, codigo, ahora)).hasValue(ahora.getEpochSecond() / 30);
    }
}
