package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatCredencialesException;
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
    Fakes.Outbox outbox = new Fakes.Outbox();
    EnviarDocumentoService service;
    Comprobante c;

    @BeforeEach void setUp() {
        tenants.guardar(Fakes.tenantListo(tenantId));
        c = Fakes.facturaFirmada(tenantId, storage);
        comprobantes.guardar(c);
        service = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
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
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
        assertThat(outbox.filas.get(0).accion()).isEqualTo("ENVIAR");
        assertThat(outbox.filas.get(0).agregadoId()).isEqualTo(c.id());
        assertThat(outbox.filas.get(0).tenantId()).isEqualTo(tenantId);
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Backoff.siguiente(1, Fakes.CLOCK.instant()));
    }

    // --- #107: credenciales SOL rechazadas ---------------------------------------------------------------------------------------

    @Test void credencialesRechazadasMarcanLaEmpresaYDicenQueHacer() {
        Fakes.RechazosDeSol rechazos = new Fakes.RechazosDeSol();
        EnviarDocumentoService conRechazos = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK, rechazos);
        gateway.falla = new SunatCredencialesException("0102", "Usuario o contrasena incorrectos");

        Comprobante r = conRechazos.enviar(tenantId, c.id());

        assertThat(r.estado()).as("no es un rechazo del comprobante: queda pendiente de envío").isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.ultimoError()).contains("credenciales SOL").contains("0102").contains("Fiscal & certificado");
        assertThat(rechazos.filas).containsKey(tenantId);
        assertThat(rechazos.filas.get(tenantId).motivo()).isEqualTo("0102 - Usuario o contrasena incorrectos");
        assertThat(outbox.filas).as("sigue en el outbox, que lo retoma al corregir las credenciales").hasSize(1);
    }

    @Test void unaCaidaDeSunatNoMarcaLasCredenciales() {
        Fakes.RechazosDeSol rechazos = new Fakes.RechazosDeSol();
        EnviarDocumentoService conRechazos = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK, rechazos);
        gateway.falla = new SunatTransientException("0109", "El servicio de autenticación no está disponible");

        conRechazos.enviar(tenantId, c.id());

        assertThat(rechazos.filas).isEmpty();
    }

    /** Un envío que sale bien después de corregir las credenciales no deja la marca puesta. */
    @Test void unEnvioAceptadoLevantaLaMarca() {
        Fakes.RechazosDeSol rechazos = new Fakes.RechazosDeSol();
        rechazos.marcar(tenantId, "0102 - Usuario o contrasena incorrectos", Fakes.CLOCK.instant());
        EnviarDocumentoService conRechazos = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK, rechazos);

        conRechazos.enviar(tenantId, c.id());

        assertThat(rechazos.filas).isEmpty();
    }

    @Test void aceptadoNoProgramaOutbox() {
        service.enviar(tenantId, c.id());
        assertThat(outbox.filas).isEmpty();
    }

    @Test void rechazoNoProgramaOutbox() {
        gateway.falla = new SunatRechazoException("2324", "registrado previamente");
        service.enviar(tenantId, c.id());
        assertThat(outbox.filas).isEmpty();
    }

    @Test void segundoFalloTransitorioNoDuplicaLaFilaDelOutbox() {
        gateway.falla = new SunatTransientException("0109", "timeout");
        service.enviar(tenantId, c.id());
        service.enviar(tenantId, c.id());
        assertThat(comprobantes.datos.get(c.id()).intentos()).isEqualTo(2);
        assertThat(outbox.filas).hasSize(1);
    }

    @Test void faultDefinitivoRechaza() {
        gateway.falla = new SunatRechazoException("2324", "registrado previamente");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.cdr().codigo()).isEqualTo("2324");
    }

    /** Un fault 1xxx llega como rechazo (lo clasifica el gateway): un solo intento, sin fila en el outbox. */
    @Test void faultDelContribuyenteEsTerminalYNoReintenta() {
        gateway.falla = new SunatRechazoException("1033", "El comprobante fue registrado previamente con otros datos");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.cdr().codigo()).isEqualTo("1033");
        assertThat(r.intentos()).isZero();
        assertThat(outbox.filas).isEmpty();
        assertThatThrownBy(() -> service.enviar(tenantId, c.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ESTADO_NO_ENVIABLE");
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
        assertThat(outbox.filas).hasSize(1);
    }
}
