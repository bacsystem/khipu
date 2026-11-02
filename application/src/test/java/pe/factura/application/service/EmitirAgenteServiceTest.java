package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirAgenteCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.in.ValidarCompradorUseCase;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.DesignacionDeAgente;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retención (20) y percepción (40), #40: se emiten por el mismo camino que la factura —número con la serie R###/P### bloqueada, XML, firma, XSD, storage y envío—, solo si la empresa
 * es agente de ese tipo, con espacios de idempotencia propios, y lo que el dominio rechaza no consume número.
 */
class EmitirAgenteServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Series series = new Fakes.Series();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    Fakes.Ubl ubl = new Fakes.Ubl();
    Fakes.Tope tope = new Fakes.Tope();
    Fakes.Idempotencias idempotencias = new Fakes.Idempotencias();
    List<TipoDocumento> validados = new ArrayList<>();
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) { validados.add(tipo); }
        public void validarBaja(String xml, ComunicacionBaja baja) {}
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml, "HASH");
    List<String> consultasAlPadron = new ArrayList<>();
    ValidarCompradorUseCase padron = new ValidarCompradorUseCase() {
        public void exigirQueExista(Receptor r) { consultasAlPadron.add("1083 " + r.numDoc()); }
        public void exigirQueExista(Receptor r, String regla, String quien) { consultasAlPadron.add(regla + " " + r.numDoc() + " " + quien); }
        public List<Aviso> avisosDe(Receptor r) { return List.of(); }
    };
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId).conAgente(new DesignacionDeAgente(true, true)));
        series.crear(new Serie(tenantId, TipoDocumento.RETENCION, "R001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.PERCEPCION, "P001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, new Fakes.Establecimientos(series),
                new Fakes.Bajas(), idempotencias, tope, padron);
    }

    static final Receptor PROVEEDOR = new Receptor("6", "20131312955", "PROVEEDOR DEL NORTE S.A.C.", null);
    static final Receptor CLIENTE = new Receptor("6", "20601234565", "CLIENTE MAYORISTA S.A.C.", null);

    static ComprobanteDeAgente.Documento factura(String numero, String importe) {
        return new ComprobanteDeAgente.Documento("01", numero, LocalDate.of(2026, 9, 1), new BigDecimal(importe), "PEN", 1, LocalDate.of(2026, 9, 13), new BigDecimal(importe), null, null);
    }

    static EmitirAgenteCommand retencion(String serie, boolean enviar) {
        return new EmitirAgenteCommand(serie, null, LocalDate.of(2026, 9, 13), PROVEEDOR,
                new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-123", "1180.00"))), null, enviar, "Pago de septiembre");
    }

    static EmitirAgenteCommand percepcion(String serie, boolean enviar) {
        return new EmitirAgenteCommand(serie, null, LocalDate.of(2026, 9, 13), CLIENTE,
                new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "01", null, List.of(factura("F001-9", "1000.00"))), null, enviar, null);
    }

    @Test void laRetencionSeNumeraEnSuSerieSeValidaConSuXsdYSeEnvia() {
        Comprobante c = service.emitirDeAgente(tenantId, retencion("R001", true));
        assertThat(c.tipo()).isEqualTo(TipoDocumento.RETENCION);
        assertThat(c.numero()).isEqualTo(1L);
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-20-R001-1");
        assertThat(validados).as("el XSD de Retention").containsExactly(TipoDocumento.RETENCION);
        assertThat(storage.datos).containsKey(tenantId + "/2026/09/20100066603-20-R001-1.xml");
        assertThat(gateway.ultimoNombre).as("sendBill, sola, como la factura").isEqualTo("20100066603-20-R001-1");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(c.totales().total()).as("3 % de 1180.00").isEqualByComparingTo("35.40");
        assertThat(c.importeNetoDeAgente()).isEqualByComparingTo("1144.60");
        assertThat(c.observaciones()).isEqualTo("Pago de septiembre");
        assertThat(consultasAlPadron).as("el proveedor contra el padrón, con su regla").containsExactly("2621 20131312955 del proveedor");
    }

    @Test void laPercepcionTambienYConSuReglaDePadron() {
        Comprobante c = service.emitirDeAgente(tenantId, percepcion("P001", false));
        assertThat(c.tipo()).isEqualTo(TipoDocumento.PERCEPCION);
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-40-P001-1");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(gateway.enviados).isZero();
        assertThat(c.totales().total()).isEqualByComparingTo("20.00");
        assertThat(consultasAlPadron).containsExactly("2605 20601234565 del cliente");
    }

    @Test void sinLaDesignacionNoSeEmiteNiSeCalculaYNoSeConsumeNumero() {
        tenants.guardar(Fakes.tenantListo(tenantId).conAgente(new DesignacionDeAgente(false, true)));
        assertThatThrownBy(() -> service.emitirDeAgente(tenantId, retencion("R001", true))).hasMessageStartingWith("2617").extracting("codigo").isEqualTo("AGENTE_NO_DESIGNADO");
        assertThatThrownBy(() -> service.calcularDeAgente(tenantId, retencion("R001", true))).extracting("codigo").isEqualTo("AGENTE_NO_DESIGNADO");
        assertThat(service.emitirDeAgente(tenantId, percepcion("P001", false)).numero()).as("la de percepción sí").isEqualTo(1L);
        tenants.guardar(Fakes.tenantListo(tenantId));
        assertThatThrownBy(() -> service.emitirDeAgente(tenantId, percepcion("P001", false))).hasMessageStartingWith("4285");
        tenants.guardar(Fakes.tenantListo(tenantId).conAgente(new DesignacionDeAgente(true, true)));
        assertThat(service.emitirDeAgente(tenantId, retencion("R001", false)).numero()).as("la retención no gastó número").isEqualTo(1L);
    }

    @Test void loQueElDominioRechazaNoConsumeNumero() {
        EmitirAgenteCommand aSiMismo = new EmitirAgenteCommand("R001", null, LocalDate.of(2026, 9, 13), new Receptor("6", "20100066603", "YO", null),
                new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(factura("F001-1", "100.00"))), null, true, null);
        assertThatThrownBy(() -> service.emitirDeAgente(tenantId, aSiMismo)).isInstanceOf(DomainException.class).hasMessageStartingWith("2620");
        assertThat(service.emitirDeAgente(tenantId, retencion("R001", false)).numero()).isEqualTo(1L);
    }

    @Test void calcularNoNumeraNiGuarda() {
        Comprobante c = service.calcularDeAgente(tenantId, retencion("R001", true));
        assertThat(c.numero()).isNull();
        assertThat(c.totales().total()).isEqualByComparingTo("35.40");
        assertThat(comprobantes.datos).isEmpty();
        assertThat(gateway.enviados).isZero();
    }

    @Test void unReintentoConLaMismaClaveDevuelveLaMismaRetencionYLasClavesNoSeCruzan() {
        Idempotencia clave = new Idempotencia("c1a7e5b0-0000-4000-8000-000000000040", "huella-1");
        var primera = service.emitirDeAgente(tenantId, retencion("R001", true), clave);
        var otra = service.emitirDeAgente(tenantId, retencion("R001", true), clave);
        assertThat(otra.repetida()).isTrue();
        assertThat(otra.comprobante().id()).isEqualTo(primera.comprobante().id());
        assertThat(gateway.enviados).as("no se reenvía").isEqualTo(1);
        assertThatThrownBy(() -> service.emitirDeAgente(tenantId, retencion("R001", true), new Idempotencia(clave.clave(), "otra-huella")))
                .extracting("codigo").isEqualTo("IDEMPOTENCIA_INVALIDA");
        // La misma cadena en una percepción es otra clave: emite la percepción.
        var p = service.emitirDeAgente(tenantId, percepcion("P001", true), clave);
        assertThat(p.repetida()).isFalse();
        assertThat(p.comprobante().tipo()).isEqualTo(TipoDocumento.PERCEPCION);
    }

    /** Se revierte en un resumen de reversiones (RR) con sendSummary; su ticket se consulta con el nombre del resumen, para que el adaptador elija el servicio. */
    @Test void unaRetencionAceptadaSeRevierteEnUnResumenDeReversiones() {
        Comprobante c = service.emitirDeAgente(tenantId, retencion("R001", true));
        Fakes.Bajas bajas = new Fakes.Bajas();
        Fakes.Gateway sunat = new Fakes.Gateway();
        DarDeBajaService baja = new DarDeBajaService(bajas, comprobantes, tenants, storage, ubl, xsd, signer, sunat, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        ComunicacionBaja rr = baja.solicitar(tenantId, c.id(), "Pago anulado por error");
        assertThat(rr.identificador()).isEqualTo("RR-20260913-1");
        assertThat(sunat.ultimoNombre).isEqualTo("20100066603-RR-20260913-1");
        assertThat(sunat.consultadosCon).containsExactly("20100066603-RR-20260913-1#T-1");
        assertThat(rr.estado()).isEqualTo(ComunicacionBaja.EstadoBaja.ACEPTADA);
        assertThat(comprobantes.buscar(tenantId, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }
}
