package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirLiquidacionCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Liquidación de compra (04, #327): se emite por el mismo camino que la factura —número con la serie L### bloqueada, XML, firma, XSD del SelfBilledInvoice, storage y envío—,
 * con su propio espacio de claves de idempotencia, y lo que el dominio rechaza no consume número.
 */
class EmitirLiquidacionServiceTest {
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
    TipoDocumento[] validado = new TipoDocumento[1];
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) { validado[0] = tipo; }
        public void validarBaja(String xml, ComunicacionBaja baja) {}
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml, "HASH");
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.LIQUIDACION_COMPRA, "L001", 0, true));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, new Fakes.Establecimientos(series),
                new Fakes.Bajas(), idempotencias, tope, Fakes.SIN_COMPRADOR);
    }

    static EmitirLiquidacionCommand liquidacion(String serie, Receptor vendedor, boolean enviar) {
        return new EmitirLiquidacionCommand(serie, null, LocalDate.of(2026, 9, 13), "PEN", null, vendedor,
                new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12", "060101", "02"), null),
                List.of(new Item("CAF-01", "Café pergamino", "KGM", new BigDecimal("10"), new BigDecimal("118"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null)),
                null, null, null, enviar, "Compra en chacra");
    }

    static final Receptor VENDEDOR = new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n");

    @Test void seNumeraEnSuSerieSeValidaConSuXsdYSeEnvia() {
        Comprobante c = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true));
        assertThat(c.tipo()).isEqualTo(TipoDocumento.LIQUIDACION_COMPRA);
        assertThat(c.numero()).isEqualTo(1L);
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-04-L001-1");
        assertThat(validado[0]).as("el XSD del SelfBilledInvoice").isEqualTo(TipoDocumento.LIQUIDACION_COMPRA);
        assertThat(storage.datos).containsKey(tenantId + "/2026/09/20100066603-04-L001-1.xml");
        assertThat(gateway.ultimoNombre).as("sendBill, sola, como la factura").isEqualTo("20100066603-04-L001-1");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(c.totales().total()).isEqualByComparingTo("985.00");
        assertThat(c.observaciones()).isEqualTo("Compra en chacra");
        assertThat(comprobantes.buscar(tenantId, c.id())).isPresent();
    }

    @Test void conEnvioDiferidoQuedaFirmada() {
        Comprobante c = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, false));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(gateway.enviados).isZero();
    }

    @Test void loQueElDominioRechazaNoConsumeNumero() {
        assertThatThrownBy(() -> service.emitirLiquidacionCompra(tenantId, liquidacion("L001", new Receptor("6", "20131312955", "SUNAT", "Av. Lima 1"), true)))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("2800");
        assertThat(service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, false)).numero()).isEqualTo(1L);
    }

    @Test void sinSerieConfiguradaNiCupoNoSeEmite() {
        assertThatThrownBy(() -> service.emitirLiquidacionCompra(tenantId, liquidacion("L002", VENDEDOR, true))).extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
        tope.lleno = true;
        assertThatThrownBy(() -> service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true))).extracting("codigo").isEqualTo("LIMITE_PLAN");
        assertThat(comprobantes.datos).isEmpty();
    }

    /** La tasa reducida del padrón es de las ventas de restaurantes y hoteles de la empresa; en la liquidación vende la persona sin RUC: 18 % (4290). */
    @Test void elPadronDeTasaEspecialDeLaEmpresaNoCambiaElIgvDeSuCompra() {
        tenants.guardar(Fakes.tenantListo(tenantId).conDatosFiscales(Domicilio.de("150101", "Av. Lima 1"), null, null, true));
        Comprobante c = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, false));
        assertThat(c.tasaIgv()).isEqualByComparingTo("18.00");
        assertThat(c.totales().igv()).isEqualByComparingTo("180.00");
    }

    @Test void calcularNoNumeraNiGuarda() {
        Comprobante c = service.calcularLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true));
        assertThat(c.numero()).isNull();
        assertThat(c.retencionRenta()).isEqualByComparingTo("15.00");
        assertThat(comprobantes.datos).isEmpty();
        assertThat(gateway.enviados).isZero();
        assertThat(service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, false)).numero()).as("calcular no gastó el número").isEqualTo(1L);
    }

    @Test void unReintentoConLaMismaClaveDevuelveLaMismaLiquidacion() {
        Idempotencia clave = new Idempotencia("c1a7e5b0-0000-4000-8000-000000000327", "huella-1");
        var primera = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true), clave);
        var otra = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true), clave);
        assertThat(primera.repetida()).isFalse();
        assertThat(otra.repetida()).isTrue();
        assertThat(otra.comprobante().id()).isEqualTo(primera.comprobante().id());
        assertThat(gateway.enviados).as("no se reenvía").isEqualTo(1);
        assertThatThrownBy(() -> service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true), new Idempotencia(clave.clave(), "otra-huella")))
                .extracting("codigo").isEqualTo("IDEMPOTENCIA_INVALIDA");
    }

    /** Las claves de la liquidación son un espacio aparte: la misma cadena en una factura emite la factura. */
    @Test void laClaveDeUnaLiquidacionNoEsLaDeUnaFactura() {
        Idempotencia clave = new Idempotencia("c1a7e5b0-0000-4000-8000-000000000328", "huella-1");
        service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true), clave);
        EmitirFacturaCommand factura = new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true);
        var f = service.emitirFactura(tenantId, factura, clave);
        assertThat(f.repetida()).isFalse();
        assertThat(f.comprobante().tipo()).isEqualTo(TipoDocumento.FACTURA);
    }

    /** No hay anulación por comunicación de baja (2308): se anula con un resumen de reversiones que khipu todavía no emite. */
    @Test void unaLiquidacionAceptadaNoSeDaDeBaja() {
        Comprobante c = service.emitirLiquidacionCompra(tenantId, liquidacion("L001", VENDEDOR, true));
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "Error en el pedido", Fakes.CLOCK)).hasMessageStartingWith("2308").extracting("codigo").isEqualTo("BAJA_INVALIDA");
    }
}
