package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boleta de venta (03, #20): se envía sola con sendBill como una factura. Lo que cambia es el receptor (Boleta2_0: DNI, otros documentos o, hasta S/ 700, sin documento)
 * y que crédito, retención, anticipos y exportación todavía no se emiten en boleta.
 */
class BoletaTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.of(2026, 9, 13);
    private final Receptor dni = new Receptor("1", "12345678", "JUAN PEREZ", null);
    private final Receptor sinDocumento = new Receptor("-", "-", "CLIENTES VARIOS", null);

    private static List<Item> items(String precio) { return List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal(precio), TipoAfectacionIgv.GRAVADO)); }

    private Comprobante.FacturaBuilder boleta(Receptor r, String precio) { return Comprobante.boleta(tenant, "B001", hoy, "PEN", "0101", r, items(precio)); }

    @Test void unaBoletaConDniNaceRecibidaYEsTipo03() {
        Comprobante c = boleta(dni, "118.00").crear(clock);
        assertThat(c.tipo()).isEqualTo(TipoDocumento.BOLETA);
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECIBIDO);
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        c.asignarNumero(7, "20100066603");
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-03-B001-7");
    }

    @Test void laSerieDeUnaBoletaEmpiezaConB() {
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "F001", hoy, "PEN", "0101", dni, items("10")).crear(clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void sinDocumentoSoloHastaSetecientosSoles() {
        assertThat(boleta(sinDocumento, "700.00").crear(clock).receptor().sinDocumento()).isTrue();
        assertThatThrownBy(() -> boleta(sinDocumento, "700.01").crear(clock))
                .isInstanceOf(DomainException.class).hasMessageContaining("700").extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        // En otra moneda no hay cómo comparar con S/ 700 sin tipo de cambio: se identifica al comprador.
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "USD", "0101", sinDocumento, items("10")).crear(clock))
                .extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1234567, JUAN PEREZ, 4207",
            "1, 1234567A, JUAN PEREZ, 4207",
            "6, 20601234560, CLIENTE SAC, 2017",
            "4, 'CE 123', JUAN PEREZ, 4208",
            "7, 1234567890123456, JUAN PEREZ, 4208",
            // Sin código SUNAT (274-H3): Boleta2_0 no tiene regla para estos dos; 2802 y 2016 son de otras hojas.
            "-, 12345678, JUAN PEREZ, Sin documento",
            "X, 12345678, JUAN PEREZ, El tipo de documento del comprador",
            "1, 12345678, JP, 2022",
            // #303: los espacios de los extremos no cuentan (más estricto que contarlos), y sin documento rige igual.
            "1, 12345678, '  JP  ', 2022",
            "-, -, AB, 2022",
    })
    void elReceptorSigueLasReglasDeBoleta(String tipo, String numero, String nombre, String regla) {
        assertThatThrownBy(() -> boleta(new Receptor(tipo, numero, nombre, null), "10").crear(clock))
                .isInstanceOf(DomainException.class).hasMessageStartingWith(regla).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    /**
     * #303: la regla 2022 (nombre del adquirente de 3 caracteres o más) pasa de observación a error en Boleta2_0 y en el resumen diario el
     * 2027-01-01; khipu ya la exige. El mensaje habla del comprador, que es como el portal y la boleta lo llaman.
     */
    @Test void elNombreDelCompradorTieneAlMenosTresCaracteresSinContarLosBordes() {
        assertThatThrownBy(() -> boleta(new Receptor("1", "12345678", "JP", null), "10").crear(clock))
                .hasMessage("2022 - El nombre del comprador tiene de 3 a 1500 caracteres, sin saltos de línea");
        assertThat(boleta(new Receptor("1", "12345678", " ANA ", null), "10").crear(clock).receptor().razonSocial()).isEqualTo(" ANA ");
    }

    /**
     * #347-H1: los extremos se cuentan como los cuenta el portal (`trim` y `\s` de JS): `String.strip()` no tiene por espacio el de no separación
     * (U+00A0, U+2007, U+202F) ni el BOM, así que «JP» pegado con un U+00A0 —o tres U+00A0, un nombre en blanco a la vista— pasaba la 2022.
     */
    @Test void losEspaciosDeNoSeparacionDeLosExtremosTampocoCuentan() {
        for (String corto : List.of("JP ", "   ", " JP ", "﻿JP", "　JP　"))
            assertThatThrownBy(() -> boleta(new Receptor("1", "12345678", corto, null), "10").crear(clock)).as(corto).hasMessageStartingWith("2022");
        assertThat(boleta(new Receptor("1", "12345678", " A B ", null), "10").crear(clock).receptor().razonSocial()).isEqualTo(" A B ");
    }

    /** #347-H3: el tope de la 2022 (1500), contado también sin los extremos. */
    @Test void elNombreDelCompradorTieneHastaMilQuinientosCaracteres() {
        assertThat(boleta(new Receptor("1", "12345678", "Ñ".repeat(1500), null), "10").crear(clock).receptor().razonSocial()).hasSize(1500);
        assertThat(boleta(new Receptor("1", "12345678", " " + "Ñ".repeat(1500) + " ", null), "10").crear(clock).receptor().razonSocial()).hasSize(1502);
        assertThatThrownBy(() -> boleta(new Receptor("1", "12345678", "Ñ".repeat(1501), null), "10").crear(clock)).hasMessageStartingWith("2022");
    }

    /** #303: el nombre convenido para el comprador sin documento cumple la 2022, y es el que propone el portal. */
    @Test void elNombreDelCompradorSinDocumentoCumpleLaRegla2022() {
        assertThat(Receptor.NOMBRE_SIN_DOCUMENTO).isEqualTo("CLIENTES VARIOS");
        assertThat(boleta(new Receptor(Receptor.SIN_DOCUMENTO, Receptor.SIN_DOCUMENTO, Receptor.NOMBRE_SIN_DOCUMENTO, null), "10").crear(clock).receptor().sinDocumento()).isTrue();
    }

    /** #303: la 2022 se exige al emitir, no al leer: una boleta ya guardada con un nombre corto se sigue rehidratando y entra al resumen diario. */
    @Test void unaBoletaGuardadaConNombreCortoSeSigueLeyendo() {
        Comprobante guardada = Comprobante.persistido(UUID.randomUUID(), tenant, TipoDocumento.BOLETA, "B001", 1L, hoy, EstadoDocumento.ACEPTADO,
                new Receptor("1", "12345678", "JP", null), items("10")).firma("h", "n", "k").rehidratar();
        assertThat(guardada.receptor().razonSocial()).isEqualTo("JP");
        assertThat(ComunicacionBaja.crear(guardada, 1, "Error en el monto", clock).resumenDiario()).isTrue();
    }

    @Test void admiteRucCarnetPasaporteYOtrosDelCatalogo06() {
        for (Receptor r : List.of(new Receptor("6", "20601234565", "CLIENTE SAC", null), new Receptor("4", "001234567", "ANA DIAZ", null),
                new Receptor("7", "AB1234567", "JOHN DOE", null), new Receptor("0", "X123", "JOHN DOE", null)))
            assertThat(boleta(r, "10").crear(clock).receptor()).isEqualTo(r);
    }

    @Test void sinReceptorEsUnError() {
        assertThatThrownBy(() -> boleta(null, "10").crear(clock)).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    /** Catálogo 51: 0113 (NRUS) es solo de boletas; 0112 (venta interna sustenta gastos deducibles PN) es solo de facturas. */
    @Test void elTipoDeOperacionDebeAplicarABoletas() {
        assertThat(Comprobante.boleta(tenant, "B001", hoy, "PEN", "0113", dni, items("10")).crear(clock).tipoOperacion()).isEqualTo("0113");
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "PEN", "0112", dni, items("10")).crear(clock))
                .hasMessageStartingWith("3206").hasMessageContaining("no aplica a boletas");
    }

    @Test void loQueTodaviaNoSeEmiteEnBoletaSeRechazaConClaridad() {
        assertThatThrownBy(() -> boleta(dni, "100").formaPago(FormaPago.credito(new BigDecimal("100"), List.of(new FormaPago.Cuota(new BigDecimal("100"), hoy.plusDays(30))))).crear(clock))
                .extracting("codigo").isEqualTo("FORMA_PAGO_INVALIDA");
        assertThatThrownBy(() -> boleta(dni, "1000").retencion(new RetencionIgv(null, null)).crear(clock))
                .extracting("codigo").isEqualTo("RETENCION_INVALIDA");
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "USD", "0200", new Receptor("7", "AB123", "JOHN DOE", null, "US"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.EXPORTACION))).crear(clock))
                .extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
    }

    /**
     * 274-H2: solo los tipos de operación cuyos datos khipu genera en una boleta. Otros del catálogo 51 que dicen «Boleta» (0401 a no domiciliados, 0302
     * con medio de pago…) pasaban, consumían número y SUNAT los rechazaba.
     */
    @Test void soloLosTiposDeOperacionQueKhipuArmaEnUnaBoleta() {
        for (String op : new String[]{"0401", "0302"})
            assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "PEN", op, dni, items("10")).crear(clock)).as(op)
                    .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
        assertThat(Comprobante.boleta(tenant, "B001", hoy, "PEN", "0101", dni, items("10")).crear(clock).tipoOperacion()).isEqualTo("0101");
    }

    /**
     * En boletas el rechazo por plazo es 1079, «Solo puede enviar el comprobante en un resumen diario» (Boleta2_0): pasado el envío individual, SUNAT la
     * recibe en el resumen. El mensaje lo dice así, no como un 2108 («fuera de fecha, emita otro»), que llevaría a cobrar la misma venta dos veces (274-H1).
     */
    @Test void fueraDePlazoCitaLaReglaDeBoletaYNoPideEmitirOtra() {
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy.minusDays(8), "PEN", "0101", dni, items("10")).crear(clock))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("1079").hasMessageContaining("resumen diario").extracting("codigo").isEqualTo("FECHA_INVALIDA");
        Comprobante c = Comprobante.boleta(tenant, "B001", hoy, "PEN", "0101", dni, items("10")).crear(clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        // #331: una boleta firmada nunca queda FUERA_DE_PLAZO: pasado el 7.º día se informa igual en el resumen diario (4443, observación).
        assertThat(c.fueraDePlazo(hoy.plusDays(30))).isFalse();
        assertThatThrownBy(() -> c.marcarFueraDePlazo(hoy.plusDays(8))).extracting("codigo").isEqualTo("TRANSICION_INVALIDA");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
    }

    /**
     * 274-H1, segunda parte: pasado el envío individual (5 días, #290) SUNAT todavía recibe la boleta, pero solo en un resumen diario, hasta el séptimo día
     * (guía del resumen diario). En esa ventana no está fuera de plazo: se informa en un resumen de alta.
     */
    @Test void entreElSextoYElSeptimoDiaSoloVaEnUnResumenDiario() {
        Comprobante c = Comprobante.boleta(tenant, "B001", hoy.minusDays(6), "PEN", "0101", dni, items("10")).crear(clock);
        assertThat(c.soloPorResumen(hoy.minusDays(1))).as("5.º día: dentro del envío individual").isFalse();
        assertThat(c.soloPorResumen(hoy)).isTrue();
        assertThat(c.fueraDePlazo(hoy)).isFalse();
        assertThat(c.informaFueraDePlazo(hoy.plusDays(1))).as("7.º día: en plazo").isFalse();
        // #331: pasado el séptimo día sigue yendo en el resumen, fuera de plazo (4443), no queda sin canal.
        assertThat(c.fueraDePlazo(hoy.plusDays(2))).as("pasado el séptimo día").isFalse();
        assertThat(c.soloPorResumen(hoy.plusDays(2))).isTrue();
        assertThat(c.informaFueraDePlazo(hoy.plusDays(2))).isTrue();

        Comprobante factura = Comprobante.factura(tenant, "F001", hoy, "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), items("10")).crear(clock);
        assertThat(factura.soloPorResumen(hoy.plusDays(5))).as("una factura nunca va en un resumen").isFalse();
        assertThat(factura.fueraDePlazo(hoy.plusDays(4))).isTrue();
    }

    /** Informada en el resumen, la boleta queda ENVIADA hasta que SUNAT responda el ticket, y su CDR la acepta o la rechaza como un envío más. */
    @Test void informadaEnElResumenQuedaEnviadaYElCdrLaResuelve() {
        Comprobante c = Comprobante.boleta(tenant, "B001", hoy.minusDays(5), "PEN", "0101", dni, items("10")).crear(clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        c.informarEnResumen("RC-20260913-1", false);
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ENVIADO);
        assertThat(c.eventosPendientes().get(c.eventosPendientes().size() - 1).detalle()).doesNotContain("fuera de plazo");
        c.aplicarCdr(new Cdr("0", "El Resumen diario RC-20260913-1 ha sido aceptado", List.of()), "R.zip");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    /** #331: pasado el 7.º día va igual en el resumen. El historial lo dice, y SUNAT la acepta con la observación 4443 en vez de rechazarla. */
    @Test void informadaFueraDePlazoQuedaAceptadaConLaObservacion4443() {
        Comprobante c = Comprobante.boleta(tenant, "B001", hoy.minusDays(7), "PEN", "0101", dni, items("10")).crear(clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        c.informarEnResumen("RC-20260913-1", true);
        assertThat(c.eventosPendientes().get(c.eventosPendientes().size() - 1).detalle()).contains("RC-20260913-1").contains("fuera de plazo").contains("4443");
        c.aplicarCdr(new Cdr("0", "El Resumen diario RC-20260913-1 ha sido aceptado", List.of("4443 - El comprobante fue informado fuera del plazo establecido")), "R.zip");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
    }

    /** Un CDR con ResponseCode 4000+ es una observación aunque no traiga notas (CdrDto: «4000+ aceptado con observaciones»): no se pierde como ACEPTADO a secas. */
    @Test void unCdrConCodigoDeObservacionSinNotasQuedaAceptadoConObservaciones() {
        Comprobante c = Comprobante.boleta(tenant, "B001", hoy.minusDays(7), "PEN", "0101", dni, items("10")).crear(clock);
        c.asignarNumero(1, "20100066603");
        c.firmar("h", "k");
        c.informarEnResumen("RC-20260913-1", true);
        c.aplicarCdr(new Cdr("4443", "El comprobante fue informado fuera del plazo establecido", List.of()), "R.zip");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
    }
}
