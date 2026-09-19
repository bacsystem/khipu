package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ComprobanteTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final Receptor empresa = new Receptor("6", "20601234567", "CLIENTE SAC", "AV. LIMA 1");
    private final List<Item> items = List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO));

    @Test void facturaValidaNaceRecibidaConTotales() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECIBIDO);
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        assertThat(c.numero()).isNull();
    }

    @Test void facturaExigeRucDelReceptor() {
        Receptor dni = new Receptor("1", "12345678", "JUAN PEREZ", null);
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", dni, items, clock))
                .isInstanceOf(DomainException.class).hasMessageContaining("RUC");
    }

    @Test void serieDebeCorresponderAlTipo() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    /** Regla 3206: el tipo de operación debe existir en el catálogo 51 y aplicar a facturas. */
    @Test void tipoDeOperacionContraElCatalogo51() {
        assertThat(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", null, empresa, items, clock).tipoOperacion()).isEqualTo("0101");
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "9999", empresa, items, clock))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("3206").extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
        // 0113 (Venta interna - NRUS) solo aplica a boletas
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0113", empresa, items, clock))
                .hasMessageContaining("no aplica a facturas");
    }

    @Test void fechaNoPuedeSerFutura() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 14), "PEN", "0101", empresa, items, clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("FECHA_INVALIDA");
    }

    @Test void sinItemsFalla() {
        assertThatThrownBy(() -> Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, List.of(), clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SIN_ITEMS");
    }

    @Test void lasObservacionesSoloAdmitenSaltosDeLinea() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        c.anotar("  Entrega en almacén central.\nHorario: 9 a 18 h.  ");
        assertThat(c.observaciones()).isEqualTo("Entrega en almacén central.\nHorario: 9 a 18 h.");
        c.anotar(" ");
        assertThat(c.observaciones()).isNull();
        assertThatThrownBy(() -> c.anotar("con\ttab")).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("OBSERVACIONES_INVALIDAS");
        assertThatThrownBy(() -> c.anotar("x".repeat(1001))).hasMessageContaining("1000");
    }

    @Test void cicloDeVidaHastaAceptado() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        c.asignarNumero(7, "20100066603");
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-01-F001-7");
        c.firmar("abc123", "t/2026/09/20100066603-01-F001-7.xml");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "La Factura numero F001-7, ha sido aceptada", List.of()), "t/2026/09/R-20100066603-01-F001-7.xml");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void cdrConObservacionesYRechazo() {
        Comprobante a = firmado(); a.marcarEnviado();
        a.aplicarCdr(new Cdr("0", "ok", List.of("4252 - dato observado")), "k");
        assertThat(a.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);

        Comprobante r = firmado(); r.marcarEnviado();
        r.aplicarCdr(new Cdr("2324", "El comprobante fue registrado previamente con otros datos", List.of()), "k");
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void errorEnvioCuentaIntentosYPermiteReenviar() {
        Comprobante c = firmado();
        c.marcarErrorEnvio("timeout");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(c.intentos()).isEqualTo(1);
        assertThat(c.estado().esEnviable()).isTrue();
        c.marcarEnviado();
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }

    @Test void faultDefinitivoRechaza() {
        Comprobante c = firmado();
        c.rechazarPorFault("2324", "registrado previamente");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(c.cdr().codigo()).isEqualTo("2324");
    }

    @Test void faultDefinitivoDesdeErrorEnvioRechaza() {
        Comprobante c = firmado();
        c.marcarErrorEnvio("x");
        c.rechazarPorFault("2324", "dup");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void transicionInvalidaLanza() {
        Comprobante c = firmado();
        assertThatThrownBy(() -> c.aplicarCdr(new Cdr("0", "x", List.of()), "k"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TRANSICION_INVALIDA");
    }

    private Comprobante firmado() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", empresa, items, clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        return c;
    }
}
