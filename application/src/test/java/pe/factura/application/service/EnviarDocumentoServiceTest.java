package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.application.port.out.RechazoDeSolRepository;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    /** Un fault del contribuyente (1xxx-3xxx) es definitivo: RECHAZADO sin reintento. El 1033 «registrado previamente» ya no está aquí: es la señal de que SUNAT lo tiene (LOG-01, más abajo). */
    @Test void faultDelContribuyenteEsTerminalYNoReintenta() {
        gateway.falla = new SunatRechazoException("2324", "El archivo XML contiene errores");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.RECHAZADO);
        assertThat(r.cdr().codigo()).isEqualTo("2324");
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

    /**
     * LOG-02: SUNAT ya aceptó (punto de no retorno) y al guardar el CDR el storage lanza algo que no es IllegalStateException (una caída de red del SDK de S3, por
     * ejemplo). Sin un catch final la excepción salía antes de la transacción de abajo: 500 al cliente, documento FIRMADO en la base sin fila de outbox, nadie lo
     * reintenta y el plazo lo cierra FUERA_DE_PLAZO estando aceptado. Debe quedar ERROR_ENVIO con su reintento programado, como cualquier otra falla de infraestructura.
     */
    @Test void unaExcepcionInesperadaAlGuardarElCdrDejaErrorEnvioConReintento() {
        Fakes.Storage storageQueCae = new Fakes.Storage() {
            @Override public void guardar(String k, byte[] contenido) {
                if (k.contains("/R-")) throw new RuntimeException("Unable to execute HTTP request: Read timed out");
                super.guardar(k, contenido);
            }
        };
        storageQueCae.datos.putAll(storage.datos);
        EnviarDocumentoService conStorageQueCae = new EnviarDocumentoService(comprobantes, tenants, storageQueCae, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);

        Comprobante r = conStorageQueCae.enviar(tenantId, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.ultimoError()).startsWith("INFRA - RuntimeException: Unable to execute HTTP request");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
    }

    /**
     * LOG-01: mientras SUNAT no contestó al primer envío, un segundo del mismo comprobante (el outbox, otro POST, el reintento del administrador) no llama a SUNAT:
     * recibe ENVIO_EN_CURSO. Antes los dos mandaban el mismo número; el segundo recibía 1033 y dejaba RECHAZADO el ACEPTADO del primero.
     */
    @Test void unSegundoEnvioMientrasSunatNoContestaNoLlamaASunat() {
        AtomicReference<Throwable> segundo = new AtomicReference<>();
        gateway.alEnviar = () -> segundo.set(catchThrowable(() -> service.enviar(tenantId, c.id())));

        Comprobante r = service.enviar(tenantId, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(gateway.enviados).as("una sola llamada a SUNAT").isEqualTo(1);
        assertThat(segundo.get()).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ENVIO_EN_CURSO");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(comprobantes.reservas).as("el guardado libera la reserva").isEmpty();
    }

    /** LOG-01: una reserva huérfana (el proceso murió entre el sendBill y el guardado) no bloquea el comprobante para siempre: pasado ENVIO_SIN_RESPUESTA otro envío la retoma. */
    @Test void unaReservaRecienteBloqueaYUnaCaducadaSeRetoma() {
        Comprobante reciente = Fakes.facturaFirmada(tenantId, storage, 2);
        comprobantes.guardar(reciente);
        comprobantes.reservas.put(reciente.id(), Fakes.CLOCK.instant().minusSeconds(30));
        assertThatThrownBy(() -> service.enviar(tenantId, reciente.id())).extracting("codigo").isEqualTo("ENVIO_EN_CURSO");
        assertThat(gateway.enviados).isZero();

        comprobantes.reservas.put(c.id(), Fakes.CLOCK.instant().minus(EnviarDocumentoService.ENVIO_SIN_RESPUESTA).minusSeconds(1));
        assertThat(service.enviar(tenantId, c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(gateway.enviados).isEqualTo(1);
    }

    /**
     * LOG-01: 1033 «registrado previamente» no es un rechazo: un envío anterior llegó aunque khipu no recibió la respuesta. En producción, con una factura, se pide la
     * constancia por getStatusCdr y el comprobante queda como SUNAT lo tiene (ACEPTADO), sin fila de outbox ni reserva.
     */
    @Test void unFaultYaRegistradoSeResuelveConGetStatusCdrEnVezDeRechazar() {
        Tenant t = Fakes.tenantListo(tenantId);
        tenants.guardar(new Tenant(t.id(), t.ruc(), t.razonSocial(), Entorno.PRODUCCION, t.sol(), t.certificado(), t.domicilio(), t.cuentaDetracciones(), t.nombreComercial(), t.personalizacionPdf(), t.padronTasaEspecialIgv()));
        Fakes.Consultas consultas = new Fakes.Consultas();
        RecuperarCdrService recuperador = new RecuperarCdrService(comprobantes, tenants, storage, consultas, cdrs, Fakes.UOW);
        EnviarDocumentoService conRecuperador = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK, RechazoDeSolRepository.NINGUNO, null, recuperador);
        gateway.falla = new SunatRechazoException("1033", "El comprobante fue registrado previamente con otros datos");

        Comprobante r = conRecuperador.enviar(tenantId, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(r.cdrKey()).endsWith("R-" + c.nombreArchivo() + ".zip");
        assertThat(consultas.ultimaOperacion).isEqualTo("getStatusCdr");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(outbox.filas).isEmpty();
        assertThat(comprobantes.reservas).isEmpty();
    }

    /** LOG-01: sin forma de preguntar (e-beta, o un servicio sin recuperador) el 1033 deja ERROR_ENVIO con el motivo y su reintento: nunca RECHAZADO sobre lo que SUNAT ya tiene. */
    @Test void unFaultYaRegistradoSinConsultaQuedaEnErrorDeEnvioYNoRechaza() {
        gateway.falla = new SunatRechazoException("1033", "El comprobante fue registrado previamente con otros datos");
        Comprobante r = service.enviar(tenantId, c.id());
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.ultimoError()).startsWith("1033 - ").contains("SUNAT ya tiene registrado").contains("getStatusCdr");
        assertThat(outbox.filas).hasSize(1);

        Comprobante b = Fakes.boletaFirmada(tenantId, storage, 1);
        comprobantes.guardar(b);
        Comprobante rb = service.enviar(tenantId, b.id());
        assertThat(rb.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(rb.ultimoError()).contains("SOL");
    }

    /** LOG-02: un CDR que llega sin ResponseCode reventaba en el dominio (CDR_INVALIDO) después de marcarEnviado, con el mismo resultado: FIRMADO sin reintento. */
    @Test void unCdrSinCodigoDejaErrorEnvioConReintento() {
        cdrs.cdr = new Cdr("", "", List.of());

        Comprobante r = service.enviar(tenantId, c.id());

        assertThat(r.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(r.ultimoError()).startsWith("INFRA - DomainException");
        assertThat(comprobantes.datos.get(c.id()).estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(outbox.filas).hasSize(1);
    }
}
