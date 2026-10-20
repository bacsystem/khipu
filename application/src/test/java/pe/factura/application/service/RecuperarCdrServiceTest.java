package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.out.SunatConsultaGateway.Consulta;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
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
        assertThat(service.recuperarPendientes().recuperados()).extracting(Comprobante::id).containsExactly(c.id());
        assertThat(consultas.llamadas).isEqualTo(1);
        // SUNAT caído: no explota, lo intenta la siguiente pasada.
        Comprobante d = Fakes.facturaFirmada(tenantId, storage);
        d.marcarEnviado(); comprobantes.guardar(d);
        consultas.falla = new SunatTransientException("0000", "caído");
        RecuperarCdrUseCase.Barrido sinSunat = service.recuperarPendientes();
        assertThat(sinSunat.recuperados()).isEmpty();
        assertThat(sinSunat.fallos()).extracting(f -> f.comprobante().id()).containsExactly(d.id());
        assertThat(comprobantes.buscar(tenantId, d.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }

    // --- #357: getStatusCdr solo existe para 01/07/08 serie F ------------------------------------------------------------------------------

    /** La boleta y sus notas no se le preguntan a SUNAT por getStatusCdr: ni siquiera ENVIADAS o en error de envío, que son los estados que sí se consultan en una factura. */
    @Test void laBoletaYSusNotasNoSeConsultanPorGetStatusCdr() {
        for (Comprobante c : List.of(Fakes.boletaFirmada(tenantId, storage, 1), Fakes.notaDeBoletaFirmada(tenantId, storage, 1))) {
            c.marcarEnviado();
            comprobantes.guardar(c);
            assertThatThrownBy(() -> service.recuperar(tenantId, c.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CDR_NO_CONSULTABLE");
            assertThatThrownBy(() -> service.recuperar(tenantId, c.id())).hasMessageContaining("resumen diario").hasMessageContaining("ticket");
        }
        assertThat(consultas.llamadas).isZero();
    }

    /** #327: getStatusCdr tampoco cubre la liquidación de compra (manual: 01, 07 y 08); su constancia llega reenviándola. */
    @Test void laLiquidacionDeCompraNoSeConsultaPorGetStatusCdr() {
        Comprobante l = Fakes.liquidacionFirmada(tenantId, storage, 1);
        l.marcarEnviado();
        comprobantes.guardar(l);
        assertThatThrownBy(() -> service.recuperar(tenantId, l.id())).isInstanceOf(DomainException.class).hasMessageContaining("liquidaciones de compra")
                .extracting("codigo").isEqualTo("CDR_NO_CONSULTABLE");
        assertThat(consultas.llamadas).isZero();
    }

    /**
     * En e-beta no hay billConsultService (404): la consulta de una factura responde NO_DISPONIBLE_EN_BETA desde el gateway. A una boleta ni se le llega: el 409 de «no se
     * consulta» va primero y es el mismo en cualquier entorno, así que el integrador no ve un 422 de beta que sugiera que en producción sí funcionaría.
     */
    @Test void laBoletaDeUnaEmpresaEnBetaTambienEsConflictoYNoLlegaAlGateway() {
        UUID beta = UUID.randomUUID();
        tenants.guardar(Fakes.tenantListo(beta));   // BETA
        Comprobante b = Fakes.boletaFirmada(beta, storage, 1);
        b.marcarEnviado(); comprobantes.guardar(b);
        assertThatThrownBy(() -> service.recuperar(beta, b.id())).extracting("codigo").isEqualTo("CDR_NO_CONSULTABLE");
        assertThat(consultas.llamadas).isZero();
    }

    /** Un CDR perdido de una boleta ya aceptada tampoco se pide por esta vía: el 409 es el mismo y no depende del estado. */
    @Test void laBoletaAceptadaSinCdrTampocoSeConsulta() {
        Comprobante b = Fakes.boletaFirmada(tenantId, storage, 1);
        b.marcarEnviado(); b.aplicarCdr(new Cdr("0", "ok", List.of()), null);
        comprobantes.guardar(b);
        assertThatThrownBy(() -> service.recuperar(tenantId, b.id())).extracting("codigo").isEqualTo("CDR_NO_CONSULTABLE");
        assertThat(consultas.llamadas).isZero();
    }

    /** El barrido ignora lo de boleta aunque la consulta del repositorio se lo entregue: no pregunta a SUNAT ni lo cuenta como fallo. */
    @Test void elBarridoNoConsultaLoDeBoleta() {
        Comprobante boleta = Fakes.boletaFirmada(tenantId, storage, 1);
        Comprobante notaB = Fakes.notaDeBoletaFirmada(tenantId, storage, 1);
        Comprobante factura = Fakes.facturaFirmada(tenantId, storage, 1);
        for (Comprobante c : List.of(boleta, notaB, factura)) { c.marcarEnviado(); comprobantes.guardar(c); }

        RecuperarCdrUseCase.Barrido r = service.recuperarPendientes();

        assertThat(r.recuperados()).extracting(Comprobante::id).containsExactly(factura.id());
        assertThat(r.fallos()).isEmpty();
        assertThat(consultas.llamadas).isEqualTo(1);
    }

    /**
     * Un fault definitivo de SUNAT (o cualquier otra excepción) en un comprobante no corta el barrido: la consulta va por fecha de emisión, así que uno atascado al frente
     * dejaría sin recuperar a todos los más nuevos, cada hora y para siempre. Cada fallo vuelve con su comprobante para que quien llama lo registre.
     */
    @Test void unFaultDefinitivoNoCortaElBarrido() {
        Comprobante atascada = Fakes.facturaFirmada(tenantId, storage, 1);
        Comprobante nueva = Fakes.facturaFirmada(tenantId, storage, 2);
        for (Comprobante c : List.of(atascada, nueva)) { c.marcarEnviado(); comprobantes.guardar(c); }
        consultas.fallaPorNumero.put(1L, new SunatRechazoException("0151", "El archivo ZIP no contiene el comprobante"));

        RecuperarCdrUseCase.Barrido r = service.recuperarPendientes();

        assertThat(r.recuperados()).extracting(Comprobante::id).containsExactly(nueva.id());
        assertThat(comprobantes.buscar(tenantId, nueva.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(r.fallos()).hasSize(1);
        assertThat(r.fallos().get(0).comprobante().id()).isEqualTo(atascada.id());
        assertThat(r.fallos().get(0).causa()).isInstanceOf(SunatRechazoException.class);
        assertThat(consultas.llamadas).isEqualTo(2);
    }

    /** Lo inesperado (un fallo del storage, un CDR ilegible) tampoco: se informa y el barrido sigue con el siguiente. */
    @Test void unaExcepcionInesperadaNoCortaElBarrido() {
        Comprobante rota = Fakes.facturaFirmada(tenantId, storage, 1);
        Comprobante sana = Fakes.facturaFirmada(tenantId, storage, 2);
        for (Comprobante c : List.of(rota, sana)) { c.marcarEnviado(); comprobantes.guardar(c); }
        consultas.fallaPorNumero.put(1L, new IllegalStateException("storage caído"));

        RecuperarCdrUseCase.Barrido r = service.recuperarPendientes();

        assertThat(r.recuperados()).extracting(Comprobante::id).containsExactly(sana.id());
        assertThat(r.fallos()).extracting(f -> f.causa().getMessage()).containsExactly("storage caído");
    }

    /**
     * Revisión de #357 (H1): buscar la empresa también puede fallar (credenciales cifradas ilegibles de una sola empresa) y estaba fuera del manejo: una empresa rota al
     * frente cortaba el barrido de todas las demás, el mismo defecto que el issue corrige para la consulta a SUNAT.
     */
    @Test void unaEmpresaQueNoSePuedeLeerNoCortaElBarridoDeLasDemas() {
        UUID ilegible = UUID.randomUUID();
        Fakes.Tenants conUnaRota = new Fakes.Tenants() {
            @Override public java.util.Optional<Tenant> buscar(UUID id) {
                if (id.equals(ilegible)) throw new IllegalStateException("no se pudo descifrar las credenciales");
                return super.buscar(id);
            }
        };
        conUnaRota.guardar(produccion(Fakes.tenantListo(tenantId)));
        Comprobante deLaRota = Fakes.facturaFirmada(ilegible, storage, 1);
        Comprobante sana = Fakes.facturaFirmada(tenantId, storage, 2);
        for (Comprobante c : List.of(deLaRota, sana)) { c.marcarEnviado(); comprobantes.guardar(c); }

        RecuperarCdrUseCase.Barrido r = new RecuperarCdrService(comprobantes, conUnaRota, storage, consultas, cdrs, Fakes.UOW).recuperarPendientes();

        assertThat(r.recuperados()).extracting(Comprobante::id).containsExactly(sana.id());
        assertThat(r.fallos()).extracting(f -> f.comprobante().id()).containsExactly(deLaRota.id());
    }

    /** Revisión de #357 (H3): una boleta que ya tiene su CDR (el del envío individual o el del resumen) no se manda al resumen: se le dice que lo descargue. */
    @Test void laBoletaQueYaTieneSuCdrDiceQueSeDescarga() {
        Comprobante b = Fakes.boletaFirmada(tenantId, storage, 1);
        b.marcarEnviado(); b.aplicarCdr(new Cdr("0", "ok", List.of()), "k/R-" + b.nombreArchivo() + ".zip");
        comprobantes.guardar(b);
        assertThatThrownBy(() -> service.recuperar(tenantId, b.id())).extracting("codigo").isEqualTo("CDR_YA_DISPONIBLE");
        assertThat(consultas.llamadas).isZero();
    }

    /** Revisión de #357 (H3): una boleta que se envió sola y quedó en ERROR_ENVIO no tiene resumen que consultar; el camino es reintentar el envío. */
    @Test void laBoletaEnErrorDeEnvioSabeQueSeReintentaElEnvio() {
        Comprobante b = Fakes.boletaFirmada(tenantId, storage, 1);
        b.marcarErrorEnvio("0109 - El sistema no puede responder");
        comprobantes.guardar(b);
        assertThatThrownBy(() -> service.recuperar(tenantId, b.id())).hasMessageContaining("ERROR_ENVIO").hasMessageContaining("POST /v1/facturas/{id}/enviar");
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
