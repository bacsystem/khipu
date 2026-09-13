package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EnviarDocumentoServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    EnviarDocumentoService service;
    Comprobante c;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        c = Fakes.facturaFirmada(tenantId, storage);
        comprobantes.guardar(c);
        service = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, Fakes.UOW);
    }

    @Test void aceptadoGuardaCdrYEstado() {
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-01-F001-1");
        assertThat(storage.datos).containsKey("k/R-20100066603-01-F001-1.zip");
        assertThat(r.cdrKey()).isEqualTo("k/R-20100066603-01-F001-1.zip");
    }

    @Test void cdrConObservaciones() {
        cdrs.cdr = new Cdr("0", "ok", List.of("4252 - obs"));
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
    }

    @Test void cdrRechazo() {
        cdrs.cdr = new Cdr("2324", "registrado previamente", List.of());
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test void faultTransitorioDejaErrorEnvio() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.ultimoError()).contains("0109");
    }

    @Test void faultDefinitivoRechaza() {
        gateway.falla = new SunatRechazoException("2324", "registrado previamente");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.cdr().codigo()).isEqualTo("2324");
    }

    @Test void reintentoDesdeErrorEnvioFunciona() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        service.enviar(tenantId, c.id());
        gateway.falla = null;
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void estadoNoEnviableLanza() {
        service.enviar(tenantId, c.id());
        assertThatThrownBy(() -> service.enviar(tenantId, c.id()))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ESTADO_NO_ENVIABLE");
    }

    @Test void otroTenantNoVeElDocumento() {
        assertThatThrownBy(() -> service.enviar(UUID.randomUUID(), c.id()))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void falloDeStorageDejaErrorEnvio() {
        storage.datos.remove(c.xmlKey());
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.ultimoError()).startsWith("INFRA");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
    }
}
