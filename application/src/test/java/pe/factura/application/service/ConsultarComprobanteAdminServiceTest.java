package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.BajaDeFicha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.Ficha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.RespuestaSunat;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La ficha de un comprobante en el backoffice (#251): se encuentra sin saber de qué empresa es, y dice qué le pasó sin abrir sus archivos. */
class ConsultarComprobanteAdminServiceTest {
    UUID empresaId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Bajas bajas = new Fakes.Bajas();
    ColaDeErroresRepository cola = new ColaDeErroresRepository() {
        public List<Fila> listar(Filtro f, int p, int pp) { throw new AssertionError("la ficha no lista"); }
        public long contar(Filtro f) { throw new AssertionError("la ficha no cuenta"); }
        public Optional<Ubicacion> ubicar(UUID id) { return Optional.ofNullable(comprobantes.datos.get(id)).map(c -> new Ubicacion(c.tenantId(), c.nombreArchivo())); }
    };
    ConsultarComprobanteAdminService service = new ConsultarComprobanteAdminService(cola, comprobantes, tenants, storage, bajas);

    {
        tenants.guardar(Fakes.tenantListo(empresaId));
        tenants.asignarCuenta(empresaId, cuentaId);
    }

    @Test void unComprobanteEnErrorDiceSuEmpresaSusIntentosYSuUltimoError() {
        Comprobante c = Fakes.facturaFirmada(empresaId, storage);
        c.marcarErrorEnvio("0109 - El sistema no puede responder");
        comprobantes.guardar(c);

        Ficha f = service.ficha(c.id());

        assertThat(f.id()).isEqualTo(c.id());
        assertThat(f.empresaId()).isEqualTo(empresaId);
        assertThat(f.ruc()).isEqualTo("20100066603");
        assertThat(f.cuentaId()).isEqualTo(cuentaId);
        assertThat(f.nombreArchivo()).isEqualTo("20100066603-01-F001-1");
        assertThat(f.tipo()).isEqualTo("01");
        assertThat(f.serie()).isEqualTo("F001");
        assertThat(f.numero()).isEqualTo(1L);
        assertThat(f.fechaEmision()).isEqualTo(c.fechaEmision());
        assertThat(f.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(f.intentos()).isEqualTo(1);
        assertThat(f.ultimoError()).isEqualTo("0109 - El sistema no puede responder");
        assertThat(f.cdr()).isNull();
        assertThat(f.tieneXml()).isTrue();
        assertThat(f.tieneCdr()).isFalse();
    }

    @Test void unComprobanteAceptadoTraeLaRespuestaDeSunatYSuCdrGuardado() {
        Comprobante c = Fakes.facturaFirmada(empresaId, storage);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "La Factura numero F001-1, ha sido aceptada", List.of()), "k/R-20100066603-01-F001-1.zip");
        storage.guardar("k/R-20100066603-01-F001-1.zip", new byte[]{1});
        comprobantes.guardar(c);

        Ficha f = service.ficha(c.id());

        assertThat(f.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(f.cdr()).isEqualTo(new RespuestaSunat("0", "La Factura numero F001-1, ha sido aceptada"));
        assertThat(f.tieneCdr()).isTrue();
        assertThat(f.ultimoError()).isNull();
    }

    /**
     * 273-H1: «guardado» es que el objeto está en el almacenamiento, no que la base tenga su clave. Es justo el caso de la verificación de integridad
     * (XML_FALTANTE, CDR_FALTANTE): desde ese fallo se llega a la ficha, y no puede decir «Guardado».
     */
    @Test void unArchivoConClavePeroSinObjetoNoEstaGuardado() {
        Comprobante c = Fakes.facturaFirmada(empresaId, storage);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/R-que-se-perdio.zip");
        comprobantes.guardar(c);
        storage.borrar(c.xmlKey());

        Ficha f = service.ficha(c.id());

        assertThat(f.tieneXml()).as("la clave está, el objeto no").isFalse();
        assertThat(f.tieneCdr()).isFalse();
    }

    @Test void unaEmpresaDeIntegracionNoTieneCuenta() {
        UUID integracion = UUID.randomUUID();
        tenants.guardar(Fakes.tenantListo(integracion));
        Comprobante c = Fakes.facturaFirmada(integracion, storage);
        comprobantes.guardar(c);

        assertThat(service.ficha(c.id()).cuentaId()).isNull();
    }

    /**
     * #362 H9: la ficha del backoffice dice la baja o el resumen diario (RC) del comprobante: cuál es, en qué estado está, su ticket y su motivo. En una boleta el RC no
     * lleva el motivo, pero khipu lo guarda y soporte lo necesita para entender la anulación.
     */
    @Test void unaBoletaConSuResumenDiarioMuestraSuIdentificadorEstadoTicketMotivoYFecha() {
        Comprobante boleta = Fakes.boletaFirmada(empresaId, storage, 21);
        boleta.marcarEnviado();
        boleta.aplicarCdr(new Cdr("0", "La Boleta numero B001-21, ha sido aceptada", List.of()), "k/R-boleta.zip");
        comprobantes.guardar(boleta);
        ComunicacionBaja rc = ComunicacionBaja.crear(boleta, 3, "Se cobró dos veces", Fakes.CLOCK);
        rc.marcarEnviada("1789768174685");
        bajas.guardar(rc);

        List<BajaDeFicha> lista = service.ficha(boleta.id()).bajas();

        assertThat(lista).hasSize(1);
        BajaDeFicha b = lista.get(0);
        assertThat(b.id()).isEqualTo(rc.id());
        assertThat(b.identificador()).isEqualTo("RC-20260913-3");
        assertThat(b.condicion()).isEqualTo("BAJA");
        assertThat(b.estado()).isEqualTo("ENVIADA");
        assertThat(b.ticket()).isEqualTo("1789768174685");
        assertThat(b.motivo()).isEqualTo("Se cobró dos veces");
        assertThat(b.fechaGeneracion()).isEqualTo(rc.fechaGeneracion());
        assertThat(b.fechaReferencia()).isEqualTo(boleta.fechaEmision());
        assertThat(b.fueraDePlazo()).isFalse();
        assertThat(b.cdr()).isNull();
        assertThat(b.intentos()).isZero();
        assertThat(b.ultimoError()).isNull();
    }

    @Test void unaBajaAceptadaTraeElCdrDeSunat() {
        Comprobante f = Fakes.facturaFirmada(empresaId, storage);
        f.marcarEnviado();
        f.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/R-f.zip");
        comprobantes.guardar(f);
        ComunicacionBaja ra = ComunicacionBaja.crear(f, 1, "Error en el RUC", Fakes.CLOCK);
        ra.marcarEnviada("T-1");
        ra.aplicarCdr(new Cdr("0", "La Comunicacion de baja RA-20260913-1, ha sido aceptada", List.of()), "k/R-RA.zip");
        bajas.guardar(ra);

        BajaDeFicha b = service.ficha(f.id()).bajas().get(0);

        assertThat(b.identificador()).isEqualTo("RA-20260913-1");
        assertThat(b.estado()).isEqualTo("ACEPTADA");
        assertThat(b.cdr()).isEqualTo(new RespuestaSunat("0", "La Comunicacion de baja RA-20260913-1, ha sido aceptada"));
    }

    @Test void unComprobanteSinBajasTraeLaListaVaciaYLasBajasDeOtroNoSeMezclan() {
        Comprobante a = Fakes.facturaFirmada(empresaId, storage, 1);
        a.marcarEnviado();
        a.aplicarCdr(new Cdr("0", "ok", List.of()), "k/R-a.zip");
        Comprobante otro = Fakes.facturaFirmada(empresaId, storage, 2);
        otro.marcarEnviado();
        otro.aplicarCdr(new Cdr("0", "ok", List.of()), "k/R-o.zip");
        comprobantes.guardar(a);
        comprobantes.guardar(otro);
        bajas.guardar(ComunicacionBaja.crear(otro, 1, "Error en el RUC", Fakes.CLOCK));

        assertThat(service.ficha(a.id()).bajas()).isEmpty();
        assertThat(service.ficha(otro.id()).bajas()).hasSize(1);
    }

    @Test void unComprobanteQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> service.ficha(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
