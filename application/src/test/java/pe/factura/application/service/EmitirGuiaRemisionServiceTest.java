package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirGuiaRemisionCommand;
import pe.factura.application.port.in.EmitirGuiaRemisionUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.GuiaUblGenerator;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.factura.application.service.FakesDeGuia.*;

/** #39: la emisión de la guía de remisión numera, firma, valida y guarda; lo que se puede rechazar se rechaza antes de numerar; los establecimientos salen de #80. */
class EmitirGuiaRemisionServiceTest {
    UUID tenant = UUID.randomUUID();
    Guias guias = new Guias();
    Fakes.Series series = new Fakes.Series();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos(series);
    Fakes.Storage storage = new Fakes.Storage();
    Gre gre = new Gre();
    Fakes.Idempotencias claves = new Fakes.Idempotencias();
    final List<String> tiposValidados = new ArrayList<>();
    final List<UUID> enviadas = new ArrayList<>();
    GuiaUblGenerator ubl = (g, t) -> "<DespatchAdvice>" + g.nombreArchivo() + "|" + t.ruc() + "</DespatchAdvice>";
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml.replace("<DespatchAdvice>", "<DespatchAdvice><ds:Signature/>"), "HASH-GRE");
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) { tiposValidados.add(tipo.codigo()); }
        public void validarBaja(String xml, ComunicacionBaja b) {}
    };
    EnviarGuiaRemisionUseCase enviar = new EnviarGuiaRemisionUseCase() {
        public GuiaRemision enviar(UUID t, UUID id) { enviadas.add(id); return guias.buscar(t, id).orElseThrow(); }
        public GuiaRemision consultarTicket(UUID t, UUID id) { throw new UnsupportedOperationException(); }
    };
    EmitirGuiaRemisionService service;

    @BeforeEach void setUp() {
        tenants.guardar(tenantGre(tenant));
        series.crear(new Serie(tenant, TipoDocumento.GUIA_REMITENTE, "T001", 0, true));
        series.crear(new Serie(tenant, TipoDocumento.GUIA_TRANSPORTISTA, "V001", 0, true));
        service = new EmitirGuiaRemisionService(guias, series, tenants, establecimientos, storage, ubl, xsd, signer, gre, enviar, claves, Fakes.UOW, Fakes.CLOCK);
    }

    static EmitirGuiaRemisionCommand pedido(Traslado t, boolean enviar) {
        return new EmitirGuiaRemisionCommand("T001", HOY, cliente(), venta(), t, items(), List.of(), null, enviar);
    }

    @Test void numeraFirmaValidaYGuardaSinEnviar() {
        GuiaRemision g = service.emitir(tenant, pedido(privado(), false), null).guia();
        assertThat(g.numero()).isEqualTo(1);
        assertThat(g.nombreArchivo()).isEqualTo("20100066603-09-T001-1");
        assertThat(g.estado()).isEqualTo(EstadoGuia.FIRMADO);
        assertThat(g.hash()).isEqualTo("HASH-GRE");
        assertThat(g.xmlKey()).isEqualTo(tenant + "/2026/09/guias/20100066603-09-T001-1.xml");
        assertThat(new String(storage.leer(g.xmlKey()))).startsWith("<DespatchAdvice><ds:Signature/>20100066603-09-T001-1|20100066603");
        assertThat(tiposValidados).containsExactly("09");
        assertThat(guias.buscar(tenant, g.id())).isPresent();
        assertThat(enviadas).isEmpty();
        assertThat(gre.envios).isZero();
        assertThat(service.emitir(tenant, pedido(privado(), false), null).guia().numero()).as("correlativo").isEqualTo(2);
    }

    @Test void conEnvioAutomaticoLaEnviaDespuesDeGuardarla() {
        GuiaRemision g = service.emitir(tenant, pedido(privado(), true), null).guia();
        assertThat(enviadas).containsExactly(g.id());
    }

    @Test void laTransportistaSeNumeraEnSuSerieV() {
        EmitirGuiaRemisionCommand cmd = new EmitirGuiaRemisionCommand("V001", HOY, cliente(), new DatosDeGuia.DeTransportista(new ParteDeGuia("6", "20555555556", "DISTRIBUIDORA NORTE"), "1512345CNG"),
                privado(), items(), List.of(), null, false);
        GuiaRemision g = service.emitir(tenant, cmd, null).guia();
        assertThat(g.nombreArchivo()).isEqualTo("20100066603-31-V001-1");
        assertThat(tiposValidados).containsExactly("31");
    }

    @Test void loQueSeRechazaAntesDeNumerarNoGastaCorrelativo() {
        Tenant sinApi = tenantGre(tenant).conCredencialesApiSunat(null);
        tenants.guardar(sinApi);
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(), true), null)).hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_API_SUNAT_NO_CARGADAS");
        tenants.guardar(tenantGre(tenant).conCredencialesSol(null));
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(), true), null)).hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_SOL_NO_CARGADAS");
        tenants.guardar(tenantGre(tenant));
        gre.disponible = false;
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(), true), null)).hasFieldOrPropertyWithValue("codigo", "GRE_NO_DISPONIBLE");
        assertThatThrownBy(() -> service.emitir(tenant, new EmitirGuiaRemisionCommand("T001", HOY, cliente(), new DatosDeGuia.DeRemitente("99", null, ModalidadTraslado.PRIVADO, null),
                privado(), items(), List.of(), null, false), null)).hasFieldOrPropertyWithValue("codigo", "TRASLADO_INVALIDO");
        tenants.guardar(tenantGre(tenant).conCertificado(null));
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(), false), null)).hasFieldOrPropertyWithValue("codigo", "CERTIFICADO_NO_CARGADO");
        assertThat(guias.datos).isEmpty();
        tenants.guardar(tenantGre(tenant));
        assertThat(service.emitir(tenant, pedido(privado(), false), null).guia().numero()).isEqualTo(1);
        // Sin enviar en el acto, no hacen falta las credenciales API ni la API GRE: se puede emitir para revisar el XML.
        tenants.guardar(tenantGre(tenant).conCredencialesApiSunat(null));
        assertThat(service.emitir(tenant, pedido(privado(), false), null).guia().numero()).isEqualTo(2);
    }

    @Test void unaSerieSinConfigurarNoEmite() {
        EmitirGuiaRemisionCommand cmd = new EmitirGuiaRemisionCommand("T002", HOY, cliente(), venta(), privado(), items(), List.of(), null, false);
        assertThatThrownBy(() -> service.emitir(tenant, cmd, null)).hasFieldOrPropertyWithValue("codigo", "SERIE_NO_CONFIGURADA");
        assertThat(guias.datos).isEmpty();
    }

    @Test void unEstablecimientoPropioSeCompletaConElRegistroDeLaEmpresa() {
        establecimientos.guardar(new Establecimiento(tenant, "0001", "Almacén Callao", Domicilio.de("070101", "Av. Gambetta 500, Callao"), true));
        GuiaRemision g = service.emitir(tenant, pedido(privado(new PuntoDeTraslado(null, null, "0001", null), new PuntoDeTraslado(null, null, "0000", null)), false), null).guia();
        assertThat(g.traslado().partida()).isEqualTo(new PuntoDeTraslado("070101", "Av. Gambetta 500, Callao", "0001", EMISOR));
        assertThat(g.traslado().llegada()).as("0000: el domicilio fiscal").isEqualTo(new PuntoDeTraslado("150101", "Av. Lima 123, Lima", "0000", EMISOR));
    }

    @Test void unEstablecimientoPropioQueNoExisteOEstaDeBajaOTraeOtroUbigeoSeRechaza() {
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(new PuntoDeTraslado(null, null, "0007", null), PuntoDeTraslado.de("040101", "Calle 1")), false), null))
                .hasFieldOrPropertyWithValue("codigo", "TRASLADO_INVALIDO").hasMessageStartingWith("3366");
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(PuntoDeTraslado.de("150101", "Av. X 1"), new PuntoDeTraslado(null, null, "0007", EMISOR)), false), null))
                .hasMessageStartingWith("3370");
        establecimientos.guardar(new Establecimiento(tenant, "0002", "Tienda vieja", Domicilio.de("070101", "Av. Gambetta 500"), false));
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(new PuntoDeTraslado(null, null, "0002", null), PuntoDeTraslado.de("040101", "Calle 1")), false), null))
                .hasMessageStartingWith("3366").hasMessageContaining("baja");
        establecimientos.guardar(new Establecimiento(tenant, "0001", "Almacén Callao", Domicilio.de("070101", "Av. Gambetta 500"), true));
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(new PuntoDeTraslado("150101", "Av. Gambetta 500", "0001", null), PuntoDeTraslado.de("040101", "Calle 1")), false), null))
                .hasMessageStartingWith("3367");
        tenants.guardar(tenantGre(tenant).conDomicilio(null));
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(new PuntoDeTraslado(null, null, "0000", null), PuntoDeTraslado.de("040101", "Calle 1")), false), null))
                .hasMessageStartingWith("3366").hasMessageContaining("domicilio fiscal");
        assertThat(guias.datos).isEmpty();
    }

    @Test void elEstablecimientoDeOtroRucNoSeBuscaEnLaEmpresa() {
        GuiaRemision g = service.emitir(tenant, pedido(privado(PuntoDeTraslado.de("150101", "Av. X 1"), new PuntoDeTraslado("040101", "Calle Mercaderes 200", "0003", "20601234565")), false), null).guia();
        assertThat(g.traslado().llegada().rucEstablecimiento()).isEqualTo("20601234565");
    }

    @Test void unReintentoConLaMismaClaveDevuelveLaMismaGuiaSinNumerarOtra() {
        Idempotencia clave = new Idempotencia("5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f", "huella-1");
        EmitirGuiaRemisionUseCase.Emision primera = service.emitir(tenant, pedido(privado(), false), clave);
        EmitirGuiaRemisionUseCase.Emision segunda = service.emitir(tenant, pedido(privado(), false), clave);
        assertThat(primera.repetida()).isFalse();
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.guia().id()).isEqualTo(primera.guia().id());
        assertThat(guias.datos).hasSize(1);
        assertThat(claves.filas).containsOnlyKeys("guia:" + tenant + "|" + clave.clave());
        assertThatThrownBy(() -> service.emitir(tenant, pedido(privado(), false), new Idempotencia(clave.clave(), "otra-huella")))
                .hasFieldOrPropertyWithValue("codigo", "IDEMPOTENCIA_INVALIDA");
        // Un reintento no se vuelve a enviar.
        service.emitir(tenant, pedido(privado(), true), clave);
        assertThat(enviadas).isEmpty();
    }

    @Test void validarArmaLaGuiaSinNumerarNiGuardar() {
        establecimientos.guardar(new Establecimiento(tenant, "0001", "Almacén Callao", Domicilio.de("070101", "Av. Gambetta 500"), true));
        GuiaRemision g = service.validar(tenant, pedido(privado(new PuntoDeTraslado(null, null, "0001", null), PuntoDeTraslado.de("040101", "Calle 1")), true));
        assertThat(g.numero()).isNull();
        assertThat(g.estado()).isEqualTo(EstadoGuia.RECIBIDO);
        assertThat(g.traslado().partida().ubigeo()).isEqualTo("070101");
        assertThat(guias.datos).isEmpty();
        assertThat(series.ultimo.get(tenant + "09T001")).isZero();
        assertThatThrownBy(() -> service.validar(tenant, new EmitirGuiaRemisionCommand("T001", HOY, cliente(), venta(), null, items(), List.of(), null, false)))
                .isInstanceOf(DomainException.class);
    }
}
