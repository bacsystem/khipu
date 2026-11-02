package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
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
 * Guardas de la comunicación de baja (hoja Comunicación de Baja1_0) que la auditoría del flujo encontró sin test: 2308 (boleta),
 * 2315 (motivo hasta 100, sin caracteres de control), frontera del plazo 2957 (día 7 sí, día 8 no), correlativo y la
 * irreversibilidad de la máquina de estados (una baja ACEPTADA o RECHAZADA no vuelve a transitar).
 */
class ComunicacionBajaTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("America/Lima")); // 2026-09-20 en Lima
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE SAC", null);

    static Comprobante aceptado(TipoDocumento tipo, String serie, LocalDate emision) {
        return Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), tipo, serie, 1L, emision, EstadoDocumento.ACEPTADO, RECEPTOR,
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).firma("h", "n", "k").rehidratar();
    }

    @Test void plazoDeSieteDiasCalendario() {
        assertThat(ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 13)), 1, "Error", CLOCK).estado()).isEqualTo(ComunicacionBaja.EstadoBaja.GENERADA);
        assertThatThrownBy(() -> ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 12)), 1, "Error", CLOCK)).hasMessageContaining("2957");
    }

    /**
     * #302: el plazo de 2957 se cuenta desde la fecha de emisión del comprobante, también para la boleta y la nota de boleta. Es la regla de la
     * RS 000048-2026, vigente desde 2027-01-01 (RS 000143-2026); hasta 2026-12-31 la boleta lo cuenta desde su CDR aceptada, que nunca es anterior a
     * la emisión, así que la regla de 2027 es la más estricta y cumple las dos. «Hoy» es la fecha en Lima: el día 7 se admite hasta las 23:59, aunque
     * en UTC ya sea el día 8, y el día 8 empieza a las 00:00 de Lima.
     */
    @Test void elPlazoSeCuentaDesdeLaEmisionEnHoraDeLima() {
        LocalDate emision = LocalDate.of(2026, 9, 13);
        Clock dia7UltimoMinuto = Clock.fixed(Instant.parse("2026-09-21T04:59:59Z"), ZoneId.of("America/Lima")); // 2026-09-20 23:59:59 en Lima, ya el 21 en UTC
        Clock dia8PrimerMinuto = Clock.fixed(Instant.parse("2026-09-21T05:00:00Z"), ZoneId.of("America/Lima")); // 2026-09-21 00:00 en Lima
        Comprobante boleta = aceptado(TipoDocumento.BOLETA, "B001", emision);
        Comprobante notaDeBoleta = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "07", "x"), emision, EstadoDocumento.ACEPTADO);
        Comprobante factura = aceptado(TipoDocumento.FACTURA, "F001", emision);
        for (Comprobante c : List.of(boleta, notaDeBoleta, factura)) {
            assertThat(ComunicacionBaja.crear(c, 1, "Error", dia7UltimoMinuto).fechaGeneracion()).as(c.serie() + ": día 7, 23:59 en Lima").isEqualTo(LocalDate.of(2026, 9, 20));
            assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "Error", dia8PrimerMinuto)).as(c.serie() + ": día 8, 00:00 en Lima")
                    .hasMessageContaining("2957").hasMessageContaining("se emitió el 2026-09-13").hasMessageContaining("7 días calendario siguientes a su emisión");
        }
        // El mismo día de la emisión, a las 00:00 de Lima (05:00 UTC), también vale: el plazo no empieza a contar antes de emitir.
        assertThat(ComunicacionBaja.crear(boleta, 1, "Error", Clock.fixed(Instant.parse("2026-09-13T05:00:00Z"), ZoneId.of("America/Lima"))).fechaGeneracion())
                .isEqualTo(emision);
    }

    /** #20: una boleta no va en una comunicación de baja (2308): se anula en el resumen diario, con su propio identificador RC y las mismas guardas. */
    @Test void unaBoletaSeDaDeBajaEnElResumenDiario() {
        ComunicacionBaja rc = ComunicacionBaja.crear(aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 18)), 3, "Error en el monto", CLOCK);
        assertThat(rc.resumenDiario()).isTrue();
        assertThat(rc.identificador()).isEqualTo("RC-20260920-3");
        assertThat(rc.nombreArchivo("20100066603")).isEqualTo("20100066603-RC-20260920-3");
        assertThat(rc.fechaReferencia()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 18)), 1, "Error", CLOCK).resumenDiario()).isFalse();
        // 2957: el plazo de 7 días también vale para la baja en el RC.
        assertThatThrownBy(() -> ComunicacionBaja.crear(aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 12)), 1, "Error", CLOCK)).hasMessageContaining("2957");
        // 2987/2282: SUNAT tiene que tenerla como válida; una boleta que no aceptó no se anula.
        Comprobante firmada = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 2L, LocalDate.of(2026, 9, 20), EstadoDocumento.FIRMADO, RECEPTOR,
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.crear(firmada, 1, "Error", CLOCK)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("BAJA_INVALIDA");
    }

    /**
     * 274-H1: una boleta que pasó el envío individual (1079) se informa en un resumen diario con su línea en estado 1 (alta), hasta el séptimo día. Es el
     * mismo resumen que la baja (RC, ticket, CDR), con otra condición.
     */
    @Test void unaBoletaPasadaDelEnvioIndividualSeInformaEnUnResumenDeAlta() {
        Comprobante boleta = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 4L, LocalDate.of(2026, 9, 14), EstadoDocumento.ERROR_ENVIO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();

        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(boleta, 2, CLOCK);

        assertThat(alta.condicion()).isEqualTo(ComunicacionBaja.Condicion.ALTA);
        assertThat(alta.condicion().codigo()).isEqualTo("1");
        assertThat(alta.identificador()).isEqualTo("RC-20260920-2");
        assertThat(alta.fechaReferencia()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(ComunicacionBaja.crear(aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 18)), 1, "Error", CLOCK).condicion())
                .isEqualTo(ComunicacionBaja.Condicion.BAJA);
    }

    @Test void soloVaAlResumenDeAltaLaBoletaQueYaNoSePuedeEnviarSola() {
        Comprobante delDia = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 4L, LocalDate.of(2026, 9, 19), EstadoDocumento.FIRMADO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(delDia, 1, CLOCK)).as("todavía va sola con sendBill").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
        Comprobante quintoDia = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 5L, LocalDate.of(2026, 9, 15), EstadoDocumento.FIRMADO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(quintoDia, 1, CLOCK)).as("5.º día: todavía va sola (#290)").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
        Comprobante aceptada = aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 15));
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(aceptada, 1, CLOCK)).as("ya la tiene SUNAT").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
        Comprobante factura = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.FACTURA, "F001", 4L, LocalDate.of(2026, 9, 15), EstadoDocumento.FIRMADO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(factura, 1, CLOCK)).as("una factura no va en un resumen").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
    }

    /**
     * #331: pasado el 7.º día SUNAT no rechaza el alta (4443 es OBSERV, RC fila 14): la boleta, firmada o con el envío fallido, va igual a un resumen de alta,
     * que sabe que sale fuera de plazo. Es un RC propio, con esa sola línea: si SUNAT rechazara el resumen entero no arrastra boletas en plazo (falta validarlo
     * en e-beta). La baja de esa misma boleta, en cambio, sigue rechazándose pasado el 7.º día (2957, RC fila 37).
     */
    @Test void pasadoElSeptimoDiaLaBoletaVaAUnResumenDeAltaFueraDePlazoPeroNoSeDaDeBaja() {
        LocalDate emision = LocalDate.of(2026, 9, 10);   // CLOCK es 2026-09-20: 10.º día
        Comprobante firmada = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 4L, emision, EstadoDocumento.FIRMADO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        Comprobante conError = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "07", "x"), emision, EstadoDocumento.ERROR_ENVIO);
        for (Comprobante c : List.of(firmada, conError)) {
            ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(c, 3, CLOCK);
            assertThat(alta.alta()).isTrue();
            assertThat(alta.identificador()).isEqualTo("RC-20260920-3");
            assertThat(alta.fechaReferencia()).isEqualTo(emision);
            assertThat(alta.fueraDePlazo()).as(c.serie()).isTrue();
            assertThat(alta.motivo()).contains("fuera de plazo").contains("4443");
        }
        Comprobante septimoDia = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 5L, LocalDate.of(2026, 9, 13), EstadoDocumento.FIRMADO,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).rehidratar();
        assertThat(ComunicacionBaja.altaEnResumen(septimoDia, 1, CLOCK).fueraDePlazo()).as("7.º día: en plazo").isFalse();
        assertThat(ComunicacionBaja.crear(aceptado(TipoDocumento.BOLETA, "B001", LocalDate.of(2026, 9, 13)), 1, "Error", CLOCK).fueraDePlazo()).as("una baja no es un alta tardía").isFalse();
        Comprobante tardiaAceptada = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 6L, emision, EstadoDocumento.ACEPTADO_CON_OBS,
                RECEPTOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).firma("h", "n", "k").rehidratar();
        assertThatThrownBy(() -> ComunicacionBaja.crear(tardiaAceptada, 1, "Error", CLOCK)).hasMessageContaining("2957");
        Comprobante nc10 = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "10", "x"), emision, EstadoDocumento.ERROR_ENVIO);
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(nc10, 1, CLOCK)).as("la NC 10 no va en el resumen, ni en plazo ni fuera").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
    }

    static Comprobante notaDe(TipoDocumento tipo, String serie, Nota nota, LocalDate emision, EstadoDocumento estado) {
        return Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), tipo, serie, 1L, emision, estado, new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).nota(nota).firma("h", "n", "k").rehidratar();
    }

    /** #293: una nota de boleta se anula como la boleta, en el resumen diario (nunca en un RA: 2310); la de factura sigue en el RA. */
    @Test void unaNotaDeBoletaSeDaDeBajaEnElResumenDiario() {
        Comprobante ncB = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "07", "x"), LocalDate.of(2026, 9, 18), EstadoDocumento.ACEPTADO);
        ComunicacionBaja rc = ComunicacionBaja.crear(ncB, 2, "Error en el monto", CLOCK);
        assertThat(rc.resumenDiario()).isTrue();
        assertThat(rc.identificador()).isEqualTo("RC-20260920-2");
        assertThat(rc.tipoComprobante()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        Comprobante ncF = notaDe(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 4, "07", "x"), LocalDate.of(2026, 9, 18), EstadoDocumento.ACEPTADO);
        assertThat(ComunicacionBaja.crear(ncF, 1, "Error en el monto", CLOCK).resumenDiario()).isFalse();
    }

    /** #293: una nota de boleta que pasó el envío individual se informa en el resumen (estado 1), salvo la NC 10 y la ND 03, que no van en él (guía, p. 19). */
    @Test void unaNotaDeBoletaPasadaDelEnvioIndividualSeInformaEnElResumen() {
        LocalDate sextoDia = LocalDate.of(2026, 9, 14);
        Comprobante nc07 = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "07", "x"), sextoDia, EstadoDocumento.ERROR_ENVIO);
        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(nc07, 1, CLOCK);
        assertThat(alta.alta()).isTrue();
        assertThat(alta.resumenDiario()).isTrue();
        Comprobante nc10 = notaDe(TipoDocumento.NOTA_CREDITO, "BC01", new Nota(TipoDocumento.BOLETA, "B001", 4, "10", "x"), sextoDia, EstadoDocumento.ERROR_ENVIO);
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(nc10, 1, CLOCK)).extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
        Comprobante ncF = notaDe(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 4, "07", "x"), LocalDate.of(2026, 9, 18), EstadoDocumento.FIRMADO);
        assertThatThrownBy(() -> ComunicacionBaja.altaEnResumen(ncF, 1, CLOCK)).as("una nota de factura no va en un resumen").extracting("codigo").isEqualTo("RESUMEN_INVALIDO");
    }

    @Test void elMotivoVaDe3A100SinCaracteresDeControl() {
        Comprobante c = aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20));
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "a".repeat(101), CLOCK)).hasMessageContaining("2315");
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "Error\tRUC", CLOCK)).hasMessageContaining("2315");
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 1, "ab", CLOCK)).hasMessageContaining("2315");
        assertThat(ComunicacionBaja.crear(c, 1, "a".repeat(100), CLOCK).motivo()).hasSize(100);
    }

    @Test void elCorrelativoDelDiaEmpiezaEnUno() {
        Comprobante c = aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20));
        assertThatThrownBy(() -> ComunicacionBaja.crear(c, 0, "Error", CLOCK)).hasMessageContaining("correlativo");
        assertThat(ComunicacionBaja.crear(c, 12, "Error", CLOCK).identificador()).isEqualTo("RA-20260920-12");
    }

    @Test void unaBajaResueltaNoVuelveATransitar() {
        ComunicacionBaja aceptada = ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20)), 1, "Error", CLOCK);
        aceptada.marcarEnviada("T-1");
        aceptada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "R.zip");
        assertThat(aceptada.pendiente()).isFalse();
        assertThatThrownBy(() -> aceptada.aplicarCdr(new Cdr("2323", "ya informado", List.of()), "R.zip")).hasMessageContaining("no puede pasar");
        assertThatThrownBy(() -> aceptada.marcarEnviada("T-2")).hasMessageContaining("no puede pasar");
        ComunicacionBaja rechazada = ComunicacionBaja.crear(aceptado(TipoDocumento.FACTURA, "F001", LocalDate.of(2026, 9, 20)), 2, "Error", CLOCK);
        rechazada.marcarEnviada("T-3");
        rechazada.rechazarPorFault("0127", "El ticket no existe");
        assertThatThrownBy(() -> rechazada.marcarEnviada("T-4")).hasMessageContaining("no puede pasar");
        assertThatThrownBy(() -> rechazada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "R.zip")).hasMessageContaining("no puede pasar");
    }
}
