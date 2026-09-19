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
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    Tenant[] emisor = new Tenant[1];
    UblGenerator ubl = new UblGenerator() {
        public String generar(Comprobante c, pe.factura.domain.tenant.Tenant t) { emisor[0] = t; return "<Invoice>" + c.nombreArchivo() + "</Invoice>"; }
        public String generarBaja(pe.factura.domain.documento.ComunicacionBaja b, pe.factura.domain.tenant.Tenant t) { return ""; }
    };
    String[] recibido = new String[1];
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) { recibido[0] = xml; }
        public void validarBaja(String xml) { recibido[0] = xml; }
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("<Invoice>", "<Invoice><ds:Signature/>"), "HASH" + xml.length());
    EmitirComprobanteService service;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F001", 0, true));
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        service = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos);
    }

    private EmitirFacturaCommand cmd(Long correlativo, boolean enviar) {
        return new EmitirFacturaCommand("F001", correlativo, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, enviar);
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
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F999", 7L, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false)))
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
        XsdValidator malo = new XsdValidator() {
            public void validar(String xml, TipoDocumento tipo) { throw new DomainException("XSD_INVALIDO", "línea 3"); }
            public void validarBaja(String xml) { throw new DomainException("XSD_INVALIDO", "línea 3"); }
        };
        EnviarDocumentoService enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        EmitirComprobanteService s = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, malo, signer, enviar, Fakes.UOW, Fakes.CLOCK, establecimientos);
        assertThatThrownBy(() -> s.emitirFactura(tenantId, cmd(null, true))).extracting("codigo").isEqualTo("XSD_INVALIDO");
        assertThat(comprobantes.datos).isEmpty();
    }

    @Test void tenantSinCertificadoFalla() {
        Tenant sinCert = new Tenant(tenantId, "20100066603", "EMPRESA SAC", pe.factura.domain.tenant.Entorno.BETA, null, null);
        tenants.guardar(sinCert);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd(null, false))).extracting("codigo").isEqualTo("CERTIFICADO_NO_CARGADO");
    }

    @Test void serieNoConfiguradaFalla() {
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F999", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false)))
                .extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }

    @Test void detraccionSinCuentaUsaLaDeLaEmpresa() {
        Detraccion sinCuenta = new Detraccion("022", new BigDecimal("12"), new BigDecimal("14.00"), null, null);
        EmitirFacturaCommand cmd = new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "1001",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), sinCuenta, null, null, List.of(), null, null, false);
        assertThatThrownBy(() -> service.emitirFactura(tenantId, cmd)).isInstanceOf(DomainException.class).hasMessageContaining("3034");

        tenants.guardar(Fakes.tenantListo(tenantId).conDatosFiscales(null, "00-000-987654"));
        assertThat(service.emitirFactura(tenantId, cmd).detraccion().cuentaBancoNacion()).isEqualTo("00-000-987654");
    }

    private EmitirFacturaCommand conAnticipo(Anticipo a) {
        return new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Obra completa", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(a), null, null, false);
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
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "USD", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", aceptado.numero(), new BigDecimal("50.00"), null, null)), null, null, false)))
                .hasMessageContaining("2071");
        assertThatThrownBy(() -> service.emitirFactura(tenantId, new EmitirFacturaCommand("F001", null, LocalDate.of(2026, 9, 13), null, "PEN", "0101",
                new Receptor("6", "20609999999", "OTRO SAC", null),
                List.of(new Item("P1", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(new Anticipo("F001", aceptado.numero(), new BigDecimal("50.00"), null, null)), null, null, false)))
                .hasMessageContaining("otro cliente");
    }

    /** La serie de un anexo emite con el domicilio del anexo (RegistrationAddress del XML); la del 0000, con el fiscal (#80). */
    @Test void laSerieDeUnEstablecimientoAnexoEmiteConSuDomicilio() {
        pe.factura.domain.tenant.Domicilio fiscal = pe.factura.domain.tenant.Domicilio.de("150101", "Av. Lima 123");
        pe.factura.domain.tenant.Domicilio tienda = pe.factura.domain.tenant.Domicilio.de("150122", "Av. Larco 345");
        tenants.guardar(Fakes.tenantListo(tenantId).conDatosFiscales(fiscal, null, null));
        establecimientos.guardar(new pe.factura.domain.tenant.Establecimiento(tenantId, "0002", "Tienda", tienda, true));
        series.crear(new Serie(tenantId, TipoDocumento.FACTURA, "F002", 0, true, "0002"));
        service.emitirFactura(tenantId, comando("F002"));
        assertThat(emisor[0].domicilio().codigoEstablecimiento()).isEqualTo("0002");
        assertThat(emisor[0].domicilio().direccion()).isEqualTo("Av. Larco 345");
        assertThat(emisor[0].ruc()).isEqualTo("20100066603");

        service.emitirFactura(tenantId, comando("F001"));
        assertThat(emisor[0].domicilio().codigoEstablecimiento()).isEqualTo("0000");
        assertThat(emisor[0].domicilio().direccion()).isEqualTo("Av. Lima 123");

        // Anexo dado de baja: se rechaza y no consume número.
        establecimientos.guardar(new pe.factura.domain.tenant.Establecimiento(tenantId, "0002", "Tienda", tienda, false));
        assertThatThrownBy(() -> service.emitirFactura(tenantId, comando("F002"))).extracting("codigo").isEqualTo("ESTABLECIMIENTO_INVALIDO");
        assertThat(comprobantes.listar(tenantId, null, 1, 10)).hasSize(2);
    }

    private EmitirFacturaCommand comando(String serie) {
        return new EmitirFacturaCommand(serie, null, LocalDate.of(2026, 9, 13), null, "PEN", "0101", new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, true);
    }

    /** Emitida sin enviar el día 13 y enviada el 17: el plazo (13 + 3 = 16) venció; se cierra sin llamar a SUNAT (#37). */
    @Test void enviarFueraDePlazoCierraElComprobanteSinLlamarASunat() {
        Comprobante c = service.emitirFactura(tenantId, cmd(null, false));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(c.fechaLimiteEnvio()).isEqualTo(LocalDate.of(2026, 9, 16));
        java.time.Clock dia17 = java.time.Clock.fixed(java.time.Instant.parse("2026-09-17T15:00:00Z"), java.time.ZoneId.of("America/Lima"));
        EnviarDocumentoService tarde = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, dia17);
        assertThatThrownBy(() -> tarde.enviar(tenantId, c.id())).extracting("codigo").isEqualTo("FUERA_DE_PLAZO");
        Comprobante cerrado = comprobantes.buscar(tenantId, c.id()).orElseThrow();
        assertThat(cerrado.estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
        assertThat(cerrado.ultimoError()).contains("2108").contains("2026-09-16");
        assertThat(gateway.ultimoNombre).isNull();
        // Terminal: un nuevo intento de envío ya no llega ni a la comprobación del plazo.
        assertThatThrownBy(() -> tarde.enviar(tenantId, c.id())).extracting("codigo").isEqualTo("ESTADO_NO_ENVIABLE");
    }

    /** El barrido marca lo que nadie intentó enviar; lo que sigue dentro del plazo no se toca. */
    @Test void elBarridoMarcaLosVencidosYRespetaLosVigentes() {
        Comprobante vieja = service.emitirFactura(tenantId, cmd(null, false));   // 13/09, vence 16/09
        java.time.Clock dia17 = java.time.Clock.fixed(java.time.Instant.parse("2026-09-17T15:00:00Z"), java.time.ZoneId.of("America/Lima"));
        gateway.falla = new SunatTransientException("0000", "caído");
        Comprobante enError = service.emitirFactura(tenantId, cmd(null, true));   // ERROR_ENVIO del 13/09, también vence
        gateway.falla = null;
        ControlarPlazoEnvioService barrido = new ControlarPlazoEnvioService(comprobantes, Fakes.UOW, dia17);
        assertThat(barrido.marcarVencidos()).extracting(Comprobante::id).containsExactlyInAnyOrder(vieja.id(), enError.id());
        assertThat(comprobantes.buscar(tenantId, vieja.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
        assertThat(comprobantes.buscar(tenantId, enError.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
        // Segunda pasada: nada nuevo. Y el día 16 (último día válido) no marca nada.
        assertThat(barrido.marcarVencidos()).isEmpty();
        Comprobante otra = service.emitirFactura(tenantId, cmd(null, false));
        java.time.Clock dia16 = java.time.Clock.fixed(java.time.Instant.parse("2026-09-16T15:00:00Z"), java.time.ZoneId.of("America/Lima"));
        assertThat(new ControlarPlazoEnvioService(comprobantes, Fakes.UOW, dia16).marcarVencidos()).isEmpty();
        assertThat(comprobantes.buscar(tenantId, otra.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.FIRMADO);
    }
}
