package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El envío automático del comprobante aceptado a su cliente (#325): cuándo corresponde, los reintentos y el correo que viaja con el comprobante. */
class EnvioAlClienteTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static EventoDocumento evento(EstadoDocumento de, EstadoDocumento a) { return EventoDocumento.pendiente(de, a, null); }

    @Test void correspondeAlPasarAAceptadoOAceptadoConObservaciones() {
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.ENVIADO, EstadoDocumento.ACEPTADO))).isTrue();
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.ENVIADO, EstadoDocumento.ACEPTADO_CON_OBS))).isTrue();
    }

    @Test void noCorrespondeEnLosDemasCambios() {
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.FIRMADO, EstadoDocumento.ENVIADO))).isFalse();
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.ENVIADO, EstadoDocumento.RECHAZADO))).isFalse();
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.ACEPTADO, EstadoDocumento.ANULADO))).as("una baja no se le manda al cliente").isFalse();
        assertThat(EnvioAlCliente.correspondeA(evento(EstadoDocumento.ACEPTADO, EstadoDocumento.ACEPTADO))).as("seguir aceptado no es pasar a aceptado").isFalse();
    }

    @Test void losReintentosEsperanCadaVezMasYElQuintoEsElUltimo() {
        Instant ahora = Instant.parse("2026-09-13T15:00:00Z");
        assertThat(EnvioAlCliente.siguienteIntento(1, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(1)));
        assertThat(EnvioAlCliente.siguienteIntento(2, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(5)));
        assertThat(EnvioAlCliente.siguienteIntento(3, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(30)));
        assertThat(EnvioAlCliente.siguienteIntento(4, ahora)).isEqualTo(ahora.plus(Duration.ofHours(2)));
        assertThat(EnvioAlCliente.siguienteIntento(EnvioAlCliente.MAXIMO_INTENTOS, ahora)).as("sin más reintentos: queda FALLIDO").isNull();
    }

    @Test void elCorreoDelClienteSeNormalizaYEnBlancoEsAusencia() {
        assertThat(CorreoDelCliente.normalizar("  Compras@Andina.PE ")).isEqualTo("compras@andina.pe");
        assertThat(CorreoDelCliente.normalizar("   ")).isNull();
        assertThat(CorreoDelCliente.normalizar(null)).isNull();
    }

    @Test void unCorreoMalFormadoODemasiadoLargoSeRechazaComoReceptorInvalido() {
        assertThatThrownBy(() -> CorreoDelCliente.normalizar("compras@andina")).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        assertThatThrownBy(() -> CorreoDelCliente.normalizar("dos @espacios.pe")).isInstanceOf(DomainException.class);
        String largo = "a".repeat(ClienteGuardado.EMAIL_MAXIMO) + "@x.pe";
        assertThatThrownBy(() -> CorreoDelCliente.normalizar(largo)).isInstanceOf(DomainException.class).hasMessageContaining("…").hasMessageContaining("hasta 150");
    }

    @Test void elComprobanteLlevaElCorreoNormalizadoYSeRehidrataTalCual() {
        Comprobante c = Comprobante.factura(java.util.UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        assertThat(c.correoCliente()).isNull();

        c.dirigirA(" Pagos@Cliente.pe");
        assertThat(c.correoCliente()).isEqualTo("pagos@cliente.pe");
        c.dirigirA("");
        assertThat(c.correoCliente()).as("en blanco lo quita").isNull();

        Comprobante guardado = Comprobante.persistido(c.id(), c.tenantId(), c.tipo(), c.serie(), 1L, c.fechaEmision(), EstadoDocumento.FIRMADO, c.receptor(), c.items())
                .correoCliente("viejo@cliente.pe").rehidratar();
        assertThat(guardado.correoCliente()).isEqualTo("viejo@cliente.pe");
    }
}
