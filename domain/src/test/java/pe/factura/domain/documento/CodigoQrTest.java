package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoQrTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Comprobante factura() {
        return Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), CLOCK);
    }

    @Test void diezCamposSeparadosPorBarraConHashAlFinal() {
        Comprobante c = factura();
        c.asignarNumero(125, "20100066603");
        c.firmar("y4M8+jW8Xp278K1aM02q19KjvO3k=", "k");
        assertThat(CodigoQr.contenido(c, "20100066603"))
                .isEqualTo("20100066603|01|F001|125|18.00|118.00|2026-09-13|6|20601234565|y4M8+jW8Xp278K1aM02q19KjvO3k=|");
    }

    @Test void sinNumeroNiFirmaNoHayQr() {
        assertThatThrownBy(() -> CodigoQr.contenido(factura(), "20100066603"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SIN_FIRMA");
    }
}
