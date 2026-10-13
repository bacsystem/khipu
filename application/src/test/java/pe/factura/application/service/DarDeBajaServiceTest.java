package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.RechazoDeSolRepository;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comunicación de baja: RA por comprobante, sendSummary + getStatus, ANULADO al aceptarse, outbox mientras SUNAT procesa o falla. */
class DarDeBajaServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Bajas bajas = new Fakes.Bajas();
    Fakes.Series series = new Fakes.Series();
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos(series);
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    String[] validado = new String[1];
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) {}
        public void validarBaja(String xml, ComunicacionBaja baja) { validado[0] = xml; }
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("</VoidedDocuments>", "<ds:Signature/></VoidedDocuments>"), "HASH");
    DarDeBajaService service;
    EmitirComprobanteService emitir;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        emitir = new EmitirComprobanteService(comprobantes, series, tenants, storage, new Fakes.Ubl(), xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos, bajas, new Fakes.Idempotencias(), new Fakes.Tope());
        service = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
    }

    private Comprobante facturaAceptada() {
        return emitir.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
    }

    @Test void bajaAceptadaEnElActoAnulaElComprobante() {
        Comprobante f = facturaAceptada();
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Error en el RUC del cliente");
        assertThat(b.identificador()).isEqualTo("RA-20260913-1");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(b.ticket()).isEqualTo("T-1");
        assertThat(b.cdr().codigo()).isEqualTo("0");
        assertThat(b.xmlKey()).isEqualTo(tenantId + "/2026/09/20100066603-RA-20260913-1.xml");
        assertThat(new String(storage.leer(b.xmlKey()))).contains("<ds:Signature/>");
        assertThat(storage.leer(b.cdrKey())).isNotEmpty();
        assertThat(validado[0]).contains("RA-20260913-1");
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RA-20260913-1");
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(outbox.filas).isEmpty();
        // El correlativo del día avanza y una segunda baja del mismo comprobante (ya ANULADO) no procede.
        assertThatThrownBy(() -> service.solicitar(tenantId, f.id(), "otra vez")).isInstanceOf(DomainException.class).hasMessageContaining("2398");
    }

    /** #20: una boleta aceptada se anula con un resumen diario (RC): mismo ciclo, su propio XML y esquema, y la boleta queda ANULADA. */
    @Test void unaBoletaSeAnulaConUnResumenDiario() {
        Comprobante f = facturaAceptada();   // el RA de la factura y el RC de la boleta comparten el correlativo del día
        service.solicitar(tenantId, f.id(), "Error en el RUC");
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        ComunicacionBaja[] validada = new ComunicacionBaja[1];
        XsdValidator porTipo = new XsdValidator() {
            public void validar(String xml, TipoDocumento tipo) {}
            public void validarBaja(String xml, ComunicacionBaja b) { validado[0] = xml; validada[0] = b; }
        };
        DarDeBajaService conEsquema = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), porTipo, signer, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        Comprobante boleta = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));

        ComunicacionBaja rc = conEsquema.solicitar(tenantId, boleta.id(), "Se cobró dos veces");

        assertThat(rc.identificador()).isEqualTo("RC-20260913-2");
        assertThat(rc.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(rc.xmlKey()).isEqualTo(tenantId + "/2026/09/20100066603-RC-20260913-2.xml");
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RC-20260913-2");
        assertThat(validado[0]).startsWith("<SummaryDocuments>");
        assertThat(validada[0].resumenDiario()).as("se valida contra el esquema del resumen").isTrue();
        assertThat(comprobantes.buscar(tenantId, boleta.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    /**
     * 274-H1: una boleta firmada que nadie envió dentro del envío individual (5 días, #290) no se pierde ni se reemplaza: al enviarla, SUNAT ya no la recibe sola
     * (1079) y khipu la informa en un resumen diario con su línea en estado 1. Cuando SUNAT acepta el resumen, la boleta queda ACEPTADA.
     */
    @Test void unaBoletaPasadaDelEnvioIndividualSeInformaEnUnResumenDeAltaYQuedaAceptada() {
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        Comprobante boleta = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false));
        Clock seisDiasDespues = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneId.of("America/Lima"));
        DarDeBajaService resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, seisDiasDespues);
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, seisDiasDespues,
                RechazoDeSolRepository.NINGUNO, resumen);
        int enviadosSolos = gateway.enviados;

        Comprobante resuelta = enviar.enviar(tenantId, boleta.id());

        assertThat(gateway.enviados).as("no se intenta sendBill: SUNAT la rechazaría con 1079").isEqualTo(enviadosSolos);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RC-20260919-1");
        assertThat(validado[0]).startsWith("<SummaryDocuments>");
        ComunicacionBaja alta = bajas.deComprobante(tenantId, boleta.id()).get(0);
        assertThat(alta.alta()).isTrue();
        assertThat(alta.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(resuelta.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(comprobantes.buscar(tenantId, boleta.id()).orElseThrow().cdrKey()).isEqualTo(alta.cdrKey());
    }

    /**
     * #293: una nota de boleta sigue a la boleta. Firmada sin enviar, al 6.º día se informa en el resumen diario (estado 1) y queda ACEPTADA; ya aceptada,
     * se anula en el resumen diario (estado 3), nunca en un RA (2310).
     */
    @Test void unaNotaDeBoletaSeInformaYSeAnulaEnElResumenDiario() {
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_CREDITO, "BC01", 0, true));
        Comprobante boleta = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true));
        Comprobante nc = emitir.emitirNota(tenantId, new EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "BC01", null, LocalDate.of(2026, 9, 13), "B001", boleta.numero(), "07",
                "Devolución", null, null, List.of(), null, false));
        Clock seisDiasDespues = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneId.of("America/Lima"));
        DarDeBajaService resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, seisDiasDespues);
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, seisDiasDespues,
                RechazoDeSolRepository.NINGUNO, resumen);

        assertThat(enviar.enviar(tenantId, nc.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RC-20260919-1");
        assertThat(bajas.deComprobante(tenantId, nc.id()).get(0).alta()).isTrue();

        ComunicacionBaja baja = resumen.solicitar(tenantId, nc.id(), "Devolución registrada por error");
        assertThat(baja.identificador()).isEqualTo("RC-20260919-2");
        assertThat(validado[0]).startsWith("<SummaryDocuments>");
        assertThat(comprobantes.buscar(tenantId, nc.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    /**
     * #331: pasado el 7.º día la boleta, firmada o con el envío fallido, ya no se cierra como fuera de plazo: va a un resumen de alta y SUNAT la acepta con la
     * observación 4443. Va en un RC propio, separado del de una boleta en plazo del mismo día: si SUNAT rechazara el resumen tardío entero, la otra no cae
     * con él. Falta validarlo en e-beta.
     */
    @Test void pasadoElSeptimoDiaLaBoletaSeInformaEnUnResumenPropioYQuedaAceptadaConObservaciones() {
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        Comprobante tardia = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false));
        Clock diaOcho = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneId.of("America/Lima"));   // 13 + 8
        Comprobante enPlazo = Comprobante.persistido(UUID.randomUUID(), tenantId, TipoDocumento.BOLETA, "B001", 99L, LocalDate.of(2026, 9, 15), EstadoDocumento.ERROR_ENVIO,
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)))
                .firma("h", "20100066603-03-B001-99", tardia.xmlKey()).rehidratar();
        comprobantes.guardar(enPlazo);
        DarDeBajaService resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, diaOcho);
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, diaOcho, RechazoDeSolRepository.NINGUNO, resumen);
        int enviadosSolos = gateway.enviados;
        cdrs.cdr = new Cdr("0", "El Resumen diario RC-20260921-1, ha sido aceptado", List.of("4443 - El comprobante fue informado fuera del plazo establecido"));

        Comprobante resuelta = enviar.enviar(tenantId, tardia.id());

        assertThat(gateway.enviados).as("ni sendBill (1079) ni FUERA_DE_PLAZO: va al resumen").isEqualTo(enviadosSolos);
        assertThat(resuelta.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(resuelta.cdr().observaciones()).singleElement().asString().startsWith("4443");
        ComunicacionBaja alta = bajas.deComprobante(tenantId, tardia.id()).get(0);
        assertThat(alta.alta()).isTrue();
        assertThat(alta.fueraDePlazo()).isTrue();
        assertThat(alta.identificador()).isEqualTo("RC-20260921-1");
        assertThat(validado[0]).startsWith("<SummaryDocuments>");
        // El historial de la boleta dice que se informó fuera de plazo: el servicio le pasa a la boleta lo que sabe el resumen.
        assertThat(comprobantes.eventosDe(tenantId, tardia.id())).filteredOn(e -> e.estadoNuevo() == EstadoDocumento.ENVIADO).singleElement()
                .satisfies(e -> assertThat(e.detalle()).contains("RC-20260921-1 fuera de plazo").contains("4443"));

        // La boleta en plazo del mismo día va en otro RC (correlativo siguiente): nunca comparten resumen.
        cdrs.cdr = new Cdr("0", "El Resumen diario RC-20260921-2, ha sido aceptado", List.of());
        assertThat(enviar.enviar(tenantId, enPlazo.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        ComunicacionBaja otra = bajas.deComprobante(tenantId, enPlazo.id()).get(0);
        assertThat(otra.identificador()).isEqualTo("RC-20260921-2");
        assertThat(otra.fueraDePlazo()).isFalse();
        assertThat(comprobantes.eventosDe(tenantId, enPlazo.id())).filteredOn(e -> e.estadoNuevo() == EstadoDocumento.ENVIADO).singleElement()
                .satisfies(e -> assertThat(e.detalle()).doesNotContain("fuera de plazo"));
        // Y la boleta tardía, ya aceptada, no se puede anular: la baja sí tiene tope (2957).
        assertThatThrownBy(() -> resumen.solicitar(tenantId, tardia.id(), "Se cobró dos veces")).hasMessageContaining("2957");
    }

    /**
     * #290, el borde en hora de Lima: a las 23:30 del 5.º día (04:30Z del 6.º) la boleta todavía va sola con sendBill. Con un reloj en UTC ya sería el 6.º
     * día y se iría al resumen un día antes.
     */
    @Test void aLas2330DeLimaDelQuintoDiaLaBoletaTodaviaVaSola() {
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        Comprobante boleta = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false));
        Clock finDelQuintoDiaEnLima = Clock.fixed(Instant.parse("2026-09-19T04:30:00Z"), ZoneId.of("America/Lima"));
        DarDeBajaService resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, finDelQuintoDiaEnLima);
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, finDelQuintoDiaEnLima,
                RechazoDeSolRepository.NINGUNO, resumen);
        int enviadosSolos = gateway.enviados;

        enviar.enviar(tenantId, boleta.id());

        assertThat(gateway.enviados).as("sendBill, no un resumen diario").isEqualTo(enviadosSolos + 1);
        assertThat(bajas.deComprobante(tenantId, boleta.id())).isEmpty();
    }

    @Test void sunatSigueProcesando_quedaEnviadaYElOutboxLaReconsulta() {
        Comprobante f = facturaAceptada();
        gateway.statusCode = "98";
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Error en el RUC");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ENVIADA);
        assertThat(b.ultimoError()).startsWith("98");
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(outbox.filas).hasSize(1);
        assertThat(outbox.filas.get(0).accion()).isEqualTo("BAJA");
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Fakes.CLOCK.instant().plus(DarDeBajaService.REINTENTO_CONSULTA));
        // Mientras está en curso no se admite otra baja del mismo comprobante.
        assertThatThrownBy(() -> service.solicitar(tenantId, f.id(), "otra")).hasMessageContaining("en curso");

        gateway.statusCode = "0";
        ComunicacionBaja lista = service.continuar(tenantId, b.id());
        assertThat(lista.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(gateway.consultas).isEqualTo(2);
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    /**
     * Una excepción no prevista (red hacia S3, SDK) al enviar: antes salía antes de la transacción final y la baja quedaba
     * GENERADA sin fila de outbox —«en curso» para siempre, sin nadie que la reintentara—. Ahora es ERROR_ENVIO con reintento.
     */
    @Test void unaFallaNoPrevistaAlEnviarDejaErrorEnvioConReintento() {
        Comprobante f = facturaAceptada();
        gateway.fallaResumen = new RuntimeException("conexión con S3 perdida");
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Error en el RUC");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ERROR_ENVIO);
        assertThat(b.ultimoError()).startsWith("INFRA - RuntimeException");
        assertThat(outbox.filas).hasSize(1);
        assertThat(outbox.filas.get(0).accion()).isEqualTo("BAJA");
        // Y se recupera al reintentar.
        gateway.fallaResumen = null;
        assertThat(service.continuar(tenantId, b.id()).estado()).isEqualTo(EstadoBaja.ACEPTADA);
    }

    /** Con una baja en curso la factura sigue ACEPTADA, pero una nota caería sobre un documento anulado si SUNAT la acepta (2120). */
    @Test void unaNotaSobreFacturaConBajaEnCursoSeRechaza() {
        Comprobante f = facturaAceptada();
        gateway.statusCode = "98";
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Error en el RUC");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ENVIADA);
        series.crear(new Serie(tenantId, TipoDocumento.NOTA_CREDITO, "FC01", 0, true));
        assertThatThrownBy(() -> emitir.emitirNota(tenantId, new pe.factura.application.port.in.EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "Anulación", null, null, List.of(), null, true)))
                .isInstanceOf(DomainException.class).hasMessageContaining("2120").hasMessageContaining("baja en curso").hasMessageContaining(b.identificador());
        // Rechazada por SUNAT, la baja ya no bloquea.
        gateway.statusCode = "0";
        gateway.respuesta = "cdr-rechazo".getBytes();
        cdrs.cdr = new Cdr("2323", "ya informado", List.of());
        assertThat(service.continuar(tenantId, b.id()).estado()).isEqualTo(EstadoBaja.RECHAZADA);
        assertThat(emitir.emitirNota(tenantId, new pe.factura.application.port.in.EmitirNotaCommand(TipoDocumento.NOTA_CREDITO, "FC01", null, LocalDate.of(2026, 9, 13), "F001", f.numero(), "01", "Anulación", null, null, List.of(), null, true)).estado()).isNotNull();
    }

    @Test void dosContinuarConcurrentesSobreLaMismaBajaNoFallanAlAnular() {
        Comprobante f = facturaAceptada();
        gateway.statusCode = "98";
        ComunicacionBaja enviada = service.solicitar(tenantId, f.id(), "Error en el RUC");
        assertThat(enviada.estado()).isEqualTo(EstadoBaja.ENVIADA);
        // Dos copias de la misma baja ENVIADA (el GET del usuario y el outbox) obtienen el CDR 0 a la vez: la segunda no debe
        // romper con TRANSICION_INVALIDA porque el comprobante ya quedó ANULADO por la primera.
        ComunicacionBaja copiaDelWorker = ComunicacionBaja.rehidratar(enviada.id(), tenantId, enviada.fechaGeneracion(), enviada.correlativo(), enviada.comprobanteId(), enviada.tipoComprobante(), enviada.deBoleta(),
                enviada.serie(), enviada.numero(), enviada.fechaReferencia(), enviada.motivo(), enviada.condicion(), EstadoBaja.ENVIADA, enviada.ticket(), enviada.xmlKey(), null, null, enviada.intentos(), enviada.ultimoError());
        gateway.statusCode = "0";
        ComunicacionBaja primera = service.continuar(tenantId, enviada.id());
        assertThat(primera.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
        bajas.guardar(copiaDelWorker);   // el worker sigue viendo la baja ENVIADA que leyó antes
        ComunicacionBaja segunda = service.continuar(tenantId, enviada.id());
        assertThat(segunda.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    @Test void rechazoDeSunatDejaRechazadaYElComprobanteSigueAceptado() {
        Comprobante f = facturaAceptada();
        gateway.statusCode = "99";
        cdrs.cdr = new Cdr("2957", "Fuera de plazo", List.of());
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Tarde");
        assertThat(b.estado()).isEqualTo(EstadoBaja.RECHAZADA);
        assertThat(b.cdr().codigo()).isEqualTo("2957");
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(outbox.filas).isEmpty();
        // Un fault definitivo al enviar también rechaza sin CDR.
        cdrs.cdr = new Cdr("0", "aceptada", List.of());
        gateway.statusCode = "0";
        Comprobante g = facturaAceptada();
        gateway.fallaResumen = new SunatRechazoException("1033", "registrado previamente");
        assertThat(service.solicitar(tenantId, g.id(), "Duplicado").estado()).isEqualTo(EstadoBaja.RECHAZADA);
    }

    @Test void sunatCaidoAlEnviar_errorEnvioConBackoffYReenvio() {
        Comprobante f = facturaAceptada();
        gateway.fallaResumen = new SunatTransientException("0109", "timeout");
        ComunicacionBaja b = service.solicitar(tenantId, f.id(), "Error");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ERROR_ENVIO);
        assertThat(b.ticket()).isNull();
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Backoff.siguiente(1, Fakes.CLOCK.instant()));
        gateway.fallaResumen = null;
        assertThat(service.continuar(tenantId, b.id()).estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    /**
     * #302: khipu admite la baja el día 7, pero SUNAT mide el plazo con el día en que la recibe (2957: RA fila 44, RC fila 37). Si el envío falla y el
     * reintento llega el día 8, se reenvía el mismo archivo, con la fecha de generación original (2346: la del nombre y la del IssueDate deben coincidir),
     * y el 2957 de SUNAT la deja RECHAZADA con el comprobante vigente; khipu tampoco admite una nueva. Es lo que la guía y la OpenAPI avisan.
     */
    @Test void unaBajaAdmitidaElDia7QueSeReintentaElDia8ReenviaElMismoArchivoYSunatDecide() {
        Comprobante f = facturaAceptada();   // emitida el 2026-09-13
        Clock dia7 = Clock.fixed(Instant.parse("2026-09-21T04:00:00Z"), ZoneId.of("America/Lima"));   // 2026-09-20 23:00 en Lima
        Clock dia8 = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneId.of("America/Lima"));
        DarDeBajaService alDia7 = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, dia7);
        DarDeBajaService alDia8 = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, dia8);
        gateway.fallaResumen = new SunatTransientException("0109", "timeout");
        ComunicacionBaja b = alDia7.solicitar(tenantId, f.id(), "Error en el RUC");
        assertThat(b.estado()).isEqualTo(EstadoBaja.ERROR_ENVIO);
        assertThat(b.identificador()).isEqualTo("RA-20260920-1");

        gateway.fallaResumen = null;
        gateway.statusCode = "99";
        cdrs.cdr = new Cdr("2957", "El comprobante no puede ser dado de baja por exceder el plazo desde su fecha de emision", List.of());
        ComunicacionBaja reintento = alDia8.continuar(tenantId, b.id());

        assertThat(gateway.ultimoNombre).as("el mismo archivo, con la fecha del día 7").isEqualTo("20100066603-RA-20260920-1");
        assertThat(reintento.estado()).isEqualTo(EstadoBaja.RECHAZADA);
        assertThat(reintento.cdr().codigo()).isEqualTo("2957");
        assertThat(comprobantes.buscar(tenantId, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThatThrownBy(() -> alDia8.solicitar(tenantId, f.id(), "Error en el RUC")).hasMessageContaining("2957");
    }

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 2026-12-31 23:50 en Lima (en UTC ya es 2027). */
    static final Clock FIN_DE_ANIO = Clock.fixed(Instant.parse("2027-01-01T04:50:00Z"), LIMA);
    /** 2026-12-31 23:59:59 en Lima: todavía 2026, aunque en UTC sea 2027-01-01 04:59. */
    static final Clock ULTIMO_SEGUNDO = Clock.fixed(Instant.parse("2027-01-01T04:59:59Z"), LIMA);
    /** 2027-01-01 00:10 en Lima: el código 05 de las gratuitas ya no vale. */
    static final Clock ANIO_NUEVO = Clock.fixed(Instant.parse("2027-01-01T05:10:00Z"), LIMA);

    private DarDeBajaService alReloj(Clock clock) {
        return new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, clock);
    }

    private Comprobante boletaConGratuitas(long numero, LocalDate emision, EstadoDocumento estado) {
        Comprobante c = Comprobante.persistido(UUID.randomUUID(), tenantId, TipoDocumento.BOLETA, "B001", numero, emision, estado, new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("M1", "Muestra", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO_BONIFICACION)))
                .firma("h", "20100066603-03-B001-" + numero, tenantId + "/2026/12/20100066603-03-B001-" + numero + ".xml").rehidratar();
        comprobantes.guardar(c);
        return c;
    }

    /**
     * #338: un RC con gratuitas generado el 31-12 (con el código 05) cuyo envío falla y se reintenta el 01-01 ya no se reenvía: SUNAT lo rechazaría por el 05.
     * Se descarta y se genera otro con la fecha y el formato del día (nuevo correlativo); la fila de outbox pasa a apuntar al nuevo. Hasta las 23:59 de Lima
     * todavía es 2026, aunque en UTC ya sea el 1: ahí se reenvía el mismo.
     */
    @Test void unaBajaConGratuitasGeneradaEl31QueSeReintentaEl1DeEneroSeRegeneraConFormatoNuevo() {
        Comprobante boleta = boletaConGratuitas(10, LocalDate.of(2026, 12, 28), EstadoDocumento.ACEPTADO);
        gateway.fallaResumen = new SunatTransientException("0109", "SUNAT no disponible");
        ComunicacionBaja vieja = alReloj(FIN_DE_ANIO).solicitar(tenantId, boleta.id(), "Se cobró dos veces");
        assertThat(vieja.identificador()).isEqualTo("RC-20261231-1");
        assertThat(vieja.estado()).isEqualTo(EstadoBaja.ERROR_ENVIO);

        alReloj(ULTIMO_SEGUNDO).continuar(tenantId, vieja.id());
        assertThat(gateway.ultimoNombre).as("31-12 en Lima: el mismo RC").isEqualTo("20100066603-RC-20261231-1");
        assertThat(bajas.deComprobante(tenantId, boleta.id())).hasSize(1);

        gateway.fallaResumen = null;
        ComunicacionBaja nueva = alReloj(ANIO_NUEVO).continuar(tenantId, vieja.id());

        assertThat(nueva.id()).isNotEqualTo(vieja.id());
        assertThat(nueva.identificador()).isEqualTo("RC-20270101-1");
        assertThat(nueva.gratuitasPorAfectacion()).isTrue();
        assertThat(nueva.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RC-20270101-1");
        assertThat(validado[0]).isEqualTo("<SummaryDocuments>RC-20270101-1</SummaryDocuments>");
        assertThat(nueva.xmlKey()).isEqualTo(tenantId + "/2027/01/20100066603-RC-20270101-1.xml");
        ComunicacionBaja descartada = bajas.buscar(tenantId, vieja.id()).orElseThrow();
        assertThat(descartada.estado()).isEqualTo(EstadoBaja.DESCARTADA);
        assertThat(descartada.ultimoError()).contains("RC-20270101-1");
        assertThat(bajas.deComprobante(tenantId, boleta.id()).stream().filter(ComunicacionBaja::pendiente)).as("nunca dos RC vivos").isEmpty();
        assertThat(comprobantes.buscar(tenantId, boleta.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(outbox.filas).singleElement().satisfies(f -> {
            assertThat(f.agregadoId()).as("la fila sigue al RC nuevo").isEqualTo(nueva.id());
            assertThat(f.accion()).isEqualTo(DarDeBajaService.ACCION_BAJA);
        });
        // Consultar después el RC viejo ya no lo reintenta ni genera otro.
        int resumenes = gateway.resumenes;
        assertThat(alReloj(ANIO_NUEVO).continuar(tenantId, vieja.id()).estado()).isEqualTo(EstadoBaja.DESCARTADA);
        assertThat(gateway.resumenes).isEqualTo(resumenes);
        assertThat(bajas.deComprobante(tenantId, boleta.id())).hasSize(2);
    }

    /**
     * #338 con #331: una boleta que se informa en un alta el 7.º día (31-12), con el envío fallido, se regenera el 8.º (01-01) y ese RC nuevo sale fuera de
     * plazo: SUNAT la acepta con la 4443 en vez de rechazar el RC viejo por el 05.
     */
    @Test void unAltaConGratuitasRegeneradaEl1DeEneroSaleFueraDePlazoYLaBoletaQuedaAceptadaConObservaciones() {
        Comprobante boleta = boletaConGratuitas(11, LocalDate.of(2026, 12, 24), EstadoDocumento.ERROR_ENVIO);
        gateway.fallaResumen = new SunatTransientException("0109", "SUNAT no disponible");
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, FIN_DE_ANIO,
                RechazoDeSolRepository.NINGUNO, alReloj(FIN_DE_ANIO));
        assertThat(enviar.enviar(tenantId, boleta.id()).estado()).isEqualTo(EstadoDocumento.ENVIADO);
        ComunicacionBaja vieja = bajas.deComprobante(tenantId, boleta.id()).get(0);
        assertThat(vieja.identificador()).isEqualTo("RC-20261231-1");
        assertThat(vieja.fueraDePlazo()).isFalse();

        gateway.fallaResumen = null;
        cdrs.cdr = new Cdr("0", "El Resumen diario RC-20270101-1, ha sido aceptado", List.of("4443 - El comprobante fue informado fuera del plazo establecido"));
        ComunicacionBaja nueva = alReloj(ANIO_NUEVO).continuar(tenantId, vieja.id());

        assertThat(nueva.identificador()).isEqualTo("RC-20270101-1");
        assertThat(nueva.alta()).isTrue();
        assertThat(nueva.fueraDePlazo()).isTrue();
        assertThat(nueva.motivo()).contains("4443");
        assertThat(nueva.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(bajas.buscar(tenantId, vieja.id()).orElseThrow().estado()).isEqualTo(EstadoBaja.DESCARTADA);
        Comprobante resuelta = comprobantes.buscar(tenantId, boleta.id()).orElseThrow();
        assertThat(resuelta.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(resuelta.cdrKey()).isEqualTo(nueva.cdrKey());
        assertThat(outbox.filas).singleElement().extracting(Fakes.Outbox.Fila::agregadoId).isEqualTo(nueva.id());
    }

    /**
     * #338 con #302: la baja admitida el 7.º día (31-12) que caduca el 8.º (01-01) ya no se puede regenerar en plazo: SUNAT la rechazaría con 2957. Queda
     * RECHAZADA con ese código, sin enviar nada; la boleta sigue vigente y la fila de outbox se va.
     */
    @Test void unaBajaConGratuitasQueCaducaConElPlazoVencidoQuedaRechazadaPor2957SinEnviarse() {
        Comprobante boleta = boletaConGratuitas(12, LocalDate.of(2026, 12, 24), EstadoDocumento.ACEPTADO);
        gateway.fallaResumen = new SunatTransientException("0109", "SUNAT no disponible");
        ComunicacionBaja baja = alReloj(FIN_DE_ANIO).solicitar(tenantId, boleta.id(), "Se cobró dos veces");
        assertThat(outbox.filas).hasSize(1);
        gateway.fallaResumen = null;
        int resumenes = gateway.resumenes;

        ComunicacionBaja resuelta = alReloj(ANIO_NUEVO).continuar(tenantId, baja.id());

        assertThat(resuelta.id()).isEqualTo(baja.id());
        assertThat(resuelta.estado()).isEqualTo(EstadoBaja.RECHAZADA);
        assertThat(resuelta.cdr().codigo()).isEqualTo("2957");
        assertThat(gateway.resumenes).as("no se envía nada").isEqualTo(resumenes);
        assertThat(bajas.deComprobante(tenantId, boleta.id())).hasSize(1);
        assertThat(bajas.buscar(tenantId, baja.id()).orElseThrow().estado()).isEqualTo(EstadoBaja.RECHAZADA);
        assertThat(comprobantes.buscar(tenantId, boleta.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(outbox.filas).isEmpty();
    }

    /**
     * #338, concurrencia: el worker y un reintento manual leen el mismo RC caducado. El primero lo reemplaza; el segundo, al releerlo con el comprobante
     * bloqueado, lo encuentra descartado y devuelve el que lo reemplazó, sin generar un tercero ni volver a enviar.
     */
    @Test void dosReintentosDelMismoResumenCaducadoGeneranUnSoloReemplazo() {
        Comprobante boleta = boletaConGratuitas(13, LocalDate.of(2026, 12, 28), EstadoDocumento.ACEPTADO);
        gateway.fallaResumen = new SunatTransientException("0109", "SUNAT no disponible");
        ComunicacionBaja vieja = alReloj(FIN_DE_ANIO).solicitar(tenantId, boleta.id(), "Se cobró dos veces");
        gateway.fallaResumen = null;
        gateway.statusCode = "98";   // SUNAT todavía procesa el RC nuevo
        // La lectura del worker, antes de que nadie la toque (una copia: el repositorio en memoria comparte la instancia).
        ComunicacionBaja copiaDelWorker = ComunicacionBaja.rehidratar(vieja.id(), tenantId, vieja.fechaGeneracion(), vieja.correlativo(), vieja.comprobanteId(), vieja.tipoComprobante(),
                vieja.deBoleta(), vieja.serie(), vieja.numero(), vieja.fechaReferencia(), vieja.motivo(), vieja.condicion(), vieja.estado(), vieja.ticket(), vieja.xmlKey(), null, null,
                vieja.intentos(), vieja.ultimoError());
        ComunicacionBaja primera = alReloj(ANIO_NUEVO).continuar(tenantId, vieja.id());
        assertThat(primera.estado()).isEqualTo(EstadoBaja.ENVIADA);
        int resumenes = gateway.resumenes;

        // El worker sigue con la copia que leyó: el repositorio se la devuelve una vez (la lectura previa) y después lo que hay.
        var conLecturaVieja = new pe.factura.application.port.out.BajaRepository() {
            boolean primeraLectura = true;
            public void guardar(ComunicacionBaja b) { bajas.guardar(b); }
            public java.util.Optional<ComunicacionBaja> buscar(UUID t, UUID id) {
                if (primeraLectura && id.equals(vieja.id())) { primeraLectura = false; return java.util.Optional.of(copiaDelWorker); }
                return bajas.buscar(t, id);
            }
            public List<ComunicacionBaja> deComprobante(UUID t, UUID c) { return bajas.deComprobante(t, c); }
            public int siguienteCorrelativo(UUID t, LocalDate f) { return bajas.siguienteCorrelativo(t, f); }
        };
        DarDeBajaService worker = new DarDeBajaService(conLecturaVieja, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, ANIO_NUEVO);
        ComunicacionBaja segunda = worker.continuar(tenantId, vieja.id());

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(gateway.resumenes).as("no reenvía").isEqualTo(resumenes);
        assertThat(bajas.deComprobante(tenantId, boleta.id())).hasSize(2);
        assertThat(bajas.deComprobante(tenantId, boleta.id()).stream().filter(ComunicacionBaja::pendiente)).singleElement()
                .extracting(ComunicacionBaja::id).isEqualTo(primera.id());
        assertThat(outbox.filas).singleElement().extracting(Fakes.Outbox.Fila::agregadoId).isEqualTo(primera.id());
    }

    private DarDeBajaService alReloj(Clock clock, pe.factura.application.port.out.BajaRepository repo) {
        return new DarDeBajaService(repo, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, clock);
    }

    /** Como la base: cada proceso lee y guarda su propia copia de la fila, no la instancia que tiene el otro. */
    static final class BajasPorCopia implements pe.factura.application.port.out.BajaRepository {
        final Fakes.Bajas datos;
        BajasPorCopia(Fakes.Bajas datos) { this.datos = datos; }
        static ComunicacionBaja copia(ComunicacionBaja b) {
            return ComunicacionBaja.rehidratar(b.id(), b.tenantId(), b.fechaGeneracion(), b.correlativo(), b.comprobanteId(), b.tipoComprobante(), b.deBoleta(), b.serie(),
                    b.numero(), b.fechaReferencia(), b.motivo(), b.condicion(), b.estado(), b.ticket(), b.xmlKey(), b.cdrKey(), b.cdr(), b.intentos(), b.ultimoError());
        }
        public void guardar(ComunicacionBaja b) { datos.guardar(copia(b)); }
        public java.util.Optional<ComunicacionBaja> buscar(UUID t, UUID id) { return datos.buscar(t, id).map(BajasPorCopia::copia); }
        public List<ComunicacionBaja> deComprobante(UUID t, UUID c) { return datos.deComprobante(t, c).stream().map(BajasPorCopia::copia).toList(); }
        public int siguienteCorrelativo(UUID t, LocalDate f) { return datos.siguienteCorrelativo(t, f); }
    }

    /**
     * #353-H2: el envío del RC viejo arranca el 31-12 a las 23:59:59 (todavía no caducó) y, mientras SUNAT contesta, otro proceso lo descarta y lo reemplaza
     * el 01-01. Al volver, ese envío no puede pisar el DESCARTADA ni volver a programar la fila del viejo: quedarían dos RC vivos. El ticket que SUNAT le dio
     * queda anotado en el descartado, y lo que sigue es el reemplazo.
     */
    @Test void unEnvioDelRcViejoQueTerminaDespuesDelReemplazoNoLoResucita() {
        BajasPorCopia db = new BajasPorCopia(bajas);
        Comprobante boleta = boletaConGratuitas(14, LocalDate.of(2026, 12, 28), EstadoDocumento.ACEPTADO);
        gateway.fallaResumen = new SunatTransientException("0109", "SUNAT no disponible");
        ComunicacionBaja vieja = alReloj(FIN_DE_ANIO, db).solicitar(tenantId, boleta.id(), "Se cobró dos veces");
        gateway.fallaResumen = null;
        gateway.statusCode = "98";
        ComunicacionBaja[] reemplazo = new ComunicacionBaja[1];
        gateway.duranteElEnvio = () -> reemplazo[0] = alReloj(ANIO_NUEVO, db).continuar(tenantId, vieja.id());

        ComunicacionBaja enVuelo = alReloj(ULTIMO_SEGUNDO, db).continuar(tenantId, vieja.id());

        assertThat(reemplazo[0].identificador()).isEqualTo("RC-20270101-1");
        ComunicacionBaja guardada = bajas.buscar(tenantId, vieja.id()).orElseThrow();
        assertThat(guardada.estado()).as("el envío en vuelo no pisa el descarte").isEqualTo(EstadoBaja.DESCARTADA);
        assertThat(guardada.ticket()).isEqualTo("T-1");
        assertThat(guardada.ultimoError()).contains("RC-20270101-1").contains("T-1");
        assertThat(bajas.deComprobante(tenantId, boleta.id()).stream().filter(ComunicacionBaja::pendiente)).as("nunca dos RC vivos").singleElement()
                .extracting(ComunicacionBaja::id).isEqualTo(reemplazo[0].id());
        assertThat(outbox.filas).singleElement().extracting(Fakes.Outbox.Fila::agregadoId).isEqualTo(reemplazo[0].id());
        assertThat(enVuelo.id()).as("devuelve lo que sigue").isEqualTo(reemplazo[0].id());
    }

    /**
     * #353-H3: un RC GENERADA sin fila de outbox (pedido el 31 a las 23:59:59 y continuado ya el 1, o un proceso caído entre las dos transacciones) que
     * caduca y cuya regeneración falla (storage, firma, XSD): la excepción salía de `continuar` sin dejar nada en la cola, y la baja quedaba «en curso» para
     * siempre (bloquea otra baja y las notas). Queda en ERROR_ENVIO con el motivo y con su fila de outbox; el reintento la regenera.
     */
    @Test void siLaRegeneracionFallaElRcViejoQuedaEnLaColaParaReintentarse() {
        Comprobante boleta = boletaConGratuitas(15, LocalDate.of(2026, 12, 28), EstadoDocumento.ACEPTADO);
        ComunicacionBaja vieja = ComunicacionBaja.crear(boleta, 1, "Se cobró dos veces", FIN_DE_ANIO);
        vieja.firmar(tenantId + "/2026/12/20100066603-RC-20261231-1.xml");
        storage.guardar(vieja.xmlKey(), "<SummaryDocuments/>".getBytes());
        bajas.guardar(vieja);
        Fakes.Storage caido = new Fakes.Storage() {
            public void guardar(String k, byte[] c) { if (k.contains("RC-2027")) throw new IllegalStateException("S3 no responde"); storage.guardar(k, c); }
            public byte[] leer(String k) { return storage.leer(k); }
        };
        DarDeBajaService conStorageCaido = new DarDeBajaService(bajas, comprobantes, tenants, caido, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, ANIO_NUEVO);

        ComunicacionBaja resultado = conStorageCaido.continuar(tenantId, vieja.id());

        assertThat(resultado.id()).isEqualTo(vieja.id());
        assertThat(resultado.estado()).isEqualTo(EstadoBaja.ERROR_ENVIO);
        assertThat(resultado.ultimoError()).contains("S3 no responde");
        assertThat(gateway.resumenes).as("no se envía el RC con el 05").isZero();
        assertThat(bajas.deComprobante(tenantId, boleta.id())).hasSize(1);
        assertThat(outbox.filas).singleElement().extracting(Fakes.Outbox.Fila::agregadoId).isEqualTo(vieja.id());

        // Con el storage de vuelta, el reintento la reemplaza.
        ComunicacionBaja nueva = alReloj(ANIO_NUEVO).continuar(tenantId, vieja.id());
        assertThat(nueva.identificador()).isEqualTo("RC-20270101-1");
        assertThat(bajas.buscar(tenantId, vieja.id()).orElseThrow().estado()).isEqualTo(EstadoBaja.DESCARTADA);
    }

    @Test void fueraDePlazoNoAceptadoOBoleta() {
        Comprobante f = facturaAceptada();
        Clock tarde = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneId.of("America/Lima"));   // 8 días después
        DarDeBajaService tardio = new DarDeBajaService(bajas, comprobantes, tenants, storage, new Fakes.Ubl(), xsd, signer, gateway, cdrs, outbox, Fakes.UOW, tarde);
        assertThatThrownBy(() -> tardio.solicitar(tenantId, f.id(), "Error")).hasMessageContaining("2957");
        Comprobante firmado = emitir.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false));
        assertThatThrownBy(() -> service.solicitar(tenantId, firmado.id(), "Error")).hasMessageContaining("2398").hasMessageContaining("FIRMADO");
        assertThatThrownBy(() -> service.solicitar(tenantId, f.id(), "ab")).hasMessageContaining("2315");
        assertThatThrownBy(() -> service.solicitar(tenantId, UUID.randomUUID(), "Error")).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(bajas.datos).isEmpty();
    }
}
