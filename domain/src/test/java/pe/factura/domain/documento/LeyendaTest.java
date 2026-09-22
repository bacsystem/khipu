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

/** Leyendas del catálogo 52 declaradas por el emisor (#66): 3027 y 3283–3289. */
class LeyendaTest {
    Clock reloj = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    Receptor r = new Receptor("6", "20601234565", "CLIENTE SAC", null);
    Item exonerado = new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO);
    Item gravado = new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO);

    @Test void textoOficialSinPrefijoNiComillas() {
        assertThat(Leyenda.texto("2001")).isEqualTo("BIENES TRANSFERIDOS EN LA AMAZONÍA REGIÓN SELVA PARA SER CONSUMIDOS EN LA MISMA");
        assertThat(Leyenda.texto("2005")).isEqualTo("Venta realizada por emisor itinerante");
        assertThat(Leyenda.texto("2008")).startsWith("VENTA EXONERADA DEL IGV-ISC-IPM");
        assertThat(Leyenda.texto("2010")).isEqualTo("Restitucion Simplificado de Derechos Arancelarios");
    }

    @Test void validaCatalogoYRechazaLasAutomaticas() {
        assertThat(Leyenda.validar(List.of("2001", " 2005 ", "2001"))).containsExactly("2001", "2005");
        assertThat(Leyenda.validar(null)).isEmpty();
        assertThatThrownBy(() -> Leyenda.validar(List.of("9999"))).isInstanceOf(DomainException.class).hasMessageContaining("3027");
        assertThatThrownBy(() -> Leyenda.validar(List.of("1000"))).hasMessageContaining("la genera khipu");
        assertThatThrownBy(() -> Leyenda.validar(List.of("2006"))).hasMessageContaining("la genera khipu");
    }

    @Test void lasDeAmazoniaExigenTotalExonerado() {
        Comprobante ok = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", r, List.of(exonerado)).leyendas(List.of("2001", "2005")).crear(reloj);
        assertThat(ok.leyendas()).containsExactly("2001", "2005");
        assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", r, List.of(gravado)).leyendas(List.of("2002")).crear(reloj))
                .isInstanceOf(DomainException.class).hasMessageContaining("3284").hasMessageContaining("exonerado");
        assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", r, List.of(gravado)).leyendas(List.of("2008")).crear(reloj)).hasMessageContaining("3289");
        // 2004/2005 no exigen exonerado; sin leyendas la lista queda vacía.
        assertThat(Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", r, List.of(gravado)).leyendas(List.of("2004")).crear(reloj).leyendas()).containsExactly("2004");
        assertThat(Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", r, List.of(gravado)).crear(reloj).leyendas()).isEmpty();
    }
}
