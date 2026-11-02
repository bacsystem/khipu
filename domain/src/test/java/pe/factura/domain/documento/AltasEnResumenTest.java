package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #301: un resumen diario (RC) lleva hasta 500 líneas de una misma fecha de emisión (SFS 0158). Una fila por comprobante, todas con el mismo identificador; el límite, la fecha
 * común y lo que no entra en un resumen (una factura, un comprobante que SUNAT ya tiene, uno repetido) se rechazan antes de firmar nada.
 */
class AltasEnResumenTest {
    static final Clock HOY = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Lima"));   // 2026-09-20 en Lima
    static final LocalDate EMISION = LocalDate.of(2026, 9, 13);   // 7 días atrás: ya no va sola, todavía en plazo del resumen
    static final UUID TENANT = UUID.randomUUID();
    static final Receptor COMPRADOR = new Receptor("1", "12345678", "JUAN PEREZ", null);

    static Comprobante boleta(long numero, LocalDate emision, EstadoDocumento estado) {
        return Comprobante.persistido(UUID.randomUUID(), TENANT, TipoDocumento.BOLETA, "B001", numero, emision, estado, COMPRADOR,
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "n", "k").rehidratar();
    }

    static List<Comprobante> boletas(int cuantas) {
        List<Comprobante> l = new ArrayList<>();
        for (int i = 1; i <= cuantas; i++) l.add(boleta(i, EMISION, EstadoDocumento.FIRMADO));
        return l;
    }

    @Test void variasBoletasDeUnaFechaSonUnSoloResumenConUnaFilaPorLinea() {
        List<Comprobante> lote = boletas(3);

        List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(lote, 4, HOY);

        assertThat(filas).hasSize(3);
        assertThat(filas).extracting(ComunicacionBaja::identificador).containsOnly("RC-20260920-4");
        assertThat(filas).extracting(ComunicacionBaja::linea).containsExactly(1, 2, 3);
        assertThat(filas).extracting(ComunicacionBaja::lineas).containsOnly(3);
        assertThat(filas).extracting(ComunicacionBaja::comprobanteId).containsExactlyElementsOf(lote.stream().map(Comprobante::id).toList());
        assertThat(filas).allSatisfy(f -> {
            assertThat(f.alta()).isTrue();
            assertThat(f.deVariasLineas()).isTrue();
            assertThat(f.fechaReferencia()).isEqualTo(EMISION);
            assertThat(f.estado()).isEqualTo(EstadoBaja.GENERADA);
        });
        assertThat(filas).extracting(ComunicacionBaja::id).doesNotHaveDuplicates();
    }

    @Test void unaSolaBoletaEsUnResumenDeUnaLineaComoAntes() {
        ComunicacionBaja b = ComunicacionBaja.altaEnResumen(boleta(1, EMISION, EstadoDocumento.ERROR_ENVIO), 1, HOY);

        assertThat(b.linea()).isEqualTo(1);
        assertThat(b.lineas()).isEqualTo(1);
        assertThat(b.deVariasLineas()).isFalse();
    }

    @Test void quinientasLineasEntranYQuinientasUnaNo() {
        assertThat(ComunicacionBaja.altasEnResumen(boletas(500), 1, HOY)).hasSize(500).last().extracting(ComunicacionBaja::linea).isEqualTo(500);

        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(boletas(501), 1, HOY)).isInstanceOf(DomainException.class).hasMessageContaining("0158").hasMessageContaining("500");
    }

    @Test void lasLineasDeUnResumenSonTodasDeLaMismaFechaDeEmision() {
        List<Comprobante> mezcla = List.of(boleta(1, EMISION, EstadoDocumento.FIRMADO), boleta(2, EMISION.plusDays(1), EstadoDocumento.FIRMADO));

        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(mezcla, 1, HOY)).hasMessageContaining("2375").hasMessageContaining("B001-2");
    }

    @Test void unComprobanteQueSunatYaTieneOUnaFacturaNoEntranYUnoRepetidoTampoco() {
        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(List.of(boleta(1, EMISION, EstadoDocumento.FIRMADO), boleta(2, EMISION, EstadoDocumento.ACEPTADO)), 1, HOY))
                .hasMessageContaining("B001-2").hasMessageContaining("SUNAT todavía no tiene");
        Comprobante factura = Comprobante.persistido(UUID.randomUUID(), TENANT, TipoDocumento.FACTURA, "F001", 1L, EMISION, EstadoDocumento.FIRMADO, new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "n", "k").rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(List.of(factura), 1, HOY)).hasMessageContaining("Solo una boleta");
        Comprobante repetida = boleta(1, EMISION, EstadoDocumento.FIRMADO);
        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(List.of(repetida, repetida), 1, HOY)).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(List.of(), 1, HOY)).hasMessageContaining("al menos una línea");
        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(boletas(2), 0, HOY)).hasMessageContaining("correlativo");
    }

    @Test void unaBoletaQueTodaviaVaSolaNoEntraEnElResumen() {
        Clock diaCinco = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));   // 13 + 5: el último día del envío individual

        assertThatThrownBy(() -> ComunicacionBaja.altasEnResumen(boletas(2), 1, diaCinco)).hasMessageContaining("todavía va sola");
    }

    @Test void lasFilasDeUnResumenCompartenElCicloPeroSoloLasDeEseResumen() {
        List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(boletas(3), 1, HOY);
        filas.forEach(f -> f.firmar("k.xml"));
        ComunicacionBaja primera = filas.get(0);
        primera.marcarEnviada("T-9");
        primera.aplicarCdr(new Cdr("0", "aceptado", List.of()), "R-k.zip");

        filas.stream().skip(1).forEach(f -> f.copiarCicloDe(primera));

        assertThat(filas).allSatisfy(f -> {
            assertThat(f.estado()).isEqualTo(EstadoBaja.ACEPTADA);
            assertThat(f.ticket()).isEqualTo("T-9");
            assertThat(f.cdrKey()).isEqualTo("R-k.zip");
            assertThat(f.cdr().codigo()).isEqualTo("0");
        });
        ComunicacionBaja deOtroResumen = ComunicacionBaja.altasEnResumen(boletas(1), 2, HOY).get(0);
        assertThatThrownBy(() -> deOtroResumen.copiarCicloDe(primera)).isInstanceOf(DomainException.class).hasMessageContaining("mismo resumen");
    }
}
