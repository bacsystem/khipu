package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.UblGenerator;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notas de crédito/débito: la factura modificada debe ser de la empresa y estar aceptada; límites 3286/3503; nota total vs parcial; NC 13. */
class EmitirNotaServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Series series = new Fakes.Series();
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos(series);
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    UblGenerator ubl = new Fakes.Ubl();
    TipoDocumento[] validado = new TipoDocumento[1];
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) { validado[0] = tipo; }
        public void validarBaja(String xml) {}
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml, "HASH");
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_CREDITO, "FC01", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_DEBITO, "FD01", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos);
    }

    /** Factura de 2 laptops (200 + 36) y un libro exonerado (50), descuento global 03 de 10: total 276.00. */
    private Comprobante facturaAceptada(FormaPago fp) {
        return service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                fp, Descuento.monto(new BigDecimal("10.00"), false), List.of(), null, null, null, List.of(), null, null, true));
    }

    private EmitirNotaCommand nc(long numeroFactura, String motivo, List<Item> items) {
        return new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", numeroFactura, motivo, "Sustento", items, null, List.of(), null, true);
    }

    @Test void notaTotalCopiaItemsDescuentoYCargosDeLaFactura() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThat(f.totales().total()).isEqualByComparingTo("276.00");

        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "01", null));
        assertThat(nc.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(nc.serie()).isEqualTo("FC01");
        assertThat(nc.numero()).isEqualTo(1L);
        assertThat(nc.nombreArchivo()).isEqualTo("20100066603-07-FC01-1");
        assertThat(nc.items()).isEqualTo(f.items());
        assertThat(nc.descuentoGlobal()).isEqualTo(f.descuentoGlobal());
        assertThat(nc.receptor()).isEqualTo(f.receptor());
        assertThat(nc.moneda()).isEqualTo("PEN");
        assertThat(nc.totales().total()).isEqualByComparingTo("276.00");
        assertThat(nc.nota().documentoAfectado()).isEqualTo("F001-" + f.numero());
        assertThat(nc.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(validado[0]).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(new String(storage.leer(nc.xmlKey()))).startsWith("<NOTA_CREDITO>");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).extracting(Comprobante::id).containsExactly(nc.id());
    }

    @Test void notaParcialSoloConLosItemsIndicados() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("P1", "Laptop devuelta", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))));
        assertThat(nc.totales().total()).isEqualByComparingTo("118.00");
        assertThat(nc.descuentoGlobal()).isNull();
    }

    @Test void laNotaDeCreditoNoPuedeSuperarALaFactura() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        // Total mayor (3286)
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("3"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("importe total");
        // Total menor pero base exonerada mayor que la de la factura (3503): 60 exonerado vs 50
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("60.00"), TipoAfectacionIgv.EXONERADO)))))
                .hasMessageContaining("3503").hasMessageContaining("exonerado");
        // Tolerancia ±1: 51 exonerado pasa
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("51.00"), TipoAfectacionIgv.EXONERADO)))).totales().total()).isEqualByComparingTo("51.00");
    }

    /**
     * SUNAT compara cada NC contra la factura (3286/3503), nunca el acumulado: dos NC totales sobre la misma factura pasan
     * en e-beta (reproducido el 2026-09-19, #83). khipu sí lleva la cuenta para que el cliente no acredite dos veces.
     */
    @Test void elAcumuladoDeNotasDeCreditoNoPuedeSuperarALaFactura() {
        Comprobante f = facturaAceptada(FormaPago.contado());   // 276.00: 200 gravado + 36 IGV + 50 exonerado − 10
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "01", null)).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        // Segunda NC total: la factura ya está acreditada por completo.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("ya acreditado 276.00");
        // Ni siquiera una parcial chica: ya no queda saldo.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("11.80"), TipoAfectacionIgv.GRAVADO)))))
                .hasMessageContaining("3286").hasMessageContaining("(11.80)").hasMessageContaining("ya acreditado 276.00");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).hasSize(1);
    }

    @Test void variasNotasParcialesPuedenSumarLaFacturaPeroNoSuperarla() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        Item unaLaptop = new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO);
        service.emitirNota(tenantId, nc(f.numero(), "07", List.of(unaLaptop)));   // gravado 100 de 200
        service.emitirNota(tenantId, nc(f.numero(), "07", List.of(unaLaptop)));   // gravado 200 de 200
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "07", List.of(unaLaptop))))
                .hasMessageContaining("3286").hasMessageContaining("ya acreditado 236.00");
        // El exonerado sigue disponible (40 de 50 acreditando 236 + 40 = 276), pero 50 ya excede el total.
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("40.00"), TipoAfectacionIgv.EXONERADO)))).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("5.00"), TipoAfectacionIgv.EXONERADO)))))
                .hasMessageContaining("3286").hasMessageContaining("ya acreditado 276.00");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).hasSize(3);
    }

    @Test void lasNotasRechazadasNoCuentanComoAcreditadoPeroLasPendientesSi() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        gateway.falla = new pe.factura.application.port.out.SunatRechazoException("2324", "rechazada");
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "01", null)).estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        gateway.falla = new pe.factura.application.port.out.SunatTransientException("0000", "caído");
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "01", null)).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        gateway.falla = null;
        // La rechazada no acreditó nada; la que está en reintento sí cuenta (SUNAT la aceptará), así que no cabe otra total.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null))).hasMessageContaining("ya acreditado 276.00");
        // Una ND no consume el saldo de crédito.
        Comprobante g = facturaAceptada(FormaPago.contado());
        service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_DEBITO, "FD01", null, LocalDate.of(2026, 9, 13), "F001", g.numero(), "01", "Intereses",
                List.of(new Item("I", "Interés", "ZZ", BigDecimal.ONE, new BigDecimal("590.00"), TipoAfectacionIgv.GRAVADO)), null, List.of(), null, true));
        assertThat(service.emitirNota(tenantId, nc(g.numero(), "01", null)).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    /** Empresa del padrón de tasa especial: la factura sale al 10.5 % y la nota hereda esa tasa aunque la empresa ya no esté en el padrón (#84). */
    @Test void laNotaHeredaLaTasaDeIgvDeLaFactura() {
        tenants.guardar(Fakes.tenantListo(tenantId).conDatosFiscales(null, null, null, true));
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Menú", "NIU", BigDecimal.ONE, new BigDecimal("110.50"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        assertThat(f.tasaIgv()).isEqualByComparingTo("10.50");
        assertThat(f.totales().igv()).isEqualByComparingTo("10.50");
        assertThat(f.totales().items().get(0).porcentajeIgv()).isEqualByComparingTo("10.50");

        tenants.guardar(Fakes.tenantListo(tenantId));   // sale del padrón
        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "01", null));
        assertThat(nc.tasaIgv()).isEqualByComparingTo("10.50");
        assertThat(nc.totales().igv()).isEqualByComparingTo("10.50");
        assertThat(nc.totales().total()).isEqualByComparingTo("110.50");
        // Una factura nueva de la misma empresa, ya fuera del padrón, vuelve al 18 %.
        assertThat(facturaAceptada(FormaPago.contado()).tasaIgv()).isEqualByComparingTo("18.00");
    }

    @Test void laNotaDeDebitoPuedeSuperarALaFactura() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        Comprobante nd = service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_DEBITO, "FD01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "Intereses",
                List.of(new Item("I", "Intereses por mora", "ZZ", BigDecimal.ONE, new BigDecimal("590.00"), TipoAfectacionIgv.GRAVADO)), null, List.of(), null, true));
        assertThat(nd.totales().total()).isEqualByComparingTo("590.00");
        assertThat(nd.nombreArchivo()).isEqualTo("20100066603-08-FD01-1");
        assertThat(validado[0]).isEqualTo(TipoDocumento.NOTA_DEBITO);
    }

    @Test void facturaInexistenteNoAceptadaOAnuladaSeRechaza() {
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(99, "01", null))).hasMessageContaining("2119").hasMessageContaining("no existe");
        gateway.falla = new pe.factura.application.port.out.SunatTransientException("0000", "caído");
        Comprobante enError = facturaAceptada(FormaPago.contado());
        assertThat(enError.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(enError.numero(), "01", null))).hasMessageContaining("2119").hasMessageContaining("ERROR_ENVIO");
        gateway.falla = null;
        // Fecha anterior a la factura (2885)
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 1), "F001", f.numero(), "01", "x", null, null, List.of(), null, false)))
                .hasMessageContaining("2885");
    }

    @Test void notaDeCredito13CorrigeLasCuotasDeUnaFacturaAlCredito() {
        FormaPago credito = FormaPago.credito(new BigDecimal("276.00"), List.of(new FormaPago.Cuota(new BigDecimal("276.00"), LocalDate.of(2026, 10, 10))));
        Comprobante f = facturaAceptada(credito);
        FormaPago corregida = FormaPago.credito(new BigDecimal("200.00"), List.of(new FormaPago.Cuota(new BigDecimal("100.00"), LocalDate.of(2026, 11, 10)), new FormaPago.Cuota(new BigDecimal("100.00"), LocalDate.of(2026, 12, 10))));
        Comprobante nc = service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "13", "Reprogramación",
                null, null, List.of(), corregida, false));
        assertThat(nc.formaPago().cuotas()).hasSize(2);
        assertThat(nc.totales().total()).isEqualByComparingTo("0.00");   // 3315: la NC 13 no mueve importes aunque no se envíen ítems
        // Sobre una factura al contado no aplica (3260)
        Comprobante contado = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", contado.numero(), "13", "x",
                null, null, List.of(), corregida, false))).hasMessageContaining("3260");
        // Pendiente mayor que el total de la factura (3320)
        FormaPago excesiva = FormaPago.credito(new BigDecimal("300.00"), List.of(new FormaPago.Cuota(new BigDecimal("300.00"), LocalDate.of(2026, 11, 10))));
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "13", "x",
                null, null, List.of(), excesiva, false))).hasMessageContaining("3320");
    }

    @Test void laNotaTotalNoAplicaSobreFacturasConAnticiposNiAdmiteDescuentosPropios() {
        // Factura de anticipo aceptada y factura final que la regulariza por completo: total neto 0.
        Comprobante anticipo = facturaAceptada(FormaPago.contado());
        Comprobante finalConAnticipo = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", anticipo.numero(), new BigDecimal("200.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1))), null, null, true));
        assertThat(finalConAnticipo.totales().total()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(finalConAnticipo.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("regularizó anticipos").hasMessageContaining("0.00");
        // Con ítems por el neto sí procede.
        Comprobante nc = service.emitirNota(tenantId, nc(finalConAnticipo.numero(), "01", List.of(new Item("A", "Anulación", "ZZ", BigDecimal.ONE, BigDecimal.ZERO.setScale(2), TipoAfectacionIgv.GRAVADO))));
        assertThat(nc.totales().total()).isEqualByComparingTo("0.00");

        // Sin ítems, un descuento o cargo propio no se ignora en silencio.
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x",
                null, Descuento.monto(new BigDecimal("5.00"), true), List.of(), null, false))).hasMessageContaining("descuento_global y cargos solo se admiten junto con items");
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x",
                null, null, List.of(Cargo.global(false, null, Cargo.Tipo.MONTO, new BigDecimal("5.00"))), null, false))).hasMessageContaining("solo se admiten junto con items");
    }

    @Test void laFormaDePagoSoloSeAdmiteEnLaNotaDeCredito13() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        FormaPago credito = FormaPago.credito(new BigDecimal("100.00"), List.of(new FormaPago.Cuota(new BigDecimal("100.00"), LocalDate.of(2026, 12, 10))));
        // NC 01 con forma_pago: se rechaza en vez de salir con PaymentTerms Credito sin validar contra la factura.
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x",
                null, null, List.of(), credito, false))).extracting("codigo").isEqualTo("NOTA_INVALIDA");
        // ND con forma_pago: ídem.
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_DEBITO, "FD01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x",
                List.of(new Item("I", "Interés", "ZZ", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)), null, List.of(), credito, false)))
                .hasMessageContaining("forma_pago solo se admite");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    @Test void laFacturaAnuladaEntreLaLecturaYLaTransaccionNoRecibeNota() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        // Simula una baja concurrente: la factura pasa a ANULADO justo antes de abrir la transacción de emisión.
        UnitOfWork uowConBaja = new UnitOfWork() {
            public <T> T ejecutar(Supplier<T> w) { anular(f); return w.get(); }
            public void ejecutar(Runnable w) { w.run(); }
        };
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        EmitirComprobanteService conCarrera = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, uowConBaja, Fakes.CLOCK, establecimientos);
        assertThatThrownBy(() -> conCarrera.emitirNota(tenantId, nc(f.numero(), "01", null))).hasMessageContaining("2120");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    private void anular(Comprobante f) {
        comprobantes.guardar(Comprobante.rehidratar(f.id(), tenantId, f.tipo(), f.serie(), f.numero(), f.fechaEmision(), f.horaEmision(), f.fechaVencimiento(), f.moneda(), f.tipoOperacion(),
                f.receptor(), f.items(), f.formaPago(), f.descuentoGlobal(), f.cargos(), f.detraccion(), f.retencion(), f.percepcion(), f.anticipos(), f.referencias(), null, f.nota(),
                EstadoDocumento.ANULADO, f.hash(), f.nombreArchivo(), f.xmlKey(), f.cdrKey(), f.cdr(), f.intentos(), f.ultimoError()));
    }

    @Test void sinSerieDeNotaConfigurada() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC02", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x", null, null, List.of(), null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }
}
