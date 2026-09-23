package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.out.FirmaResultado;
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
        public void validarBaja(String xml) { validado[0] = xml; }
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("</VoidedDocuments>", "<ds:Signature/></VoidedDocuments>"), "HASH");
    DarDeBajaService service;
    EmitirComprobanteService emitir;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        emitir = new EmitirComprobanteService(comprobantes, series, tenants, storage, new Fakes.Ubl(), xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos, bajas);
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
        ComunicacionBaja copiaDelWorker = ComunicacionBaja.rehidratar(enviada.id(), tenantId, enviada.fechaGeneracion(), enviada.correlativo(), enviada.comprobanteId(), enviada.tipoComprobante(),
                enviada.serie(), enviada.numero(), enviada.fechaReferencia(), enviada.motivo(), EstadoBaja.ENVIADA, enviada.ticket(), enviada.xmlKey(), null, null, enviada.intentos(), enviada.ultimoError());
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
