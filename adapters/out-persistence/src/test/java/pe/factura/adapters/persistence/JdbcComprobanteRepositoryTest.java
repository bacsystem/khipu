package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JdbcComprobanteRepositoryTest extends PersistenciaTestBase {
    JdbcComprobanteRepository repo = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    private Comprobante factura(UUID t, long numero) {
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)), clock);
        c.asignarNumero(numero, "20100066603");
        return c;
    }

    @Test void guardaYRehidrataFormaPagoAlCredito() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.credito(new BigDecimal("100.00"), List.of(
                        new FormaPago.Cuota(new BigDecimal("60.00"), LocalDate.of(2026, 10, 13)),
                        new FormaPago.Cuota(new BigDecimal("40.00"), LocalDate.of(2026, 11, 13)))), clock);
        c.asignarNumero(3, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.formaPago().esCredito()).isTrue();
        assertThat(leido.formaPago().montoPendiente()).isEqualByComparingTo("100.00");
        assertThat(leido.formaPago().cuotas()).containsExactly(
                new FormaPago.Cuota(new BigDecimal("60.00"), LocalDate.of(2026, 10, 13)),
                new FormaPago.Cuota(new BigDecimal("40.00"), LocalDate.of(2026, 11, 13)));

        // Un update posterior (estado) no toca ni duplica las cuotas.
        leido.marcarEnviado();
        repo.guardar(leido);
        assertThat(repo.buscar(t, c.id()).orElseThrow().formaPago().cuotas()).hasSize(2);
    }

    @Test void guardaYRehidrataDescuentos() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("12.5"), true)),
                        new Item("P2", "Mouse", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO, Descuento.monto(new BigDecimal("5.00"), false))),
                FormaPago.contado(), Descuento.porcentaje(new BigDecimal("2"), true), clock);
        c.asignarNumero(5, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.items().get(0).descuento()).isEqualTo(Descuento.porcentaje(new BigDecimal("12.5"), true));
        assertThat(leido.items().get(1).descuento()).isEqualTo(Descuento.monto(new BigDecimal("5"), false));
        assertThat(leido.descuentoGlobal()).isEqualTo(Descuento.porcentaje(new BigDecimal("2"), true));
        assertThat(leido.totales().total()).isEqualByComparingTo(c.totales().total());
    }

    @Test void guardaYRehidrataDetraccion() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "1001",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, new Detraccion("022", new BigDecimal("12.5"), new BigDecimal("148.00"), "00-000-123456", "003"), clock);
        c.asignarNumero(6, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.detraccion()).isEqualTo(new Detraccion("022", new BigDecimal("12.5"), new BigDecimal("148.00"), "00-000-123456", "003"));
        assertThat(leido.tipoOperacion()).isEqualTo("1001");
    }

    @Test void guardaYRehidrataRetencionYPercepcion() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "2001",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, null, new RetencionIgv(new BigDecimal("3"), null), new Percepcion("52", null, null, null), clock);
        c.asignarNumero(7, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.retencion()).isEqualTo(new RetencionIgv(new BigDecimal("3"), new BigDecimal("35.40")));
        assertThat(leido.percepcion()).isEqualTo(new Percepcion("52", new BigDecimal("1"), new BigDecimal("1180.00"), new BigDecimal("11.80")));
    }

    @Test void guardaYRehidrataIscEIcbper() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("C", "Cerveza", "NIU", BigDecimal.ONE, new BigDecimal("159.30"), TipoAfectacionIgv.GRAVADO, null, new Isc("02", null, new BigDecimal("2.25")), false),
                        new Item("B", "Bolsa", "NIU", new BigDecimal("3"), new BigDecimal("0.618"), TipoAfectacionIgv.GRAVADO, null, null, true)), clock);
        c.asignarNumero(8, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.items().get(0).isc()).isEqualTo(new Isc("02", null, new BigDecimal("2.25")));
        assertThat(leido.items().get(0).icbper()).isFalse();
        assertThat(leido.items().get(1).isc()).isNull();
        assertThat(leido.items().get(1).icbper()).isTrue();
        assertThat(leido.totales().total()).isEqualByComparingTo(c.totales().total());
    }

    @Test void guardaYRehidrataAnticiposYBuscaPorNumero() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.crearFactura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("O", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("E", "Exonerado", "NIU", BigDecimal.ONE, new BigDecimal("200.00"), TipoAfectacionIgv.EXONERADO)),
                FormaPago.contado(), null, null, null, null,
                List.of(new Anticipo("F001", 3, new BigDecimal("300.00"), null, LocalDate.of(2026, 9, 1)),
                        new Anticipo("F002", 4, new BigDecimal("50.00"), Anticipo.Afectacion.EXONERADO, null)), clock);
        c.asignarNumero(9, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 9).orElseThrow();
        assertThat(leido.id()).isEqualTo(c.id());
        assertThat(leido.anticipos()).containsExactly(
                new Anticipo("F001", 3, new BigDecimal("300.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1)),
                new Anticipo("F002", 4, new BigDecimal("50.00"), Anticipo.Afectacion.EXONERADO, null));
        assertThat(leido.totales().totalAnticipos()).isEqualByComparingTo("404.00");
        assertThat(leido.totales().total()).isEqualByComparingTo("976.00");   // 1380 − 404
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 99)).isEmpty();
        // Lo regularizado del anticipo F001-3 suma solo lo de finales no rechazadas
        assertThat(repo.montoRegularizado(t, "F001", 3)).isEqualByComparingTo("300.00");
        assertThat(repo.montoRegularizado(t, "F002", 4)).isEqualByComparingTo("50.00");
        assertThat(repo.montoRegularizado(t, "F001", 99)).isEqualByComparingTo("0");
        c.marcarEnviado(); c.rechazarPorFault("2335", "rechazado");
        repo.guardar(c);
        assertThat(repo.montoRegularizado(t, "F001", 3)).isEqualByComparingTo("0");
    }

    @Test void guardaYRehidrataCompleto() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 1);
        c.firmar("HASH==", t + "/2026/09/20100066603-01-F001-1.xml");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(r.numero()).isEqualTo(1L);
        assertThat(r.hash()).isEqualTo("HASH==");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(1).afectacion()).isEqualTo(TipoAfectacionIgv.EXONERADO);
        assertThat(r.totales().total()).isEqualByComparingTo("286.00");
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs");
        assertThat(r.cdrKey()).isEqualTo("k/cdr.zip");
        assertThat(r.receptor().razonSocial()).isEqualTo("CLIENTE SAC");
    }

    @Test void existeYUnicidad() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 5); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 5)).isPresent();
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 6)).isEmpty();
        Comprobante dup = factura(t, 5); dup.firmar("h", "k");
        assertThatThrownBy(() -> repo.guardar(dup)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void aislamientoPorTenant() {
        UUID t1 = tenantDePrueba(), t2 = tenantDePrueba();
        Comprobante c = factura(t1, 1); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscar(t2, c.id())).isEmpty();
        assertThat(repo.listar(t1, null, 1, 10)).hasSize(1);
        assertThat(repo.listar(t2, null, 1, 10)).isEmpty();
    }

    @Test void observacionesConSaltosDeLineaSobrevivenAlRoundTrip() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 7);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs\ncon salto", "otra\tcon tab")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs\ncon salto", "otra\tcon tab");
    }

    @Test void unEnvioTardioNoPisaUnEstadoTerminal() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 9);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/cdr.zip");
        repo.guardar(c);

        // Copia rehidratada "vieja" (leída antes de que otra transacción persistiera ACEPTADO) que falla al enviar
        Comprobante tardio = Comprobante.rehidratar(c.id(), t, TipoDocumento.FACTURA, "F001", 9L, LocalDate.of(2026, 9, 13), "PEN", "0101",
                c.receptor(), c.items(), FormaPago.contado(), null, null, null, null, List.of(), EstadoDocumento.ERROR_ENVIO, "h", c.nombreArchivo(), "k", null, null, 1, "0109 - timeout");
        assertThatThrownBy(() -> repo.guardar(tardio))
                .isInstanceOf(pe.factura.domain.DomainException.class).extracting("codigo").isEqualTo("ESTADO_CONFLICTO");
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE id = ?", Integer.class, c.id())).isEqualTo(1);

        Comprobante enviadoTardio = Comprobante.rehidratar(c.id(), t, TipoDocumento.FACTURA, "F001", 9L, LocalDate.of(2026, 9, 13), "PEN", "0101",
                c.receptor(), c.items(), FormaPago.contado(), null, null, null, null, List.of(), EstadoDocumento.ENVIADO, "h", c.nombreArchivo(), "k", null, null, 1, null);
        assertThatThrownBy(() -> repo.guardar(enviadoTardio)).extracting("codigo").isEqualTo("ESTADO_CONFLICTO");
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void errorEnvioSobreFirmadoOErrorEnvioSiSeGuarda() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 11);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarErrorEnvio("0109 - timeout");
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        c.marcarErrorEnvio("0109 - timeout otra vez");
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().intentos()).isEqualTo(2);
        c.marcarEnviado();
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }
}
