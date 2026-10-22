package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #39: cada regla de la guía de remisión, con el código de SUNAT que cita, y sus bordes. */
class GuiaRemisionTest {
    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), LIMA);
    static final LocalDate HOY = LocalDate.of(2026, 10, 20);
    static final String EMISOR = "20100066603";
    static final String CLIENTE = "20601234565";
    static final String EMPRESA_DE_TRANSPORTE = "20480072872";
    static final String OTRO_REMITENTE = "20555555556";
    static final UUID TENANT = UUID.randomUUID();

    static ParteDeGuia cliente() { return new ParteDeGuia("6", CLIENTE, "COMERCIAL ANDINA S.A.C."); }
    static List<ItemDeGuia> items() { return List.of(new ItemDeGuia("P001", "Laptop 15 pulgadas", "NIU", new BigDecimal("2"), null)); }
    static Vehiculo camion() { return new Vehiculo("ABC-123"); }
    static Conductor chofer() { return new Conductor("1", "45678912", "JUAN", "QUISPE MAMANI", "Q45678912"); }

    static Traslado privado() { return traslado(List.of(camion()), List.of(chofer()), Set.of()); }
    static Traslado traslado(List<Vehiculo> v, List<Conductor> c, Set<IndicadorDeTraslado> ind) {
        return new Traslado(HOY, new BigDecimal("25.5"), "KGM", null, PuntoDeTraslado.de("150101", "Av. Argentina 1234, Lima"), PuntoDeTraslado.de("040101", "Calle Mercaderes 200, Arequipa"),
                v, c, ind);
    }
    static DatosDeGuia.DeRemitente venta(ModalidadTraslado m, TransportistaContratado t) { return new DatosDeGuia.DeRemitente("01", null, m, t); }
    static TransportistaContratado empresaDeTransporte() { return new TransportistaContratado(EMPRESA_DE_TRANSPORTE, "TRANSPORTES DEL SUR S.A.C.", "1512345CNG"); }

    static GuiaRemision remitente(DatosDeGuia.DeRemitente d, Traslado t) { return remitente(d, t, cliente(), List.of()); }
    static GuiaRemision remitente(DatosDeGuia.DeRemitente d, Traslado t, ParteDeGuia dest, List<DocumentoDeTraslado> docs) {
        return GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, dest, d, t, items(), docs, null, RELOJ);
    }
    static GuiaRemision transportista(DatosDeGuia.DeTransportista d, Traslado t) {
        return GuiaRemision.crear(TENANT, EMISOR, "V001", HOY, cliente(), d, t, items(), List.of(), null, RELOJ);
    }
    static DatosDeGuia.DeTransportista deTransportista() { return new DatosDeGuia.DeTransportista(new ParteDeGuia("6", OTRO_REMITENTE, "DISTRIBUIDORA NORTE S.A."), "1512345CNG"); }

    static void rechaza(Runnable r, String codigo, String regla) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("codigo", codigo).message().startsWith(regla);
    }

    @Test void unaGuiaRemitenteEnTransportePrivadoSeArmaConSusDatos() {
        GuiaRemision g = remitente(venta(ModalidadTraslado.PRIVADO, null), privado());
        assertThat(g.tipo()).isEqualTo(TipoDocumento.GUIA_REMITENTE);
        assertThat(g.estado()).isEqualTo(EstadoGuia.RECIBIDO);
        assertThat(g.fechaEmision()).isEqualTo(HOY);
        assertThat(g.horaEmision()).isNotNull();
        assertThat(g.remitente()).hasValueSatisfying(r -> {
            assertThat(r.motivo()).isEqualTo("01");
            assertThat(r.descripcionMotivo()).as("la del catálogo 20 si no se da").isEqualTo("Venta");
        });
        assertThat(g.transportista()).isEmpty();
        assertThat(g.traslado().vehiculos()).extracting(Vehiculo::placa).as("sin guiones").containsExactly("ABC123");
        assertThat(g.traslado().unidadPeso()).isEqualTo("KGM");
        g.asignarNumero(7, EMISOR);
        assertThat(g.nombreArchivo()).isEqualTo("20100066603-09-T001-7");
        g.firmar("HASH", "k.xml");
        assertThat(g.estado()).isEqualTo(EstadoGuia.FIRMADO);
    }

    @Test void elTipoSaleDeLosDatosYLaSerieDebeCorresponder() {
        rechaza(() -> GuiaRemision.crear(TENANT, EMISOR, "V001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), null, RELOJ), "SERIE_INVALIDA", "502");
        rechaza(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), deTransportista(), privado(), items(), List.of(), null, RELOJ), "SERIE_INVALIDA", "502");
        rechaza(() -> GuiaRemision.crear(TENANT, EMISOR, "F001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), null, RELOJ), "SERIE_INVALIDA", "502");
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), null, privado(), items(), List.of(), null, RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "GUIA_INVALIDA");
        assertThat(GuiaRemision.crear(TENANT, EMISOR, "t001", null, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), null, RELOJ).serie())
                .as("en mayúsculas; sin fecha, la de hoy").isEqualTo("T001");
    }

    @Test void laFechaDeEmisionNoEsFuturaYElTrasladoNoEmpiezaAntes() {
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY.plusDays(1), cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), null, RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "FECHA_INVALIDA");
        Traslado antes = new Traslado(HOY.minusDays(1), BigDecimal.TEN, "KGM", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), antes), "TRASLADO_INVALIDO", "3343");
        Traslado sinFecha = new Traslado(null, BigDecimal.TEN, "KGM", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), sinFecha), "TRASLADO_INVALIDO", "3406");
        Traslado manana = new Traslado(HOY.plusDays(3), BigDecimal.TEN, "KGM", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), manana).traslado().fechaInicio()).isEqualTo(HOY.plusDays(3));
    }

    @Test void elPesoBrutoEsPositivoEnKilosOToneladasYSinBultos() {
        for (String peso : new String[]{"0", "-1", "1.2345"}) {
            Traslado t = new Traslado(HOY, new BigDecimal(peso), "KGM", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
            rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), t), "TRASLADO_INVALIDO", "2880");
        }
        Traslado sinPeso = new Traslado(HOY, null, "KGM", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), sinPeso), "TRASLADO_INVALIDO", "2880");
        Traslado litros = new Traslado(HOY, BigDecimal.TEN, "LTR", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), litros), "TRASLADO_INVALIDO", "2881");
        Traslado toneladas = new Traslado(HOY, new BigDecimal("1.250"), "tne", null, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), toneladas).traslado().unidadPeso()).isEqualTo("TNE");
        Traslado bultos = new Traslado(HOY, BigDecimal.TEN, "KGM", 3, privado().partida(), privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), bultos), "TRASLADO_INVALIDO", "4195");
    }

    @Test void partidaYLlegadaConUbigeoYDireccion() {
        Traslado sinPartida = new Traslado(HOY, BigDecimal.TEN, "KGM", null, null, privado().llegada(), List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), sinPartida), "TRASLADO_INVALIDO", "1075");
        Traslado sinLlegada = new Traslado(HOY, BigDecimal.TEN, "KGM", null, privado().partida(), null, List.of(camion()), List.of(chofer()), Set.of());
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), sinLlegada), "TRASLADO_INVALIDO", "1074");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(PuntoDeTraslado.de("999999", "Av. X 1"), null)), "TRASLADO_INVALIDO", "3363");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(null, PuntoDeTraslado.de("15010", "Av. X 1"))), "TRASLADO_INVALIDO", "3368");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(PuntoDeTraslado.de("150101", null), null)), "TRASLADO_INVALIDO", "2577");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(null, PuntoDeTraslado.de("150101", " "))), "TRASLADO_INVALIDO", "2574");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(PuntoDeTraslado.de("150101", "Av.\nX"), null)), "TRASLADO_INVALIDO", "4184");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(null, PuntoDeTraslado.de("150101", "x".repeat(501)))), "TRASLADO_INVALIDO", "4179");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(new PuntoDeTraslado("150101", "Av. X 1", "12", null), null)), "TRASLADO_INVALIDO", "3365");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(null, new PuntoDeTraslado("150101", "Av. X 1", "0001", "20100066600"))), "TRASLADO_INVALIDO", "3410");
    }

    @Test void elEstablecimientoSinRucEsDelRemitente() {
        GuiaRemision g = remitente(venta(ModalidadTraslado.PRIVADO, null), conPuntos(new PuntoDeTraslado("150101", "Av. Argentina 1234", "0001", null), null));
        assertThat(g.traslado().partida().rucEstablecimiento()).isEqualTo(EMISOR);
        assertThat(g.traslado().llegada().rucEstablecimiento()).as("sin código no lleva RUC").isNull();
        GuiaRemision t = transportista(deTransportista(), conPuntos(new PuntoDeTraslado("150101", "Av. Argentina 1234", "0002", null), null));
        assertThat(t.traslado().partida().rucEstablecimiento()).as("en la transportista, del remitente").isEqualTo(OTRO_REMITENTE);
    }

    static Traslado conPuntos(PuntoDeTraslado partida, PuntoDeTraslado llegada) {
        Traslado b = privado();
        return new Traslado(HOY, BigDecimal.TEN, "KGM", null, partida == null ? b.partida() : partida, llegada == null ? b.llegada() : llegada, b.vehiculos(), b.conductores(), Set.of());
    }

    @Test void elMotivoEsDelCatalogo20YKhipuEmiteLosQueNoPidenAduana() {
        rechaza(() -> remitente(new DatosDeGuia.DeRemitente(null, null, ModalidadTraslado.PRIVADO, null), privado()), "TRASLADO_INVALIDO", "3404");
        rechaza(() -> remitente(new DatosDeGuia.DeRemitente("99", null, ModalidadTraslado.PRIVADO, null), privado()), "TRASLADO_INVALIDO", "3405");
        for (String m : new String[]{"08", "09", "18"})
            assertThatThrownBy(() -> remitente(new DatosDeGuia.DeRemitente(m, null, ModalidadTraslado.PRIVADO, null), privado()))
                    .hasFieldOrPropertyWithValue("codigo", "TRASLADO_INVALIDO").hasMessageContaining("todavía no emite");
        rechaza(() -> remitente(new DatosDeGuia.DeRemitente("13", null, ModalidadTraslado.PRIVADO, null), privado()), "TRASLADO_INVALIDO", "3457");
        assertThat(remitente(new DatosDeGuia.DeRemitente("13", "Traslado a feria", ModalidadTraslado.PRIVADO, null), privado()).remitente().orElseThrow().descripcionMotivo()).isEqualTo("Traslado a feria");
        rechaza(() -> remitente(new DatosDeGuia.DeRemitente("13", "x".repeat(101), ModalidadTraslado.PRIVADO, null), privado()), "TRASLADO_INVALIDO", "4190");
        assertThatThrownBy(() -> remitente(new DatosDeGuia.DeRemitente("01", null, null, null), privado())).hasFieldOrPropertyWithValue("codigo", "TRASLADO_INVALIDO").hasMessageContaining("modalidad");
    }

    @Test void elDestinatarioSegunElMotivo() {
        ParteDeGuia elMismo = new ParteDeGuia("6", EMISOR, "EMPRESA DE PRUEBA S.A.C.");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), elMismo, List.of()), "DESTINATARIO_INVALIDO", "2555");
        for (String m : new String[]{"03", "05", "14"})
            rechaza(() -> remitente(new DatosDeGuia.DeRemitente(m, null, ModalidadTraslado.PRIVADO, null), privado(), elMismo, List.of()), "DESTINATARIO_INVALIDO", "2555");
        assertThat(remitente(new DatosDeGuia.DeRemitente("02", null, ModalidadTraslado.PRIVADO, null), privado(), elMismo, List.of())).as("una compra puede llegar al propio remitente").isNotNull();
        ParteDeGuia conDni = new ParteDeGuia("1", "45678912", "MARÍA LÓPEZ");
        for (String m : new String[]{"06", "17"})
            rechaza(() -> remitente(new DatosDeGuia.DeRemitente(m, null, ModalidadTraslado.PRIVADO, null), privado(), conDni, List.of()), "DESTINATARIO_INVALIDO", "3417");
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), conDni, List.of()).destinatario().numDoc()).isEqualTo("45678912");
    }

    @Test void elDestinatarioSeIdentificaConElCatalogo06() {
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), new ParteDeGuia("Z", "1", "NOMBRE"), List.of()), "DESTINATARIO_INVALIDO", "El tipo");
        assertThatThrownBy(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), new ParteDeGuia("6", "20601234560", "COMERCIAL"), List.of()))
                .hasFieldOrPropertyWithValue("codigo", "DESTINATARIO_INVALIDO").hasMessageContaining("dígito verificador");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), new ParteDeGuia("1", "1234567", "JUAN PEREZ"), List.of()), "DESTINATARIO_INVALIDO", "El DNI");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), new ParteDeGuia("6", CLIENTE, "AB"), List.of()), "DESTINATARIO_INVALIDO", "El nombre");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), new ParteDeGuia("-", "-", "CLIENTES VARIOS"), List.of()), "DESTINATARIO_INVALIDO", "El tipo");
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, null, venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), null, RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "DESTINATARIO_INVALIDO");
    }

    @Test void elTrasladoEntreEstablecimientosVaDeUnAnexoAOtroDelRemitente() {
        ParteDeGuia elMismo = new ParteDeGuia("6", EMISOR, "EMPRESA DE PRUEBA S.A.C.");
        DatosDeGuia.DeRemitente entre = new DatosDeGuia.DeRemitente("04", null, ModalidadTraslado.PRIVADO, null);
        PuntoDeTraslado almacen = new PuntoDeTraslado("150101", "Av. Argentina 1234", "0001", null);
        PuntoDeTraslado tienda = new PuntoDeTraslado("040101", "Calle Mercaderes 200", "0002", null);
        rechaza(() -> remitente(entre, conPuntos(almacen, tienda), cliente(), List.of()), "DESTINATARIO_INVALIDO", "2554");
        rechaza(() -> remitente(entre, conPuntos(PuntoDeTraslado.de("150101", "Av. X 1"), tienda), elMismo, List.of()), "TRASLADO_INVALIDO", "3365");
        rechaza(() -> remitente(entre, conPuntos(almacen, PuntoDeTraslado.de("150101", "Av. X 1")), elMismo, List.of()), "TRASLADO_INVALIDO", "3369");
        rechaza(() -> remitente(entre, conPuntos(almacen, new PuntoDeTraslado("040101", "Calle Mercaderes 200", "0002", CLIENTE)), elMismo, List.of()), "TRASLADO_INVALIDO", "3414");
        assertThatThrownBy(() -> remitente(entre, conPuntos(almacen, new PuntoDeTraslado("040101", "Otra", "0001", null)), elMismo, List.of())).hasMessageContaining("distintos");
        GuiaRemision g = remitente(entre, conPuntos(almacen, tienda), elMismo, List.of());
        assertThat(g.traslado().llegada().rucEstablecimiento()).isEqualTo(EMISOR);
    }

    @Test void enTransportePublicoVaLaEmpresaDeTransporteYNoSusVehiculos() {
        Traslado sinVehiculos = traslado(List.of(), List.of(), Set.of());
        GuiaRemision g = remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), sinVehiculos);
        assertThat(g.remitente().orElseThrow().transportista().ruc()).isEqualTo(EMPRESA_DE_TRANSPORTE);
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, null), sinVehiculos), "TRANSPORTE_INVALIDO", "2534");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, new TransportistaContratado(null, "TRANSPORTES", null)), sinVehiculos), "TRANSPORTE_INVALIDO", "4160");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, new TransportistaContratado("20480072870", "TRANSPORTES", null)), sinVehiculos), "TRANSPORTE_INVALIDO", "4163");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, new TransportistaContratado(EMPRESA_DE_TRANSPORTE, "T", null)), sinVehiculos), "TRANSPORTE_INVALIDO", "4164");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, new TransportistaContratado(EMPRESA_DE_TRANSPORTE, "TRANSPORTES", "X".repeat(21))), sinVehiculos), "TRANSPORTE_INVALIDO", "4392");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, new TransportistaContratado(EMISOR, "EMPRESA DE PRUEBA", null)), sinVehiculos), "TRANSPORTE_INVALIDO", "2560");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), privado()), "TRANSPORTE_INVALIDO", "2774");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), traslado(List.of(), List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "2774");
    }

    @Test void conElIndicadorDeVehiculosDelTransportistaElRemitenteLosRegistra() {
        Set<IndicadorDeTraslado> vct = Set.of(IndicadorDeTraslado.VEHICULOS_CONDUCTORES_TRANSPORTISTA);
        assertThat(remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), traslado(List.of(camion()), List.of(chofer()), vct)).traslado().conVehiculos()).isTrue();
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), traslado(List.of(), List.of(chofer()), vct)), "TRANSPORTE_INVALIDO", "2566");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), traslado(List.of(camion()), List.of(), vct)), "TRANSPORTE_INVALIDO", "3357");
        rechaza(() -> remitente(venta(ModalidadTraslado.PUBLICO, empresaDeTransporte()), traslado(List.of(camion()), List.of(chofer()),
                Set.of(IndicadorDeTraslado.VEHICULOS_CONDUCTORES_TRANSPORTISTA, IndicadorDeTraslado.VEHICULO_M1_O_L))), "TRASLADO_INVALIDO", "3451");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(chofer()), vct)), "TRASLADO_INVALIDO", "3450");
    }

    @Test void enTransportePrivadoVanVehiculoYConductorSalvoM1oL() {
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, empresaDeTransporte()), privado()), "TRANSPORTE_INVALIDO", "3347");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(), List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "2533");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(), Set.of())), "TRANSPORTE_INVALIDO", "2533");
        GuiaRemision m1 = remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(), List.of(), Set.of(IndicadorDeTraslado.VEHICULO_M1_O_L)));
        assertThat(m1.traslado().tiene(IndicadorDeTraslado.VEHICULO_M1_O_L)).isTrue();
    }

    @Test void vehiculosYConductoresConSuFormatoYSuTope() {
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(new Vehiculo("AB-12")), List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "2567");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(new Vehiculo(" ")), List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "2566");
        List<Vehiculo> cuatro = List.of(new Vehiculo("AAA111"), new Vehiculo("AAA112"), new Vehiculo("AAA113"), new Vehiculo("AAA114"));
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(cuatro, List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "4389");
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(cuatro.subList(0, 3), List.of(chofer()), Set.of())).traslado().vehiculos()).hasSize(3);
        assertThatThrownBy(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(new Vehiculo("AAA111"), new Vehiculo("aaa-111")), List.of(chofer()), Set.of())))
                .hasMessageContaining("misma placa");

        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("6", CLIENTE, "A", "B", "Q45678912")), Set.of())), "TRANSPORTE_INVALIDO", "2571");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("1", "4567891", "A", "B", "Q45678912")), Set.of())), "TRANSPORTE_INVALIDO", "2569");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("1", "45678912", null, "B", "Q45678912")), Set.of())), "TRANSPORTE_INVALIDO", "3360");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("1", "45678912", "A", " ", "Q45678912")), Set.of())), "TRANSPORTE_INVALIDO", "3361");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("1", "45678912", "A", "B", null)), Set.of())), "TRANSPORTE_INVALIDO", "2572");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(new Conductor("1", "45678912", "A", "B", "Q123")), Set.of())), "TRANSPORTE_INVALIDO", "2573");
        Conductor otro = new Conductor("4", "CE001234", "ANA", "RUIZ", "B12345678");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(chofer(), otro, new Conductor("7", "PA12345", "X", "Y", "C12345678"), new Conductor("1", "11111111", "X", "Y", "C12345679")), Set.of())),
                "TRANSPORTE_INVALIDO", "4376");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(chofer(), new Conductor("1", "45678912", "OTRO", "NOMBRE", "Q99999999")), Set.of())),
                "TRANSPORTE_INVALIDO", "3362");
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), traslado(List.of(camion()), List.of(chofer(), otro), Set.of())).traslado().conductores().get(1).licencia()).isEqualTo("B12345678");
    }

    @Test void losItemsLlevanDescripcionUnidadDelCatalogo03YCantidad() {
        for (ItemDeGuia malo : List.of(new ItemDeGuia(null, " ", "NIU", BigDecimal.ONE, null), new ItemDeGuia(null, "Caja", "XYZ", BigDecimal.ONE, null),
                new ItemDeGuia(null, "Caja", "NIU", BigDecimal.ZERO, null), new ItemDeGuia(null, "Caja", "NIU", new BigDecimal("0.00000000001"), null),
                new ItemDeGuia("x".repeat(31), "Caja", "NIU", BigDecimal.ONE, null), new ItemDeGuia(null, "Caja", "NIU", BigDecimal.ONE, "1234567")))
            assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), List.of(malo), List.of(), null, RELOJ))
                    .hasFieldOrPropertyWithValue("codigo", "ITEM_INVALIDO").hasMessageStartingWith("El ítem 1");
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), List.of(), List.of(), null, RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "ITEM_INVALIDO");
        GuiaRemision g = GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(),
                List.of(new ItemDeGuia(null, "Café pergamino", "kgm", new BigDecimal("12.5"), "50201706")), List.of(), null, RELOJ);
        assertThat(g.items().get(0).unidad()).isEqualTo("KGM");
    }

    @Test void losDocumentosRelacionadosSonDelCatalogo61YDeSuGuia() {
        DocumentoDeTraslado factura = new DocumentoDeTraslado("01", "F001-123", EMISOR);
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(factura)).documentos()).containsExactly(factura);
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(new DocumentoDeTraslado("31", "V001-1", EMPRESA_DE_TRANSPORTE))), "DOCUMENTO_RELACIONADO_INVALIDO", "3445");
        rechaza(() -> transportista(deTransportista(), privado(), List.of(new DocumentoDeTraslado("49", "123456", null))), "DOCUMENTO_RELACIONADO_INVALIDO", "3445");
        assertThat(transportista(deTransportista(), privado(), List.of(new DocumentoDeTraslado("09", "T001-45", OTRO_REMITENTE))).documentos()).hasSize(1);
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(new DocumentoDeTraslado("01", "F001123", EMISOR))), "DOCUMENTO_RELACIONADO_INVALIDO", "3441");
        assertThatThrownBy(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(new DocumentoDeTraslado("01", "F001-123", null))))
                .hasFieldOrPropertyWithValue("codigo", "DOCUMENTO_RELACIONADO_INVALIDO").hasMessageContaining("RUC");
        assertThatThrownBy(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(new DocumentoDeTraslado("99", "X", null))))
                .hasFieldOrPropertyWithValue("codigo", "DOCUMENTO_RELACIONADO_INVALIDO").hasMessageContaining("catálogo 61");
        rechaza(() -> remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(factura, new DocumentoDeTraslado("01", "f001-123", EMISOR))), "DOCUMENTO_RELACIONADO_INVALIDO", "3340");
        assertThat(remitente(venta(ModalidadTraslado.PRIVADO, null), privado(), cliente(), List.of(new DocumentoDeTraslado("50", "118-2026-10-123456", null))).documentos()).hasSize(1);
    }

    static GuiaRemision transportista(DatosDeGuia.DeTransportista d, Traslado t, List<DocumentoDeTraslado> docs) {
        return GuiaRemision.crear(TENANT, EMISOR, "V001", HOY, cliente(), d, t, items(), docs, null, RELOJ);
    }

    @Test void unaGuiaTransportistaLlevaAlRemitenteSuMtcSuVehiculoYSuConductor() {
        GuiaRemision g = transportista(deTransportista(), privado());
        assertThat(g.tipo()).isEqualTo(TipoDocumento.GUIA_TRANSPORTISTA);
        assertThat(g.remitente()).isEmpty();
        assertThat(g.transportista().orElseThrow().registroMtc()).isEqualTo("1512345CNG");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(null, "1512345CNG"), privado()), "REMITENTE_INVALIDO", "3383");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(new ParteDeGuia("Z", "1", "NOMBRE"), "1512345CNG"), privado()), "REMITENTE_INVALIDO", "3383");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(new ParteDeGuia("1", "123", "JUAN PEREZ"), "1512345CNG"), privado()), "REMITENTE_INVALIDO", "3384");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(new ParteDeGuia("6", OTRO_REMITENTE, "X"), "1512345CNG"), privado()), "REMITENTE_INVALIDO", "3387");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(new ParteDeGuia("6", EMISOR, "EMPRESA DE PRUEBA"), "1512345CNG"), privado()), "REMITENTE_INVALIDO", "2560");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(deTransportista().remitente(), null), privado()), "TRANSPORTE_INVALIDO", "4391");
        rechaza(() -> transportista(new DatosDeGuia.DeTransportista(deTransportista().remitente(), "MTC/123"), privado()), "TRANSPORTE_INVALIDO", "4392");
        rechaza(() -> transportista(deTransportista(), traslado(List.of(), List.of(chofer()), Set.of())), "TRANSPORTE_INVALIDO", "2566");
        rechaza(() -> transportista(deTransportista(), traslado(List.of(camion()), List.of(), Set.of())), "TRANSPORTE_INVALIDO", "3357");
    }

    @ParameterizedTest @ValueSource(strings = {"VEHICULO_M1_O_L", "VEHICULOS_CONDUCTORES_TRANSPORTISTA"})
    void laTransportistaNoLlevaLosIndicadoresDeLaModalidad(String indicador) {
        assertThatThrownBy(() -> transportista(deTransportista(), traslado(List.of(camion()), List.of(chofer()), Set.of(IndicadorDeTraslado.valueOf(indicador)))))
                .hasFieldOrPropertyWithValue("codigo", "TRASLADO_INVALIDO").hasMessageContaining("transportista");
        assertThat(transportista(deTransportista(), traslado(List.of(camion()), List.of(chofer()), Set.of(IndicadorDeTraslado.TRANSBORDO_PROGRAMADO))).traslado().indicadores())
                .containsExactly(IndicadorDeTraslado.TRANSBORDO_PROGRAMADO);
    }

    @Test void lasObservacionesVanEnUnaLinea() {
        assertThat(GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), "  Frágil  ", RELOJ).observaciones()).isEqualTo("Frágil");
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), items(), List.of(), "a\nb", RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "OBSERVACIONES_INVALIDAS");
    }

    static GuiaRemision firmada() {
        GuiaRemision g = remitente(venta(ModalidadTraslado.PRIVADO, null), privado());
        g.asignarNumero(1, EMISOR);
        g.firmar("HASH", "k.xml");
        return g;
    }

    @Test void elEnvioDaUnTicketQueSeConsultaHastaElCdr() {
        GuiaRemision g = firmada();
        g.marcarEnviada("7f7b00ab-4fee-4ae2-9e04-fbd16ca18677", LocalDateTime.of(2026, 10, 20, 10, 0, 1));
        assertThat(g.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(g.reintentoAutomatico()).isTrue();
        assertThat(g.intentos()).isEqualTo(1);
        g.ticketEnProceso();
        g.ticketEnProceso();
        assertThat(g.consultasTicket()).isEqualTo(2);
        g.aplicarCdr(new Cdr("0", "La Guia numero T001-1, ha sido aceptada", List.of()), "r.zip", "https://e-factura.sunat.gob.pe/x");
        assertThat(g.estado()).isEqualTo(EstadoGuia.ACEPTADO);
        assertThat(g.reintentoAutomatico()).isFalse();
        assertThat(g.enlaceConsulta()).isEqualTo("https://e-factura.sunat.gob.pe/x");
        assertThatThrownBy(g::ticketEnProceso).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
        assertThatThrownBy(() -> g.marcarErrorEnvio("x", true)).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
    }

    @Test void elCdrDecideEntreAceptadaObservadaYRechazada() {
        GuiaRemision obs = firmada(); obs.marcarEnviada("t", null);
        obs.aplicarCdr(new Cdr("0", "aceptada", List.of("4000 - observación")), "r.zip", null);
        assertThat(obs.estado()).isEqualTo(EstadoGuia.ACEPTADO_CON_OBS);
        GuiaRemision rech = firmada(); rech.marcarEnviada("t", null);
        rech.aplicarCdr(new Cdr("2560", "Transportista no debe ser igual al remitente", List.of()), "r.zip", null);
        assertThat(rech.estado()).isEqualTo(EstadoGuia.RECHAZADO);
    }

    @Test void unTicketConErrorSinCdrRechazaSoloSiElCodigoEsDeRechazo() {
        GuiaRemision rechazo = firmada(); rechazo.marcarEnviada("t", null);
        rechazo.errorDelTicket("2555", "Destinatario no debe ser igual al remitente");
        assertThat(rechazo.estado()).isEqualTo(EstadoGuia.RECHAZADO);
        assertThat(rechazo.cdr().codigo()).isEqualTo("2555");
        GuiaRemision excepcion = firmada(); excepcion.marcarEnviada("t", null);
        excepcion.errorDelTicket("1033", "El comprobante fue registrado previamente con otros datos");
        assertThat(excepcion.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(excepcion.reintentoAutomatico()).isFalse();
        assertThat(excepcion.ultimoError()).contains("1033");
        GuiaRemision sinCodigo = firmada(); sinCodigo.marcarEnviada("t", null);
        sinCodigo.errorDelTicket(null, "error raro");
        assertThat(sinCodigo.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
    }

    /** H2: el envío se reserva (ENVIANDO) antes de llamar a SUNAT; desde ahí solo sale con la respuesta o el error. Una guía resuelta no se reserva. */
    @Test void elEnvioSeReservaAntesDeLlamarASunat() {
        GuiaRemision g = firmada();
        g.iniciarEnvio();
        assertThat(g.estado()).isEqualTo(EstadoGuia.ENVIANDO);
        assertThat(g.estado().esEnviable()).as("no se reenvía sola").isFalse();
        g.marcarErrorEnvio("0109 - tiempo agotado", true);
        g.iniciarEnvio();
        g.marcarEnviada("t", null);
        assertThat(g.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThatThrownBy(g::iniciarEnvio).hasFieldOrPropertyWithValue("codigo", "ESTADO_NO_ENVIABLE");
    }

    /** H4: al agotar los intentos la guía queda en error, sin reintento automático y diciendo por qué. */
    @Test void agotarLosReintentosApagaElReintentoAutomatico() {
        GuiaRemision g = firmada();
        g.marcarErrorEnvio("0000 - caída", true);
        g.agotarReintentos("agotó 20 intentos");
        assertThat(g.reintentoAutomatico()).isFalse();
        assertThat(g.ultimoError()).isEqualTo("agotó 20 intentos");
        GuiaRemision otra = firmada();
        assertThatThrownBy(() -> otra.agotarReintentos("x")).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
    }

    /** H7: 1032, 1033, 2223 y 4000 dicen que el número ya está en SUNAT: no es un rechazo que se corrige, ni algo que se reenvía. */
    @ParameterizedTest @ValueSource(strings = {"1032", "1033", "2223", "4000"})
    void unaGuiaQueYaEstaEnSunatNoInvitaAReenviar(String codigo) {
        GuiaRemision g = firmada(); g.marcarEnviada("t", null);
        g.errorDelTicket(codigo, "ya fue presentado");
        assertThat(g.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(g.reintentoAutomatico()).isFalse();
        assertThat(g.ultimoError()).contains("ya está registrada en SUNAT").contains("revísela en SOL").doesNotContain("reenvíela");
        assertThat(GuiaRemision.yaEnSunat(codigo)).isTrue();
    }

    @Test void unErrorDeEnvioDiceSiKhipuReintentaSolo() {
        GuiaRemision g = firmada();
        g.marcarErrorEnvio("0000 - SUNAT no responde", true);
        assertThat(g.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(g.reintentoAutomatico()).isTrue();
        g.marcarErrorEnvio("401 - credenciales", false);
        assertThat(g.intentos()).isEqualTo(2);
        assertThat(g.reintentoAutomatico()).isFalse();
        g.marcarEnviada("t2", null);
        assertThat(g.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        g.ticketSinRespuesta("se dejó de consultar", false);
        assertThat(g.reintentoAutomatico()).isFalse();
        g.reanudarConsultas();
        assertThat(g.reintentoAutomatico()).isTrue();
        assertThat(g.consultasTicket()).isZero();
    }

    @Test void noSeNumeraDosVecesNiSeFirmaSinNumero() {
        GuiaRemision g = remitente(venta(ModalidadTraslado.PRIVADO, null), privado());
        assertThatThrownBy(() -> g.firmar("H", "k")).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
        g.asignarNumero(1, EMISOR);
        assertThatThrownBy(() -> g.asignarNumero(2, EMISOR)).hasFieldOrPropertyWithValue("codigo", "NUMERO_YA_ASIGNADO");
        assertThatThrownBy(() -> g.marcarEnviada("t", null)).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
    }

    @Test void hastaNueveMilNovecientosNoventaYNueveBienes() {
        List<ItemDeGuia> muchos = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) muchos.add(new ItemDeGuia(null, "Caja " + i, "BX", BigDecimal.ONE, null));
        assertThatThrownBy(() -> GuiaRemision.crear(TENANT, EMISOR, "T001", HOY, cliente(), venta(ModalidadTraslado.PRIVADO, null), privado(), muchos, List.of(), null, RELOJ))
                .hasFieldOrPropertyWithValue("codigo", "ITEM_INVALIDO");
    }
}
