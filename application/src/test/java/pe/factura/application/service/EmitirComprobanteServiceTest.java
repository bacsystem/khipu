package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmitirComprobanteServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Series series = new Fakes.Series();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    UblGenerator ubl = (c, t) -> "<Invoice>" + c.nombreArchivo() + "</Invoice>";
    String[] recibido = new String[1];
    XsdValidator xsd = (xml, tipo) -> recibido[0] = xml;
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("<Invoice>", "<Invoice><ds:Signature/>"), "HASH" + xml.length());
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK);
    }

    private EmitirFacturaCommand cmd(Long correlativo, boolean enviar) {
        return new EmitirFacturaCommand("F001", correlativo, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, enviar);
    }

    @Test void asignaNumeroFirmaGuardaYEnvia() {
        Comprobante c = service.emitirFactura(tenantId, cmd(null, true));
        assertThat(c.numero()).isEqualTo(1L);
        assertThat(c.hash()).startsWith("HASH");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(c.xmlKey()).isEqualTo(tenantId + "/2026/09/20100066603-01-F001-1.xml");
        assertThat(new String(storage.leer(c.xmlKey()))).contains("<ds:Signature/>");
        assertThat(comprobantes.datos).containsKey(c.id());
        assertThat(outbox.filas).isEmpty();
        // El validador XSD debe recibir el XML ya firmado, no el XML sin firmar generado por ubl.generar(...).
        assertThat(recibido[0]).contains("<ds:Signature/>");
    }

    @Test void numeracionCorrelativa() {
        service.emitirFactura(tenantId, cmd(null, false));
        Comprobante segundo = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(segundo.numero()).isEqualTo(2L);
        assertThat(segundo.estado()).isEqualTo(EstadoDocumento.FIRMADO);
    }

    @Test void correlativoExplicitoSeRespetaYNoSeDuplica() {
        Comprobante c = service.emitirFactura(tenantId, cmd(50L, false));
        assertThat(c.numero()).isEqualTo(50L);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(50L, false)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DUPLICADO");
    }

    @Test void correlativoExplicitoAvanzaLaSerie() {
        service.emitirFactura(tenantId, cmd(50L, false));
        assertThat(service.emitirFactura(tenantId, cmd(null, false)).numero()).isEqualTo(51L);
        // Un correlativo explícito menor al último no retrocede la serie
        service.emitirFactura(tenantId, cmd(10L, false));
        assertThat(service.emitirFactura(tenantId, cmd(null, false)).numero()).isEqualTo(52L);
    }

    @Test void correlativoExplicitoEnSerieNoConfiguradaFalla() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F999", 7L, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
        assertThat(comprobantes.datos).isEmpty();
    }

    @Test void sinCredencialesSolYEnvioAutomaticoFallaAntesDeConsumirNumero() {
        Tenant sinSol = new Tenant(tenantId, "20100066603", "EMPRESA SAC", pe.factura.domain.tenant.Entorno.BETA, null,
                new pe.factura.domain.tenant.CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)));
        tenants.guardar(sinSol);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(null, true)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CREDENCIALES_SOL_NO_CARGADAS");
        assertThat(comprobantes.datos).isEmpty();
        assertThat(storage.datos).isEmpty();
        assertThat(outbox.filas).isEmpty();
        // La serie no consumió ningún número
        assertThat(service.emitirFactura(tenantId, cmd(null, false)).numero()).isEqualTo(1L);
    }

    @Test void sinCredencialesSolYSinEnvioAutomaticoQuedaFirmado() {
        Tenant sinSol = new Tenant(tenantId, "20100066603", "EMPRESA SAC", pe.factura.domain.tenant.Entorno.BETA, null,
                new pe.factura.domain.tenant.CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)));
        tenants.guardar(sinSol);
        Comprobante c = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(comprobantes.datos).containsKey(c.id());
    }

    @Test void sinEnvioAutomaticoQuedaFirmado() {
        Comprobante c = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(gateway.ultimoNombre).isNull();
    }

    @Test void falloTransitorioProgramaOutbox() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        Comprobante c = service.emitirFactura(tenantId, cmd(null, true));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
        assertThat(outbox.filas.get(0).accion()).isEqualTo("ENVIAR");
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Backoff.siguiente(1, Fakes.CLOCK.instant()));
    }

    @Test void xsdInvalidoNoConsumeNumeroNiGuarda() {
        XsdValidator malo = (xml, tipo) -> { throw new DomainException("XSD_INVALIDO", "línea 3"); };
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        EmitirComprobanteService s = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, malo, signer, enviar, Fakes.UOW, Fakes.CLOCK);
        assertThatThrownBy(() -> s.emitirFactura(tenantId, cmd(null, true))).extracting("codigo").isEqualTo("XSD_INVALIDO");
        assertThat(comprobantes.datos).isEmpty();
    }

    @Test void tenantSinCertificadoFalla() {
        Tenant sinCert = new Tenant(tenantId, "20100066603", "EMPRESA SAC", pe.factura.domain.tenant.Entorno.BETA, null, null);
        tenants.guardar(sinCert);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(null, false))).extracting("codigo").isEqualTo("CERTIFICADO_NO_CARGADO");
    }

    @Test void serieNoConfiguradaFalla() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F999", null, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }

    @Test void detraccionSinCuentaUsaLaDeLaEmpresa() {
        Detraccion sinCuenta = new Detraccion("022", new BigDecimal("12"), new BigDecimal("14.00"), null, null);
        EmitirFacturaCommand cmd = new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), "PEN", "1001",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), sinCuenta, null, null, List.of(), null, false);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd)).isInstanceOf(DomainException.class).hasMessageContaining("3034");

        tenants.guardar(Fakes.tenantListo(tenantId).conDatosFiscales(null, "00-000-987654"));
        assertThat(service.emitirFactura(tenantId, cmd).detraccion().cuentaBancoNacion()).isEqualTo("00-000-987654");
    }

    private EmitirFacturaCommand conAnticipo(Anticipo a) {
        return new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Obra completa", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(a), null, false);
    }

    @Test void anticipoDescuentaUnaFacturaAceptadaDeLaMismaEmpresa() {
        Comprobante anticipo = service.emitirFactura(tenantId, cmd(null, true));   // F001-1 por 118.00, ACEPTADO por el gateway fake
        assertThat(anticipo.estado()).isEqualTo(EstadoDocumento.ACEPTADO);

        Comprobante fin = service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", 1, new BigDecimal("100.00"), null, null)));
        assertThat(fin.totales().totalAnticipos()).isEqualByComparingTo("118.00");
        assertThat(fin.totales().total()).isEqualByComparingTo("1062.00");
    }

    @Test void unAnticipoNoSeRegularizaDosVeces() {
        Comprobante anticipo = service.emitirFactura(tenantId, cmd(null, true));   // F001-1: 100.00 gravado, ACEPTADO
        service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", anticipo.numero(), new BigDecimal("60.00"), null, null)));
        assertThatThrownBy(() -> service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", anticipo.numero(), new BigDecimal("40.01"), null, null))))
                .isInstanceOf(DomainException.class).hasMessageContaining("ya se regularizaron 60.00");
        // El resto (40.00) sí puede regularizarse en otra factura final
        assertThat(service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", anticipo.numero(), new BigDecimal("40.00"), null, null))).totales().totalAnticipos())
                .isEqualByComparingTo("47.20");
    }

    @Test void anticipoRechazaFacturaInexistenteNoAceptadaOAjena() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", 99, new BigDecimal("100.00"), null, null))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3218").hasMessageContaining("no existe");

        // El UnitOfWork de prueba no revierte el contador de la serie, así que se usa el número que devuelve cada emisión.
        Comprobante firmado = service.emitirFactura(tenantId, cmd(null, false));   // queda FIRMADO
        assertThatThrownBy(() -> service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", firmado.numero(), new BigDecimal("100.00"), null, null))))
                .hasMessageContaining("3218").hasMessageContaining("FIRMADO");

        Comprobante aceptado = service.emitirFactura(tenantId, cmd(null, true));   // ACEPTADO por 100.00 gravado
        assertThatThrownBy(() -> service.emitirFactura(tenantId, conAnticipo(new Anticipo("F001", aceptado.numero(), new BigDecimal("100.01"), null, null))))
                .hasMessageContaining("supera el valor de venta gravado de esa factura");
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), "USD", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", aceptado.numero(), new BigDecimal("50.00"), null, null)), null, false)))
                .hasMessageContaining("2071");
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20609999999", "OTRO SAC", null),
                List.of(new Item("P1", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", aceptado.numero(), new BigDecimal("50.00"), null, null)), null, false)))
                .hasMessageContaining("otro cliente");
    }
}
