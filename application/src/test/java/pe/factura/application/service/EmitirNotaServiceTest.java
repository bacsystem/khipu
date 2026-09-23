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
        // Tolerancia +1 del 3503 (filas 114-122): 51 exonerado pasa
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("51.00"), TipoAfectacionIgv.EXONERADO)))).totales().total()).isEqualByComparingTo("51.00");
    }

    /**
     * 3286 sobre facturas es estricto (NotaCredito2_0 fila 111: «mayor a la sumatoria», sin la +1 que la fila 113 da a las
     * boletas) y exime al motivo 10 «Otros conceptos». Antes se aplicaba ±1 a todo: una NC que excedía por 0.50 pasaba y
     * SUNAT la rechazaba con el correlativo consumido.
     */
    @Test void elImporteTotalNoAdmiteToleranciaSalvoEnElMotivo10() {
        Comprobante f = facturaAceptada(FormaPago.contado()); // 276.00
        // 2 × 118.59 (gravado 201.00, IGV 36.18) + 50 = 287.18: cada concepto dentro de la +1 del 3503, el total supera por 11.18.
        List<Item> porEncima = List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.59"), TipoAfectacionIgv.GRAVADO),
                new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO));
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09", porEncima)))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("importe total");
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "10", porEncima)).totales().total()).isEqualByComparingTo("287.18");
        // Y tampoco el 3503 (filas 114–122 eximen al 10 igual que la 111): gravado 300 sobre una factura con gravado 200.
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "10",
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("3"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))).totales().gravado()).isEqualByComparingTo("300.00");
        // Exceso de 0.50 en el total: antes pasaba por la tolerancia ±1.
        Comprobante g = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(g.numero(), "09",
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("40.50"), TipoAfectacionIgv.EXONERADO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("(276.50)");
    }

    /**
     * 3503 por tributo cuando el total (3286) pasa: la factura tiene gravado 200 + exonerado 50 − 10 = 276; una NC de una sola
     * línea gravada de 237.77 (base 201.50) queda por debajo del total pero supera el gravado (200 + 1). Es el caso de una
     * NC por importe sobre una factura con ISC/anticipos (f-cargos en el portal); la recert #8 midió que quitar el límite
     * del gravado dejaba la suite verde. El límite del IVAP no puede atar solo: en una factura IVAP total = base × 1.04
     * y el 3286 lo atrapa antes (mutante equivalente).
     */
    @Test void elGravadoDeLaNotaNoPuedeSuperarElDeLaFacturaAunqueElTotalPase() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("D", "Disminución en el valor", "ZZ", BigDecimal.ONE, new BigDecimal("237.77"), TipoAfectacionIgv.GRAVADO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3503").hasMessageContaining("gravado").hasMessageContaining("(201.50)");
        // 237.18 (base 201.00) entra en la +1.
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("D", "Disminución en el valor", "ZZ", BigDecimal.ONE, new BigDecimal("237.18"), TipoAfectacionIgv.GRAVADO)))).totales().gravado()).isEqualByComparingTo("201.00");
    }

    /**
     * Los tres límites del 3503 que el total no implica (recert #9: sus mutaciones sobrevivían): exportación (f114, la base
     * 9995 queda por debajo del total con un cargo global sin IGV), inafecto en una factura mixta (f115) y gratuitas (f117,
     * que ni entran en el total). Cada uno con el total dentro del 3286 y el concepto fuera de la +1.
     */
    @Test void losLimitesPorTributoQueElTotalNoImplica() {
        // Exportación 4500 + cargo global 50 de 100: total 4600, base 9995 = 4500. NC por 4550 pasa el total y no la base.
        Comprobante ex = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "USD", "0200",
                new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St", "US"),
                List.of(new Item("CAF", "Café verde", "KGM", new BigDecimal("1000"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION)),
                FormaPago.contado(), null, List.of(Cargo.global(false, null, Cargo.Tipo.MONTO, new BigDecimal("100.00"))), null, null, null, List.of(), null, null, true, null, List.of(), new Exportacion("FOB", null)));
        assertThat(ex.totales().total()).isEqualByComparingTo("4600.00");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(ex.numero(), "07",
                List.of(new Item("CAF", "Café verde", "KGM", new BigDecimal("1000"), new BigDecimal("4.55"), TipoAfectacionIgv.EXPORTACION)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3503").hasMessageContaining("exportación");
        // Mixta: gravado 200 + inafecto 50. NC inafecta por 51.50: total dentro, inafecto fuera de la +1.
        Comprobante mixta = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P3", "Servicio inafecto", "ZZ", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.INAFECTO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(mixta.numero(), "07",
                List.of(new Item("P3", "Servicio inafecto", "ZZ", BigDecimal.ONE, new BigDecimal("51.50"), TipoAfectacionIgv.INAFECTO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3503").hasMessageContaining("inafecto");
        // Gratuitas: no entran en el total (la NC pasa el 3286 con 0.00) pero su valor referencial sí se compara (f117).
        Comprobante conGratuita = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B1", "Bonificación", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(conGratuita.numero(), "07",
                List.of(new Item("B1", "Bonificación", "NIU", BigDecimal.ONE, new BigDecimal("102.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3503").hasMessageContaining("gratuitas");
    }

    /** Una NC dada de baja (ANULADO) ya no acredita: sin este filtro la factura quedaba bloqueada tras anular una nota errónea. */
    @Test void unaNotaAnuladaNoCuentaEnElAcumulado() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        Comprobante primera = service.emitirNota(tenantId, nc(f.numero(), "01", null));
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null))).hasMessageContaining("ya acreditado 276.00");
        primera.anular();
        comprobantes.guardar(primera);
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "01", null)).totales().total()).isEqualByComparingTo("276.00");
    }

    /** La nota total copia también el redondeo de la factura: sin él salía por el total sin redondear, por encima del PayableAmount (3286). */
    @Test void laNotaTotalCopiaElRedondeoDeLaFactura() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.44"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, new BigDecimal("-0.44"), true));
        assertThat(f.totales().total()).isEqualByComparingTo("118.00");
        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "01", null));
        assertThat(nc.totales().redondeo()).isEqualByComparingTo("-0.44");
        assertThat(nc.totales().total()).isEqualByComparingTo("118.00");
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

    /** IVAP (#67): la NC sobre una factura IVAP se limita por base e IVAP (3503, tributo 1016) y no admite mezclar con líneas IGV. */
    @Test void laNotaDeCreditoSobreUnaFacturaIvapSeLimitaPorElIvap() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "MOLINO SAC", "AV 1"),
                List.of(new Item("ARZ", "Arroz pilado", "KGM", new BigDecimal("100"), new BigDecimal("3.12"), TipoAfectacionIgv.IVAP)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        assertThat(f.totales().ivap()).isEqualByComparingTo("12.00");
        assertThat(f.totales().igv()).isEqualByComparingTo("0.00");
        assertThat(f.totales().total()).isEqualByComparingTo("312.00");
        // Sobre IVAP la NC exige el motivo 12 (3230, NotaCredito2_0 fila 223) y el 12 exige líneas 17 (2644, fila 222).
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("ARZ", "Arroz", "KGM", new BigDecimal("50"), new BigDecimal("3.12"), TipoAfectacionIgv.IVAP)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3230");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "12",
                List.of(new Item("ARZ", "Arroz", "KGM", new BigDecimal("50"), new BigDecimal("3.12"), TipoAfectacionIgv.GRAVADO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("2644");
        // Más IVAP que la factura → 3286 (y 3503 IVAP)
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "12",
                List.of(new Item("ARZ", "Arroz", "KGM", new BigDecimal("150"), new BigDecimal("3.12"), TipoAfectacionIgv.IVAP)))))
                .hasMessageContaining("3286");
        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "12",
                List.of(new Item("ARZ", "Arroz", "KGM", new BigDecimal("50"), new BigDecimal("3.12"), TipoAfectacionIgv.IVAP))));
        assertThat(nc.totales().ivap()).isEqualByComparingTo("6.00");
        assertThat(nc.totales().total()).isEqualByComparingTo("156.00");
        // Nota total (12, copia las líneas IVAP de la factura) y descuenta lo ya acreditado (#83); con el 01 sería 3230.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null))).hasMessageContaining("3230");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "12", null))).hasMessageContaining("ya acreditado");
    }

    /** Exportación (#65): la nota hereda tipo de operación, datos de exportación y receptor del exterior; se limita por la base 9995 (3503). */
    @Test void laNotaSobreUnaExportacionHeredaSusDatos() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "USD", "0200",
                new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St", "US"),
                List.of(new Item("CAF", "Café verde", "KGM", new BigDecimal("1000"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true, null, List.of(), new Exportacion("FOB", null)));
        assertThat(f.totales().exportacion()).isEqualByComparingTo("4500.00");
        assertThat(f.exportacion().incoterm()).isEqualTo("FOB");
        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("CAF", "Café verde", "KGM", new BigDecimal("100"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION))));
        assertThat(nc.tipoOperacion()).isEqualTo("0200");
        assertThat(nc.moneda()).isEqualTo("USD");
        assertThat(nc.exportacion()).isEqualTo(f.exportacion());
        assertThat(nc.receptor().pais()).isEqualTo("US");
        assertThat(nc.totales().exportacion()).isEqualByComparingTo("450.00");
        // El acumulado (450 + 4500) supera la factura: 3286 por el total (y el mismo exceso en la base 9995, 3503)
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("CAF", "Café verde", "KGM", new BigDecimal("1000"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("ya acreditado 450.00");
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
        comprobantes.guardar(Comprobante.persistido(f.id(), tenantId, f.tipo(), f.serie(), f.numero(), f.fechaEmision(), EstadoDocumento.ANULADO, f.receptor(), f.items()).horaEmision(f.horaEmision()).fechaVencimiento(f.fechaVencimiento()).moneda(f.moneda()).tipoOperacion(f.tipoOperacion()).formaPago(f.formaPago()).descuentoGlobal(f.descuentoGlobal()).cargos(f.cargos()).detraccion(f.detraccion()).retencion(f.retencion()).percepcion(f.percepcion()).anticipos(f.anticipos()).referencias(f.referencias()).nota(f.nota()).firma(f.hash(), f.nombreArchivo(), f.xmlKey()).cdr(f.cdr(), f.cdrKey()).envio(f.intentos(), f.ultimoError()).rehidratar());
    }

    @Test void sinSerieDeNotaConfigurada() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC02", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x", null, null, List.of(), null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }
}
