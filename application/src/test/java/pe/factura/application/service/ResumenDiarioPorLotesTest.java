package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.RechazoDeSolRepository;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #301: las boletas de una misma fecha de emisión que ya solo van en el resumen diario viajan juntas en <b>un</b> RC (hasta 500 líneas), con un solo ticket y un solo CDR.
 * Si SUNAT rechaza el resumen entero (2987) ninguna boleta queda rechazada por culpa de otra: cada una vuelve a informarse sola, y solo la defectuosa termina RECHAZADA.
 */
class ResumenDiarioPorLotesTest {
    static final LocalDate EMISION = LocalDate.of(2026, 9, 13);
    /** El 6.º día: el envío individual ya no vale (1079) y la boleta solo va en el resumen diario. */
    static final Clock SEIS_DIAS_DESPUES = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneId.of("America/Lima"));

    UUID tenantId = UUID.randomUUID();
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Bajas bajas = new Fakes.Bajas();
    Fakes.Series series = new Fakes.Series();
    Fakes.Establecimientos establecimientos = new Fakes.Establecimientos(series);
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Gateway gateway = new Fakes.Gateway();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    Fakes.Ubl ubl = new Fakes.Ubl();
    XsdValidator xsd = new XsdValidator() {
        public void validar(String xml, TipoDocumento tipo) {}
        public void validarBaja(String xml, ComunicacionBaja baja) {}
    };
    XmlSigner signer = (xml, cert) -> new FirmaResultado(xml, "HASH");
    DarDeBajaService resumen;
    EnviarDocumentoService enviar;
    EmitirComprobanteService emitir;

    @BeforeEach void setUp() {
        comprobantes.bajasParaAgrupar = bajas;
        tenants.guardar(Fakes.tenantListo(tenantId));
        series.crear(new Serie(tenantId, TipoDocumento.BOLETA, "B001", 0, true));
        EnviarDocumentoService alEmitir = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, Fakes.CLOCK);
        emitir = new EmitirComprobanteService(comprobantes, series, tenants, storage, ubl, xsd, signer, alEmitir, Fakes.UOW, Fakes.CLOCK, establecimientos, bajas, new Fakes.Idempotencias(), new Fakes.Tope(), Fakes.SIN_COMPRADOR);
        resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, ubl, xsd, signer, gateway, cdrs, outbox, Fakes.UOW, SEIS_DIAS_DESPUES);
        enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, SEIS_DIAS_DESPUES, RechazoDeSolRepository.NINGUNO, resumen);
    }

    /** Una boleta firmada que nadie envió. */
    private Comprobante boleta(LocalDate emision) {
        return emitir.emitirFactura(tenantId, new EmitirFacturaCommand("B001", null, emision, null, "PEN", "0101",
                new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, null, false));
    }

    private EstadoDocumento estado(Comprobante c) { return comprobantes.buscar(tenantId, c.id()).orElseThrow().estado(); }

    @Test void tresBoletasDeLaMismaFechaSeInformanEnUnSoloResumenConUnSoloTicketYUnSoloCdr() {
        List<Comprobante> boletas = List.of(boleta(EMISION), boleta(EMISION), boleta(EMISION));

        Comprobante primera = enviar.enviar(tenantId, boletas.get(0).id());

        assertThat(ubl.lineasPorResumen).as("un XML de tres líneas").containsExactly(3);
        assertThat(gateway.resumenes).as("un sendSummary, no tres").isEqualTo(1);
        assertThat(gateway.ultimoNombre).isEqualTo("20100066603-RC-20260919-1");
        assertThat(primera.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(boletas).allSatisfy(b -> assertThat(estado(b)).isEqualTo(EstadoDocumento.ACEPTADO));
        List<ComunicacionBaja> filas = bajas.delResumen(tenantId, LocalDate.of(2026, 9, 19), 1);
        assertThat(filas).extracting(ComunicacionBaja::linea).containsExactly(1, 2, 3);
        assertThat(filas).extracting(ComunicacionBaja::lineas).containsOnly(3);
        assertThat(filas).extracting(ComunicacionBaja::estado).containsOnly(EstadoBaja.ACEPTADA);
        assertThat(filas).extracting(ComunicacionBaja::ticket).containsOnly("T-1");
        assertThat(filas).extracting(ComunicacionBaja::xmlKey).doesNotContainNull().containsOnly(filas.get(0).xmlKey());
        assertThat(filas).extracting(ComunicacionBaja::cdrKey).doesNotContainNull().containsOnly(filas.get(0).cdrKey());
        assertThat(boletas).extracting(b -> comprobantes.buscar(tenantId, b.id()).orElseThrow().cdrKey()).containsOnly(filas.get(0).cdrKey());
        assertThat(outbox.filas).as("nada que reintentar").isEmpty();
        // El envío de otra del mismo lote ya no procede: el outbox la descarta (ESTADO_NO_ENVIABLE).
        assertThatThrownBy(() -> enviar.enviar(tenantId, boletas.get(1).id())).isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).codigo()).isEqualTo("ESTADO_NO_ENVIABLE");
    }

    @Test void cadaFechaDeEmisionVaEnSuResumen() {
        Comprobante a1 = boleta(EMISION), a2 = boleta(EMISION), b1 = boleta(EMISION.minusDays(1));

        enviar.enviar(tenantId, a1.id());
        enviar.enviar(tenantId, b1.id());

        assertThat(ubl.lineasPorResumen).containsExactly(2, 1);
        assertThat(bajas.deComprobante(tenantId, a2.id()).get(0).identificador()).isEqualTo("RC-20260919-1");
        assertThat(bajas.deComprobante(tenantId, b1.id()).get(0).identificador()).isEqualTo("RC-20260919-2");
    }

    @Test void unaBoletaQueYaTieneSuResumenEnCursoNoEntraEnOtro() {
        Comprobante a = boleta(EMISION);
        gateway.statusCode = "98";   // SUNAT sigue procesando: el resumen queda ENVIADA
        enviar.enviar(tenantId, a.id());
        Comprobante b = boleta(EMISION);

        enviar.enviar(tenantId, b.id());

        assertThat(ubl.lineasPorResumen).as("la segunda no se une a un resumen que ya salió").containsExactly(1, 1);
    }

    @Test void masDeQuinientasSePartenEnVariosResumenes() {
        List<Comprobante> boletas = new ArrayList<>();
        for (int n = 1; n <= 501; n++) {
            Comprobante b = Comprobante.persistido(UUID.randomUUID(), tenantId, TipoDocumento.BOLETA, "B001", (long) n, EMISION, EstadoDocumento.FIRMADO, new Receptor("1", "12345678", "JUAN PEREZ", null),
                    List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "n" + n, "k").rehidratar();
            comprobantes.guardar(b);
            boletas.add(b);
        }

        enviar.enviar(tenantId, boletas.get(0).id());
        assertThat(ubl.lineasPorResumen).containsExactly(500);
        assertThat(estado(boletas.get(499))).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(estado(boletas.get(500))).as("la 501 queda para otro resumen").isEqualTo(EstadoDocumento.FIRMADO);

        enviar.enviar(tenantId, boletas.get(500).id());
        assertThat(ubl.lineasPorResumen).containsExactly(500, 1);
        assertThat(estado(boletas.get(500))).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    /** SUNAT rechaza todo el resumen si una línea falla (2987) y no dice cuál: ninguna se da por rechazada, cada una se vuelve a informar sola. */
    @Test void siSunatRechazaElResumenCadaBoletaSeVuelveAInformarSolaYSoloLaDefectuosaQuedaRechazada() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION), mala = boleta(EMISION);
        cdrs.cdr = new Cdr("2987", "Una línea del resumen tiene errores", List.of());

        enviar.enviar(tenantId, a.id());

        assertThat(ubl.lineasPorResumen).containsExactly(3);
        assertThat(bajas.delResumen(tenantId, LocalDate.of(2026, 9, 19), 1)).extracting(ComunicacionBaja::estado).containsOnly(EstadoBaja.RECHAZADA);
        assertThat(List.of(a, b, mala)).allSatisfy(x -> {
            assertThat(estado(x)).as("ninguna queda rechazada por culpa de otra").isEqualTo(EstadoDocumento.ERROR_ENVIO);
            assertThat(comprobantes.buscar(tenantId, x.id()).orElseThrow().ultimoError()).contains("RC-20260919-1").contains("2987").contains("sola");
            assertThat(outbox.filas).anySatisfy(f -> {
                assertThat(f.accion()).isEqualTo("ENVIAR");
                assertThat(f.agregadoId()).isEqualTo(x.id());
            });
        });

        // Se reprograman en el orden del resumen (una nota detrás de la boleta que modifica): el outbox toma lo vencido por orden de programación.
        assertThat(outbox.filas).filteredOn(f -> f.accion().equals("ENVIAR")).extracting(f -> f.agregadoId()).containsExactly(a.id(), b.id(), mala.id());
        assertThat(outbox.filas.stream().filter(f -> f.accion().equals("ENVIAR")).map(f -> f.cuando()).toList()).isSorted().doesNotHaveDuplicates();

        cdrs.cdr = new Cdr("0", "aceptado", List.of());
        assertThat(enviar.enviar(tenantId, a.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(enviar.enviar(tenantId, b.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        cdrs.cdr = new Cdr("2987", "Una línea del resumen tiene errores", List.of());
        assertThat(enviar.enviar(tenantId, mala.id()).estado()).as("sola, su rechazo ya es suyo").isEqualTo(EstadoDocumento.RECHAZADO);

        assertThat(ubl.lineasPorResumen).as("el de tres y luego uno por boleta").containsExactly(3, 1, 1, 1);
        assertThat(bajas.deComprobante(tenantId, a.id()).stream().map(ComunicacionBaja::identificador)).contains("RC-20260919-2");
    }

    /** Una boleta nueva de la misma fecha no se une a las que ya se aíslan de a una tras un rechazo del resumen entero: va con las que no están aisladas. */
    @Test void lasBoletasAisladasTrasUnRechazoNoVuelvenAAgruparseConUnaNueva() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION);
        cdrs.cdr = new Cdr("2987", "Una línea del resumen tiene errores", List.of());
        enviar.enviar(tenantId, a.id());
        cdrs.cdr = new Cdr("0", "aceptado", List.of());
        Comprobante nueva = boleta(EMISION);

        enviar.enviar(tenantId, nueva.id());

        assertThat(ubl.lineasPorResumen).as("el rechazado de dos líneas y la nueva sola: a y b siguen esperando su turno").containsExactly(2, 1);
        assertThat(estado(nueva)).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(estado(a)).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(estado(b)).isEqualTo(EstadoDocumento.ERROR_ENVIO);
    }

    /** La que se aísla va sola aunque haya otras nuevas esperando: de lo contrario volvería a agruparse con ellas y a rebotar con el mismo rechazo. */
    @Test void unaBoletaAisladaVaSolaAunqueHayaOtrasNuevasEsperando() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION);
        cdrs.cdr = new Cdr("2987", "Una línea del resumen tiene errores", List.of());
        enviar.enviar(tenantId, a.id());
        cdrs.cdr = new Cdr("0", "aceptado", List.of());
        Comprobante nueva = boleta(EMISION);

        enviar.enviar(tenantId, a.id());

        assertThat(ubl.lineasPorResumen).as("el rechazado de dos líneas y luego a sola: la nueva no se le une").containsExactly(2, 1);
        assertThat(estado(a)).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(estado(nueva)).isEqualTo(EstadoDocumento.FIRMADO);
        assertThat(estado(b)).isEqualTo(EstadoDocumento.ERROR_ENVIO);
    }

    /** El defecto de una hermana (aquí, la plantilla que falla con ella) no puede bloquear a las demás boletas de la fecha. */
    @Test void unaHermanaQueNoSePuedeProducirNoBloqueaAlasDemas() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION), mala = boleta(EMISION);
        pe.factura.application.port.out.UblGenerator conCulpable = new pe.factura.application.port.out.UblGenerator() {
            public String generar(Comprobante c, pe.factura.domain.tenant.Tenant t) { return ubl.generar(c, t); }
            public String generarBaja(ComunicacionBaja baja, Comprobante c, pe.factura.domain.tenant.Tenant t) { return ubl.generarBaja(baja, c, t); }
            public String generarResumen(List<ComunicacionBaja> filas, List<Comprobante> comprobantes, pe.factura.domain.tenant.Tenant t) {
                if (comprobantes.stream().anyMatch(x -> x.id().equals(mala.id()))) throw new IllegalStateException("la plantilla no puede con " + mala.serie() + "-" + mala.numero());
                return ubl.generarResumen(filas, comprobantes, t);
            }
        };
        resumen = new DarDeBajaService(bajas, comprobantes, tenants, storage, conCulpable, xsd, signer, gateway, cdrs, outbox, Fakes.UOW, SEIS_DIAS_DESPUES);
        enviar = new EnviarDocumentoService(comprobantes, tenants, storage, gateway, cdrs, outbox, Fakes.UOW, SEIS_DIAS_DESPUES, RechazoDeSolRepository.NINGUNO, resumen);

        assertThat(enviar.enviar(tenantId, a.id()).estado()).as("a sale sola, no se queda sin informar por culpa de la hermana").isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(enviar.enviar(tenantId, b.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);

        assertThat(ubl.lineasPorResumen).containsExactly(1, 1);
        assertThat(estado(mala)).as("la defectuosa sigue esperando").isEqualTo(EstadoDocumento.FIRMADO);
        assertThatThrownBy(() -> enviar.enviar(tenantId, mala.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("la plantilla no puede");
    }

    /**
     * El worker y un GET de cualquier fila del resumen pueden resolver el mismo CDR a la vez. Si mientras uno espera a SUNAT el otro ya devolvió las boletas al envío y una se
     * reinformó en un resumen nuevo, el resultado viejo no le toca a esa boleta: lo resuelve el nuevo. Sin esa guarda, el rechazo viejo la devolvía otra vez al envío con su
     * resumen nuevo en curso, y el CDR aceptado de este se perdía.
     */
    @Test void elResultadoViejoDeUnResumenNoTocaALaBoletaYaReinformadaEnUnoNuevo() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION);
        gateway.statusCode = "98";
        enviar.enviar(tenantId, a.id());
        ComunicacionBaja cabeza = bajas.deComprobante(tenantId, a.id()).get(0);
        assertThat(cabeza.deVariasLineas()).isTrue();
        gateway.statusCode = "0";
        cdrs.cdr = new Cdr("2987", "Una línea del resumen tiene errores", List.of());
        gateway.alConsultar = () -> {
            // «El otro proceso»: resuelve el rechazo, y a se reinforma sola en un resumen nuevo que SUNAT todavía procesa.
            resumen.continuar(tenantId, cabeza.id());
            gateway.statusCode = "98";
            enviar.enviar(tenantId, a.id());
            gateway.statusCode = "0";
        };

        resumen.continuar(tenantId, cabeza.id());   // este vio el mismo rechazo antes que el otro lo aplicara

        assertThat(estado(a)).as("a está en su resumen nuevo, no devuelta otra vez al envío").isEqualTo(EstadoDocumento.ENVIADO);
        assertThat(bajas.deComprobante(tenantId, a.id()).stream().filter(x -> x.lineas() == 1).findFirst().orElseThrow().estado()).isEqualTo(EstadoBaja.ENVIADA);
        assertThat(estado(b)).isEqualTo(EstadoDocumento.ERROR_ENVIO);
    }

    @Test void siElEnvioFallaTodasLasFilasQuedanEnErrorYElOutboxLlevaUnaSolaFilaPorResumen() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION), c = boleta(EMISION);
        gateway.fallaResumen = new SunatTransientException("0109", "timeout");

        enviar.enviar(tenantId, a.id());

        List<ComunicacionBaja> filas = bajas.delResumen(tenantId, LocalDate.of(2026, 9, 19), 1);
        assertThat(filas).hasSize(3).extracting(ComunicacionBaja::estado).containsOnly(EstadoBaja.ERROR_ENVIO);
        assertThat(filas).extracting(ComunicacionBaja::ticket).containsOnlyNulls();
        assertThat(outbox.filas).filteredOn(f -> f.accion().equals("BAJA")).singleElement().satisfies(f -> assertThat(f.agregadoId()).isEqualTo(filas.get(0).id()));
        assertThat(List.of(a, b, c)).allSatisfy(x -> assertThat(estado(x)).isEqualTo(EstadoDocumento.ENVIADO));

        // El reintento puede llegar por cualquier fila (el usuario consulta la de su comprobante): resuelve el resumen entero.
        gateway.fallaResumen = null;
        ComunicacionBaja tercera = resumen.continuar(tenantId, filas.get(2).id());

        assertThat(tercera.estado()).isEqualTo(EstadoBaja.ACEPTADA);
        assertThat(gateway.resumenes).as("el segundo intento, uno solo").isEqualTo(2);
        assertThat(List.of(a, b, c)).allSatisfy(x -> assertThat(estado(x)).isEqualTo(EstadoDocumento.ACEPTADO));
        assertThat(bajas.delResumen(tenantId, LocalDate.of(2026, 9, 19), 1)).extracting(ComunicacionBaja::estado).containsOnly(EstadoBaja.ACEPTADA);
    }

    @Test void siSunatSigueProcesandoElTicketEsUnoYSeReconsultaUnaVez() {
        Comprobante a = boleta(EMISION), b = boleta(EMISION);
        gateway.statusCode = "98";

        enviar.enviar(tenantId, a.id());

        List<ComunicacionBaja> filas = bajas.delResumen(tenantId, LocalDate.of(2026, 9, 19), 1);
        assertThat(filas).extracting(ComunicacionBaja::estado).containsOnly(EstadoBaja.ENVIADA);
        assertThat(filas).extracting(ComunicacionBaja::ticket).containsOnly("T-1");
        assertThat(outbox.filas).filteredOn(f -> f.accion().equals("BAJA")).hasSize(1);
        assertThat(gateway.consultas).isEqualTo(1);

        gateway.statusCode = "0";
        resumen.continuar(tenantId, filas.get(0).id());

        assertThat(gateway.consultas).as("una consulta por resumen, no por línea").isEqualTo(2);
        assertThat(estado(a)).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(estado(b)).isEqualTo(EstadoDocumento.ACEPTADO);
    }
}
