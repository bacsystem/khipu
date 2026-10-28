package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.IdempotenciaRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.UblGenerator;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notas de crédito/débito: la factura modificada debe ser de la empresa y estar aceptada; límites 3286/3503; nota total vs parcial; NC 13. */
class EmitirNotaServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Series series = new Fakes.Series();
    Fakes.Bajas bajas = new Fakes.Bajas();
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
        public void validarBaja(String xml, ComunicacionBaja baja) {}
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml, "HASH");
    EmitirComprobanteService service;
    Fakes.Tope tope = new Fakes.Tope();

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_CREDITO, "FC01", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_DEBITO, "FD01", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_CREDITO, "BC01", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_DEBITO, "BD01", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos, bajas, new Fakes.Idempotencias(), tope, Fakes.SIN_COMPRADOR);
    }

    /** #18: una nota consume del plan como una factura, así que en el tope tampoco se emite. Para anular una factura queda la comunicación de baja, que no consume. */
    @Test void enElTopeDelPlanTampocoSeEmiteUnaNota() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        tope.lleno = true;

        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("LIMITE_PLAN");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    /** #378: SUNAT aplica la regla del RUC del receptor también a las notas (anexos de NC y ND, 1083); la nota lleva el comprador del documento. */
    @Test void unaNotaSeRechazaSiElRucDelCompradorYaNoFiguraEnElPadron() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        var padron = new ConsultarPadronRucServiceTest.PadronEnMemoria();
        padron.cargada = Fakes.CLOCK.instant().minus(java.time.Duration.ofHours(6));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        EmitirComprobanteService conPadron = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos, bajas,
                new Fakes.Idempotencias(), new Fakes.Tope(), new ValidarCompradorEnPadron(padron, Fakes.CLOCK, java.time.Duration.ofDays(3)));

        assertThatThrownBy(() -> conPadron.emitirNota(tenantId, nc(f.numero(), "01", null))).isInstanceOf(DomainException.class).hasMessageContaining("1083");
        assertThatThrownBy(() -> conPadron.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_DEBITO, "FD01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "Intereses",
                laptops("1", "11.80"), null, List.of(), null, true))).isInstanceOf(DomainException.class).hasMessageContaining("1083");

        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    /** #325: la factura guarda el correo del cliente normalizado y la nota, que va al mismo cliente, lo hereda para que también le llegue al aceptarse. */
    @Test void laNotaHeredaElCorreoDelClienteDeLaFactura() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true, null, List.of(), null, null, " Compras@Cliente.PE "));
        assertThat(f.correoCliente()).isEqualTo("compras@cliente.pe");

        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "01", null));

        assertThat(nc.correoCliente()).isEqualTo("compras@cliente.pe");
        assertThat(comprobantes.datos.get(nc.id()).correoCliente()).as("se guarda con la nota").isEqualTo("compras@cliente.pe");
    }

    /** Un correo mal formado se rechaza antes de numerar: el número no se consume. */
    @Test void unCorreoDelClienteMalFormadoSeRechazaSinConsumirNumero() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true, null, List.of(), null, null, "compras@cliente")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        assertThat(comprobantes.datos).isEmpty();
        assertThat(facturaAceptada(FormaPago.contado()).numero()).isEqualTo(1L);
    }

    /** Factura de 2 laptops (200 + 36) y un libro exonerado (40): total 276.00. Sin descuentos: una nota no admite los que no afectan la base (3280, #388). */
    private Comprobante facturaAceptada(FormaPago fp) {
        return service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("40.00"), TipoAfectacionIgv.EXONERADO)),
                fp, null, List.of(), null, null, null, List.of(), null, null, true));
    }

    /**
     * Como {@link #facturaAceptada} pero con un descuento global 02 de 8.48 (afecta la base): gravado 191.52, IGV 34.47, exonerado 50, total 275.99. Es el descuento que la
     * nota total copia de la factura (#388: pasa al valor de las líneas, la nota no lo lleva).
     */
    private Comprobante facturaConDescuentoAceptada() {
        return service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                FormaPago.contado(), Descuento.monto(new BigDecimal("8.48"), true), List.of(), null, null, null, List.of(), null, null, true));
    }

    private EmitirNotaCommand nc(long numeroFactura, String motivo, List<Item> items) {
        return new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", numeroFactura, motivo, "Sustento", items, null, List.of(), null, true);
    }

    static final Receptor SIN_DOCUMENTO = new Receptor("-", "-", "CLIENTES VARIOS", null);

    /** Boleta de 2 laptops (200 + 36) a un comprador sin documento: total 236.00. */
    private Comprobante boletaAceptada() {
        return service.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101", SIN_DOCUMENTO,
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
    }

    private EmitirNotaCommand notaDeBoleta(TipoDocumento tipo, String serie, long numeroBoleta, String motivo, List<Item> items) {
        return new EmitirNotaCommand(tipo, serie, null, LocalDate.of(2026, 9, 13), "B001", numeroBoleta, motivo, "Sustento", items, null, List.of(), null, true);
    }

    private static List<Item> laptops(String cantidad, String precio) {
        return List.of(new Item("P1", "Laptop", "NIU", new BigDecimal(cantidad), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO));
    }

    /** #316: una boleta en dólares sin documento se emite con tipo de cambio (S/ 375 ≤ 700) y su nota total hereda moneda y tipo de cambio. */
    @Test void laNotaHeredaElTipoDeCambioDeLaBoletaEnDolares() {
        Comprobante b = service.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 10), null, "USD", "0101", SIN_DOCUMENTO,
                List.of(new Item("P1", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true, null, List.of(), null, new BigDecimal("3.750")));
        assertThat(b.tipoCambio()).isEqualByComparingTo("3.750");
        assertThat(b.totalEnSoles()).hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("375.00"));
        Comprobante nc = service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "01", null));
        assertThat(nc.moneda()).isEqualTo("USD");
        assertThat(nc.tipoCambio()).isEqualByComparingTo("3.750");
        // Sin tipo de cambio la misma boleta sigue pidiendo el documento del comprador.
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 10), null, "USD", "0101", SIN_DOCUMENTO,
                List.of(new Item("P1", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true)))
                .hasMessageContaining("identifica al comprador");
    }

    /** #295: una boleta que regularizó un anticipo no se anula con una nota total (el CreditNote no lleva PrepaidPayment): el mensaje dice «boleta» y pide los ítems por el neto. */
    @Test void laNotaTotalSobreUnaBoletaConAnticiposPideLosItemsPorElNeto() {
        Comprobante anticipo = boletaAceptada();   // B001: 200.00 gravado, mismo comprador sin documento
        Comprobante conAnticipo = service.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101", SIN_DOCUMENTO,
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null,
                List.of(new Anticipo("B001", anticipo.numero(), new BigDecimal("100.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1))), null, null, true));
        assertThat(conAnticipo.totales().total()).isEqualByComparingTo("118.00");   // 236.00 − 118.00 del anticipo
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", conAnticipo.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("La boleta B001-" + conAnticipo.numero()).hasMessageContaining("regularizó anticipos")
                .hasMessageContaining("indique los items").hasMessageNotContaining("La factura");
    }

    /** #293: la nota total sobre una boleta copia sus ítems y su comprador sin documento, y queda como nota de la boleta. */
    @Test void notaDeCreditoTotalSobreUnaBoleta() {
        Comprobante b = boletaAceptada();
        Comprobante nc = service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "01", null));
        assertThat(nc.nota().tipoAfectado()).isEqualTo(TipoDocumento.BOLETA);
        assertThat(nc.nota().documentoAfectado()).isEqualTo("B001-" + b.numero());
        assertThat(nc.receptor()).isEqualTo(SIN_DOCUMENTO);
        assertThat(nc.items()).isEqualTo(b.items());
        assertThat(nc.totales().total()).isEqualByComparingTo("236.00");
        assertThat(nc.esDeBoleta()).isTrue();
        assertThat(nc.nombreArchivo()).isEqualTo("20100066603-07-BC01-1");
        assertThat(comprobantes.notasDe(tenantId, "B001", b.numero())).extracting(Comprobante::id).containsExactly(nc.id());
    }

    @Test void notaDeDebitoSobreUnaBoleta() {
        Comprobante b = boletaAceptada();
        Comprobante nd = service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_DEBITO, "BD01", b.numero(), "01", laptops("1", "11.80")));
        assertThat(nd.tipo()).isEqualTo(TipoDocumento.NOTA_DEBITO);
        assertThat(nd.nota().tipoAfectado()).isEqualTo(TipoDocumento.BOLETA);
    }

    /**
     * 3286 sobre una boleta admite +1 (NotaCredito2_0 fila 113; desde 2027 es error y khipu lo aplica ya); sobre una factura, 0 (fila 111). El acumulado de NC
     * vigentes cuenta igual que en la factura (#83).
     */
    @Test void laNotaDeCreditoSobreUnaBoletaAdmiteUnSolDeTolerancia() {
        Comprobante b = boletaAceptada(); // 236.00
        assertThat(service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "09", laptops("2", "118.50"))).totales().total())
                .as("237.00: un sol por encima, dentro de la tolerancia").isEqualByComparingTo("237.00");
        Comprobante otra = boletaAceptada();
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", otra.numero(), "09", laptops("2", "118.60"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3286").hasMessageContaining("boleta");
        service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", otra.numero(), "07", laptops("1", "118.00")));
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", otra.numero(), "07", laptops("1", "120.00"))))
                .as("118 + 120 = 238 > 236 + 1").hasMessageContaining("3286").hasMessageContaining("ya acreditado");
    }

    /** La letra de la serie de la nota sigue a la del documento que modifica (2399/2400): una nota F no modifica una boleta, ni una B una factura. */
    @Test void laSerieDeLaNotaTieneLaLetraDelDocumentoQueModifica() {
        Comprobante b = boletaAceptada();
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "FC01", b.numero(), "01", null)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "BC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x", null, null, List.of(), null, true)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    /** Sobre una boleta no hay NC 13 (3259) ni NC por descuento o bonificación (RCP 10.1.4, 4367): se rechazan antes de mirar la forma de pago. */
    @Test void sobreUnaBoletaNoHayNotaDeCredito13NiDescuentos() {
        Comprobante b = boletaAceptada();
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "13", null))).hasMessageContaining("3259");
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "04", laptops("1", "10.00")))).hasMessageContaining("4367");
        assertThat(comprobantes.notasDe(tenantId, "B001", b.numero())).isEmpty();
    }

    /**
     * La NC 10 de una boleta no va en el resumen diario (guía, p. 19): emitida el 13 sin enviar, el 19 (6.º día) ya no tiene canal y se cierra con el 1079.
     * A diferencia de la boleta (#290: su fecha es la de la venta y no se reemplaza), una nota sí se vuelve a emitir con fecha vigente: el mensaje lo dice.
     */
    @Test void laNotaDeCredito10DeUnaBoletaVenceAlQuintoDia() {
        Comprobante b = boletaAceptada();
        Comprobante nc = service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "BC01", null, LocalDate.of(2026, 9, 13), "B001", b.numero(), "10", "Otros",
                laptops("1", "10.00"), null, List.of(), null, false));
        assertThat(nc.fechaLimiteEnvio()).isEqualTo(LocalDate.of(2026, 9, 18));
        java.time.Clock dia19 = java.time.Clock.fixed(java.time.Instant.parse("2026-09-19T15:00:00Z"), java.time.ZoneId.of("America/Lima"));
        EnviarDocumentoService tarde = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, dia19);
        assertThatThrownBy(() -> tarde.enviar(tenantId, nc.id())).extracting("codigo", "message")
                .satisfies(t -> assertThat(t.get(0)).isEqualTo("FUERA_DE_PLAZO"), t -> assertThat((String) t.get(1)).contains("1079").contains("Emita"));
        assertThat(comprobantes.buscar(tenantId, nc.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
    }

    /** La boleta tiene que existir, estar aceptada y no anulada ni con una baja en curso (2119/2120), como la factura; los mensajes la nombran. */
    @Test void boletaInexistenteNoAceptadaOAnuladaSeRechaza() {
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", 999, "01", null)))
                .hasMessageContaining("2119").hasMessageContaining("boleta B001-999");
        Comprobante b = boletaAceptada();
        anular(b);
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "01", null)))
                .hasMessageContaining("2120").hasMessageContaining("boleta");
    }

    @Test void notaTotalCopiaItemsDescuentoYCargosDeLaFactura() {
        Comprobante f = facturaConDescuentoAceptada();
        assertThat(f.totales().total()).isEqualByComparingTo("275.99");

        Comprobante nc = service.emitirNota(tenantId, nc(f.numero(), "01", null));
        assertThat(nc.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(nc.serie()).isEqualTo("FC01");
        assertThat(nc.numero()).isEqualTo(1L);
        assertThat(nc.nombreArchivo()).isEqualTo("20100066603-07-FC01-1");
        assertThat(nc.items()).isEqualTo(f.items());
        assertThat(nc.descuentoGlobal()).isEqualTo(f.descuentoGlobal());
        assertThat(nc.totales().descuentoGlobal()).as("#388: la nota no lleva el descuento global, lo reparte en las líneas").isNull();
        assertThat(nc.receptor()).isEqualTo(f.receptor());
        assertThat(nc.moneda()).isEqualTo("PEN");
        assertThat(nc.totales().total()).isEqualByComparingTo("275.99");
        assertThat(nc.totales().gravado()).isEqualByComparingTo(f.totales().gravado());
        assertThat(nc.totales().igv()).isEqualByComparingTo(f.totales().igv());
        assertThat(nc.nota().documentoAfectado()).isEqualTo("F001-" + f.numero());
        assertThat(nc.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(validado[0]).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(new String(storage.leer(nc.xmlKey()))).startsWith("<NOTA_CREDITO>");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).extracting(Comprobante::id).containsExactly(nc.id());
    }

    /**
     * #388: una factura con un descuento que no afecta la base (03) no se puede anular con una nota total: el importe total de la nota no resta descuentos (3280, e-beta
     * 2026-10-18). Se rechaza con el motivo antes de numerar; antes salía con número y SUNAT la devolvía.
     */
    @Test void laNotaTotalDeUnaFacturaConDescuentoQueNoAfectaLaBaseSeRechazaAntesDeNumerar() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), Descuento.monto(new BigDecimal("10.00"), false), List.of(), null, null, null, List.of(), null, null, true));

        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("3280").hasMessageContaining("F001-" + f.numero())
                .extracting(e -> ((DomainException) e).codigo()).isEqualTo("DESCUENTO_INVALIDO");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).as("no se emitió ni numeró ninguna nota").isEmpty();
    }

    /**
     * #308: una boleta emitida antes de validar el catálogo 03 con «ROL» (no existe en la rec 20) se informó en un resumen diario, que no
     * lleva líneas, y quedó aceptada. Ni la nota con esa unidad en sus ítems ni la nota total que la copia se emiten, porque SUNAT cruza la
     * unidad de la nota con el catálogo (2936) y la nota sale sola (#348-H1); ninguna consume número. Con ítems propios en «RO», sí.
     */
    @Test void unaUnidadFueraDelCatalogo03NoSeEmiteEnLaNotaNiCopiadaDelDocumento() {
        Comprobante vieja = Comprobante.persistido(UUID.randomUUID(), tenantId, TipoDocumento.BOLETA, "B001", 7L, LocalDate.of(2026, 9, 10), EstadoDocumento.ACEPTADO, SIN_DOCUMENTO,
                List.of(new Item("P1", "Cable", "ROL", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "20100066603-03-B001-7", "k").rehidratar();
        comprobantes.guardar(vieja);

        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", 7L, "07",
                List.of(new Item("P1", "Cable", "ROL", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("2936").hasMessageContaining("ROL");
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", 7L, "01", null)))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("2936 - La boleta B001-7 lleva la unidad ROL").hasMessageContaining("indique los items");
        assertThat(series.ultimo).as("las notas rechazadas no consumen número").containsEntry(tenantId + "07BC01", 0L);

        Comprobante nc = service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", 7L, "01",
                List.of(new Item("P1", "Cable", "RO", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))));
        assertThat(nc.items()).extracting(Item::unidad).containsExactly("RO");
        assertThat(nc.numero()).isEqualTo(1L);
    }

    /**
     * #354: las reglas de la línea (ICBPER 2892/4320, precio 0 2369, más de 999 líneas 2137) también valen en la nota, sobre factura y sobre boleta, y la rechazan antes de
     * numerar. La NC 13 y su línea sin importe (3315) no pasan por ellas.
     */
    @Test void lasReglasDeLaLineaSeRechazanEnLaNotaSinConsumirNumero() {
        Comprobante boleta = boletaAceptada();
        Comprobante factura = facturaAceptada(FormaPago.contado());
        List<Item> bolsasFraccionarias = List.of(new Item("B", "Bolsa", "NIU", new BigDecimal("1.5"), new BigDecimal("0.60"), TipoAfectacionIgv.GRAVADO, null, null, true));
        List<Item> bolsasEnKilos = List.of(new Item("B", "Bolsa", "KGM", new BigDecimal("2"), new BigDecimal("0.60"), TipoAfectacionIgv.GRAVADO, null, null, true));
        List<Item> sinPrecio = laptops("1", "0");
        List<Item> demasiadas = java.util.Collections.nCopies(1000, new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("1.18"), TipoAfectacionIgv.GRAVADO));

        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", boleta.numero(), "07", bolsasFraccionarias))).hasMessageStartingWith("2892 - ");
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", boleta.numero(), "07", bolsasEnKilos))).hasMessageStartingWith("4320 - ");
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", boleta.numero(), "07", sinPrecio))).hasMessageStartingWith("2369 - ");
        assertThatThrownBy(() -> service.emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", boleta.numero(), "07", demasiadas))).hasMessageStartingWith("2137 - ");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(factura.numero(), "07", sinPrecio))).hasMessageStartingWith("2369 - ");
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(factura.numero(), "07", demasiadas))).hasMessageStartingWith("2137 - ");
        assertThat(series.ultimo).as("las notas rechazadas no consumen número").containsEntry(tenantId + "07BC01", 0L).containsEntry(tenantId + "07FC01", 0L);
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
        // Total menor pero base exonerada mayor que la de la factura (3503): 50 exonerado vs 40
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)))))
                .hasMessageContaining("3503").hasMessageContaining("exonerado");
        // Tolerancia +1 del 3503 (filas 114-122): 41 exonerado pasa
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "09",
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("41.00"), TipoAfectacionIgv.EXONERADO)))).totales().total()).isEqualByComparingTo("41.00");
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
     * 3503 por tributo cuando el total (3286) pasa: la factura tiene gravado 200 + IGV 36 + exonerado 40 = 276; una NC de una sola
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

    /**
     * f118 (impuesto de las gratuitas, 9996) no es redundante con f117 (su base): una nota que cambia la CLASE de gratuita —21
     * exonerada (impuesto 0) por 11 gravada (18 %)— lleva la misma base y 18 más de impuesto. Alcanzable solo por API, porque el
     * portal reenvía la afectación de la línea facturada.
     */
    @Test void elImpuestoDeLasGratuitasSeLimitaAparteDeSuBase() {
        Comprobante f = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("M1", "Muestra", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.EXONERADO_GRATUITO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        assertThat(f.totales().gratuito()).isEqualByComparingTo("100.00");
        assertThat(f.totales().igvGratuitas()).isEqualByComparingTo("0.00");
        // Misma base gratuita, pero declarada como gravada (11): el IGV informado pasa de 0 a 18 y f118 la rechaza.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("M1", "Muestra", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO_RETIRO_PREMIO)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3503").hasMessageContaining("IGV de las operaciones gratuitas");
        // Con la misma clase (21) pasa: base y impuesto coinciden con la factura.
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "07",
                List.of(new Item("M1", "Muestra", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.EXONERADO_GRATUITO)))).totales().gratuito()).isEqualByComparingTo("100.00");
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
        Comprobante f = facturaAceptada(FormaPago.contado());   // 276.00: 200 gravado + 36 IGV + 40 exonerado
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
        // El exonerado sigue disponible (los 40 acreditando 236 + 40 = 276), pero 5 más ya excede el total.
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
        // H3 (revisión de #354): con neto 0.00 «indique los items por el importe neto» era un callejón sin salida (la línea en cero la rechaza 2369/2370 y cualquier
        // importe supera el total de la factura, 3286): el mensaje manda a la comunicación de baja.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(finalConAnticipo.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("regularizó anticipos").hasMessageContaining("0.00").hasMessageContaining("comunicación de baja")
                .hasMessageNotContaining("indique los items");
        // Con un neto mayor que cero sí hay nota posible por ese importe: el mensaje pide los items y no manda a la baja.
        Comprobante anticipoParcial = facturaAceptada(FormaPago.contado());
        Comprobante conNeto = service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", anticipoParcial.numero(), new BigDecimal("100.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1))), null, null, true));
        assertThat(conNeto.totales().total()).isGreaterThan(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(conNeto.numero(), "01", null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("indique los items").hasMessageNotContaining("comunicación de baja");
        // Con ítems por el neto (0.00) tampoco: SUNAT rechaza la línea en cero (2369/2370, #354) y no hay importe que anular; antes salía y volvía rechazada.
        assertThatThrownBy(() -> service.emitirNota(tenantId, nc(finalConAnticipo.numero(), "01", List.of(new Item("A", "Anulación", "ZZ", BigDecimal.ONE, BigDecimal.ZERO.setScale(2), TipoAfectacionIgv.GRAVADO)))))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("2369 - ");

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
        EmitirComprobanteService conCarrera = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, uowConBaja, Fakes.CLOCK, establecimientos, bajas, new Fakes.Idempotencias(), new Fakes.Tope(), Fakes.SIN_COMPRADOR);
        assertThatThrownBy(() -> conCarrera.emitirNota(tenantId, nc(f.numero(), "01", null))).hasMessageContaining("2120");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    /** Lo que otra transacción ya dejó en la base: la fila ANULADA tal cual, sin pasar por el guardado (que, como el real, exige el estado de origen; LOG-01). */
    private void anular(Comprobante f) {
        comprobantes.datos.put(f.id(), Comprobante.persistido(f.id(), tenantId, f.tipo(), f.serie(), f.numero(), f.fechaEmision(), EstadoDocumento.ANULADO, f.receptor(), f.items()).horaEmision(f.horaEmision()).fechaVencimiento(f.fechaVencimiento()).moneda(f.moneda()).tipoOperacion(f.tipoOperacion()).formaPago(f.formaPago()).descuentoGlobal(f.descuentoGlobal()).cargos(f.cargos()).detraccion(f.detraccion()).retencion(f.retencion()).percepcion(f.percepcion()).anticipos(f.anticipos()).referencias(f.referencias()).nota(f.nota()).firma(f.hash(), f.nombreArchivo(), f.xmlKey()).cdr(f.cdr(), f.cdrKey()).envio(f.intentos(), f.ultimoError()).rehidratar());
    }

    @Test void sinSerieDeNotaConfigurada() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        assertThatThrownBy(() -> service.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC02", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "x", null, null, List.of(), null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }

    // --- #355: Idempotency-Key en la nota ----------------------------------------------------------------------------------------

    private static final Idempotencia CLAVE = new Idempotencia("c1a7e5b0-0000-4000-8000-000000000355", "huella-nota-1");

    /** Servicio con las claves a la vista, sobre los mismos datos; con otro reloj es el mismo sistema, más tarde. */
    private record ConClaves(EmitirComprobanteService service, Fakes.Idempotencias claves) {}

    private ConClaves conClaves() { return conClaves(new Fakes.Idempotencias(), Fakes.CLOCK); }

    private ConClaves conClaves(Fakes.Idempotencias claves, Clock reloj) {
        Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
        claves.uow = uow;
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, uow, reloj);
        return new ConClaves(new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, uow, reloj, establecimientos, bajas, claves, tope, Fakes.SIN_COMPRADOR), claves);
    }

    @Test void conUnaClaveNuevaEmiteLaNotaYLaAnotaBajoLaClaveDeNotas() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();

        var e = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThat(e.repetida()).isFalse();
        assertThat(e.comprobante().tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(s.claves().filas.get("nota:" + tenantId + "|" + CLAVE.clave()))
                .extracting(IdempotenciaRepository.Registro::huella, IdempotenciaRepository.Registro::recursoId)
                .containsExactly("huella-nota-1", e.comprobante().id());
        assertThat(s.claves().reservadoDentro).as("la reserva va en la transacción de la emisión: si esta se revierte, la clave queda libre").containsExactly(true);
        assertThat(s.claves().completadoDentro).as("y la clave se completa en la misma transacción: nunca queda una reserva sin nota ni una nota sin clave").containsExactly(true);
    }

    /** El reintento tras un corte de red, en una nota de factura: la misma nota, sin otro número ni otro envío a SUNAT. */
    @Test void elReintentoDeUnaNotaDeFacturaDevuelveLaMismaNotaSinEmitirOtra() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        Comprobante primera = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();
        int enviosAntes = gateway.enviados;

        var otraVez = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThat(otraVez.repetida()).isTrue();
        assertThat(otraVez.comprobante().id()).isEqualTo(primera.id());
        assertThat(otraVez.comprobante().numero()).isEqualTo(1L);
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).hasSize(1);
        assertThat(gateway.enviados).as("no se reenvía a SUNAT").isEqualTo(enviosAntes);
        assertThat(service.emitirNota(tenantId, nc(f.numero(), "10", laptops("1", "11.80"))).numero()).as("el número 2 de la serie sigue libre").isEqualTo(2L);
    }

    /** Lo mismo para una nota sobre una boleta: la clave no distingue entre las dos. */
    @Test void elReintentoDeUnaNotaDeBoletaDevuelveLaMismaNotaSinEmitirOtra() {
        Comprobante b = boletaAceptada();
        ConClaves s = conClaves();
        Comprobante primera = s.service().emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "01", null), CLAVE).comprobante();

        var otraVez = s.service().emitirNota(tenantId, notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", b.numero(), "01", null), CLAVE);

        assertThat(otraVez.repetida()).isTrue();
        assertThat(otraVez.comprobante().id()).isEqualTo(primera.id());
        assertThat(otraVez.comprobante().esDeBoleta()).isTrue();
        assertThat(comprobantes.notasDe(tenantId, "B001", b.numero())).hasSize(1);
    }

    @Test void laMismaClaveConOtraNotaSeRechazaSinEmitir() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThatThrownBy(() -> s.service().emitirNota(tenantId, nc(f.numero(), "01", null), new Idempotencia(CLAVE.clave(), "otra-huella")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("IDEMPOTENCIA_INVALIDA");
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).hasSize(1);
    }

    /** Las claves de las facturas y las de las notas son espacios distintos: la misma cadena en las dos no se pisa (el pedido sería otro contenido). */
    @Test void laMismaClaveEnFacturasYEnNotasNoSeCruza() {
        ConClaves s = conClaves();
        Comprobante f = s.service().emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), laptops("1", "118.00"), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true), CLAVE).comprobante();

        var nota = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), new Idempotencia(CLAVE.clave(), "huella-de-la-nota"));

        assertThat(nota.repetida()).isFalse();
        assertThat(nota.comprobante().tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(s.claves().filas).containsOnlyKeys("factura:" + tenantId + "|" + CLAVE.clave(), "nota:" + tenantId + "|" + CLAVE.clave());
    }

    @Test void laClaveDeLaNotaEsPorEmpresa() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        UUID otra = UUID.randomUUID();
        tenants.guardar(Fakes.tenantListo(otra));
        series.crear(new Serie(otra, TipoDocumento.FACTURA, "F001", 0, true));
        series.crear(new Serie(otra, TipoDocumento.NOTA_CREDITO, "FC01", 0, true));
        Comprobante deOtra = s.service().emitirFactura(otra, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 10), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), laptops("1", "118.00"), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        Comprobante mia = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();

        var suya = s.service().emitirNota(otra, nc(deOtra.numero(), "01", null), CLAVE);

        assertThat(suya.repetida()).isFalse();
        assertThat(suya.comprobante().id()).isNotEqualTo(mia.id());
        assertThat(suya.comprobante().tenantId()).isEqualTo(otra);
    }

    /** H1 de la revisión de #225, en la nota: el reintento se contesta antes de mirar el estado del documento, el plazo y el certificado. */
    @Test void elReintentoSeContestaAunqueElDocumentoYaEstéAnuladoPorUnaBaja() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        Comprobante emitida = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();
        anular(f);

        var reintento = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThat(reintento.repetida()).isTrue();
        assertThat(reintento.comprobante().id()).isEqualTo(emitida.id());
    }

    @Test void elReintentoSeContestaAunqueElDocumentoYaNoExista() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        Comprobante emitida = s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();

        var reintento = s.service().emitirNota(tenantId, nc(f.numero() + 77, "01", null), CLAVE);

        assertThat(reintento.comprobante().id()).isEqualTo(emitida.id());
    }

    @Test void elReintentoConElCertificadoYaVencidoDevuelveLaNotaYaEmitida() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves hoy = conClaves();
        Comprobante emitida = hoy.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();
        ConClaves muchoDespues = conClaves(hoy.claves(), Clock.offset(Fakes.CLOCK, Duration.ofDays(2000)));

        var reintento = muchoDespues.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThat(reintento.repetida()).isTrue();
        assertThat(reintento.comprobante().id()).isEqualTo(emitida.id());
    }

    /** Contestar antes de validar no abre la puerta a otro contenido: con otra huella se sigue rechazando, aunque el documento ya no admita notas. */
    @Test void conOtraHuellaSeRechazaAunqueElDocumentoYaNoAdmitaNotas() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);
        anular(f);

        assertThatThrownBy(() -> s.service().emitirNota(tenantId, nc(f.numero(), "01", null), new Idempotencia(CLAVE.clave(), "otra-huella")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("IDEMPOTENCIA_INVALIDA");
    }

    /**
     * Una clave que no se usó antes no sirve de pase: si el documento ya no admite la nota se rechaza como siempre, antes de reservar nada. (Que un rechazo
     * posterior a la reserva, dentro de la transacción, deje la clave libre lo prueba la E2E con Postgres: aquí el fake no revierte.)
     */
    @Test void unaClaveNuevaSobreUnDocumentoAnuladoSeRechazaSinReservarLaClave() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        anular(f);
        ConClaves s = conClaves();

        assertThatThrownBy(() -> s.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE)).hasMessageContaining("2120");
        assertThat(s.claves().filas).isEmpty();
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).isEmpty();
    }

    /**
     * Dos pedidos simultáneos con la misma clave: los dos pasan la consulta previa (el otro aún no confirmó) y la reserva del segundo espera al
     * primero. Esa reserva es la que tiene que ver la nota y devolverla, sin tomar la serie ni bloquear el documento: lo que impide la nota doble.
     */
    @Test void siLaReservaEncuentraLaNotaQueLaConsultaPreviaNoVioLaDevuelveSinEmitirOtra() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves primero = conClaves();
        Comprobante emitida = primero.service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE).comprobante();
        int enviosAntes = gateway.enviados;
        Fakes.Idempotencias ciegaAlBuscar = new Fakes.Idempotencias() {
            @Override public Optional<IdempotenciaRepository.Registro> buscar(String alcance, String clave) { return Optional.empty(); }
        };
        ciegaAlBuscar.filas.putAll(primero.claves().filas);

        var simultaneo = conClaves(ciegaAlBuscar, Fakes.CLOCK).service().emitirNota(tenantId, nc(f.numero(), "01", null), CLAVE);

        assertThat(simultaneo.repetida()).isTrue();
        assertThat(simultaneo.comprobante().id()).isEqualTo(emitida.id());
        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).as("no se emitió otra nota").hasSize(1);
        assertThat(gateway.enviados).as("tampoco se reenvía a SUNAT").isEqualTo(enviosAntes);
    }

    @Test void sinClaveCadaPedidoEsUnaNotaNueva() {
        Comprobante f = facturaAceptada(FormaPago.contado());
        ConClaves s = conClaves();
        s.service().emitirNota(tenantId, nc(f.numero(), "07", laptops("1", "11.80")), null);
        s.service().emitirNota(tenantId, nc(f.numero(), "07", laptops("1", "11.80")), null);

        assertThat(comprobantes.notasDe(tenantId, "F001", f.numero())).hasSize(2);
        assertThat(s.claves().filas).isEmpty();
    }
}
