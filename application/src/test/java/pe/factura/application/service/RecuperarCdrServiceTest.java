package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.application.port.out.SunatConsultaGateway.Consulta;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Recuperación de CDR por getStatusCdr y consulta de validez por billValidService (#36). */
class RecuperarCdrServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Consultas consultas = new Fakes.Consultas();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    RecuperarCdrService service = new RecuperarCdrService(comprobantes, tenants, storage, consultas, cdrs, Fakes.UOW);
    ConsultarValidezService validez = new ConsultarValidezService(tenants, consultas);

    @BeforeEach void setUp() { tenants.guardar(produccion(Fakes.tenantListo(tenantId))); }

    private static Tenant produccion(Tenant t) {
        return new Tenant(t.id(), t.ruc(), t.razonSocial(), Entorno.PRODUCCION, t.sol(), t.certificado(), t.domicilio(), t.cuentaDetracciones(), t.nombreComercial(), t.personalizacionPdf(), t.padronTasaEspecialIgv());
    }

    /** Se envió, SUNAT respondió, pero khipu no llegó a guardar el CDR: quedó ENVIADO. La recuperación lo cierra sin reenviar. */
    @Test void recuperaElCdrDeUnComprobanteEnviadoSinConstancia() {
        Comprobante c = Fakes.facturaFirmada(tenantId, storage);
        c.marcarEnviado();
        comprobantes.guardar(c);
        Comprobante r = service.recuperar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(r.cdrKey()).endsWith("R-" + c.nombreArchivo() + ".zip");
        assertThat(storage.leer(r.cdrKey())).isEqualTo("cdr".getBytes());
        assertThat(consultas.ultimaOperacion).isEqualTo("getStatusCdr");
        assertThat(consultas.ultimosCriterios).containsExactly("20100066603", "01", "F001", 1L);
        assertThatThrownBy(() -> service.recuperar(tenantId, c.id())).extracting("codigo").isEqualTo("CDR_YA_DISPONIBLE");
    }

    @Test void enErrorDeEnvioSinCdrEnSunatNoCambiaNada() {
        Comprobante c = Fakes.facturaFirmada(tenantId, storage);
        c.marcarErrorEnvio("timeout");
        comprobantes.guardar(c);
        consultas.respuesta = new Consulta("0011", "El comprobante de pago electrónico no existe.", null);
        Comprobante r = service.recuperar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.cdrKey()).isNull();
        // Un CDR de rechazo también se aplica.
        cdrs.cdr = new Cdr("2324", "rechazada", List.of());
        consultas.respuesta = new Consulta("0002", "rechazado", "cdr".getBytes());
        assertThat(service.recuperar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void restauraElCdrPerdidoDeUnAceptadoYRechazaSinFirma() {
        Comprobante c = Fakes.facturaFirmada(tenantId, storage);
        c.marcarEnviado(); c.aplicarCdr(new Cdr("0", "ok", List.of()), null);   // aceptado pero sin cdrKey
        comprobantes.guardar(c);
        Comprobante r = service.recuperar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(r.cdrKey()).isNotNull();
        Comprobante sinFirma = Comprobante.factura(tenantId, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(), c.items()).crear(Fakes.CLOCK);
        comprobantes.guardar(sinFirma);
        assertThatThrownBy(() -> service.recuperar(tenantId, sinFirma.id())).extracting("codigo").isEqualTo("SIN_FIRMA");
    }

    @Test void elBarridoSoloConsultaEmpresasEnProduccionYToleraFallos() {
        Comprobante c = Fakes.facturaFirmada(tenantId, storage);
        c.marcarEnviado(); comprobantes.guardar(c);
        UUID beta = UUID.randomUUID();
        tenants.guardar(Fakes.tenantListo(beta));   // BETA: se salta
        Comprobante cb = Fakes.facturaFirmada(beta, storage);
        cb.marcarEnviado(); comprobantes.guardar(cb);
        assertThat(service.recuperarPendientes()).extracting(Comprobante::id).containsExactly(c.id());
        assertThat(consultas.llamadas).isEqualTo(1);
        // SUNAT caído: no explota, lo intenta la siguiente pasada.
        Comprobante d = Fakes.facturaFirmada(tenantId, storage);
        d.marcarEnviado(); comprobantes.guardar(d);
        consultas.falla = new SunatTransientException("0000", "caído");
        assertThat(service.recuperarPendientes()).isEmpty();
        assertThat(comprobantes.buscar(tenantId, d.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }

    @Test void consultaLaValidezDeUnComprobanteDeUnTercero() {
        consultas.respuesta = new Consulta("0003", "El comprobante existe pero está de baja.", null);
        ConsultarValidezUseCase.Validez v = validez.consultar(tenantId, new ConsultarValidezUseCase.Criterios("20100066603", "01", "F001", 12, "6", "20601234565", LocalDate.of(2026, 9, 10), new BigDecimal("118.00")));
        assertThat(v.estado()).isEqualTo("DE_BAJA");
        assertThat(v.codigo()).isEqualTo("0003");
        assertThat(consultas.ultimaOperacion).isEqualTo("validar");
        assertThat(consultas.ultimosCriterios[6]).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(ConsultarValidezService.estado("0001")).isEqualTo("ACEPTADO");
        assertThat(ConsultarValidezService.estado("0011")).isEqualTo("NO_EXISTE");
        assertThat(ConsultarValidezService.estado("0004")).isEqualTo("ERROR_CONSULTA");
        assertThatThrownBy(() -> validez.consultar(tenantId, new ConsultarValidezUseCase.Criterios("123", "01", "F001", 1, null, null, null, null))).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThatThrownBy(() -> validez.consultar(tenantId, new ConsultarValidezUseCase.Criterios("20100066603", "09", "F001", 1, null, null, null, null))).hasMessageContaining("tipo");
    }
}
