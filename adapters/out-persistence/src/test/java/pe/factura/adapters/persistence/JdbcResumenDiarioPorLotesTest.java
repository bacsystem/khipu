package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import pe.factura.domain.documento.*;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;

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
 * #301: las filas de un resumen diario de varias líneas comparten fecha de generación y correlativo (identificador, XML, ticket y CDR); la restricción única es por línea, y la
 * consulta que arma un resumen con todo lo de una fecha trae solo lo que corresponde.
 */
class JdbcResumenDiarioPorLotesTest extends PersistenciaTestBase {
    static final Clock EMISION = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Clock SEIS_DIAS_DESPUES = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate DIA = LocalDate.of(2026, 9, 13);
    static final LocalDate HOY = LocalDate.of(2026, 9, 19);

    JdbcComprobanteRepository comprobantes = new JdbcComprobanteRepository(jdbc);
    JdbcBajaRepository bajas = new JdbcBajaRepository(jdbc);

    private Comprobante boleta(UUID t, long numero, LocalDate fecha, EstadoDocumento estado) {
        Comprobante c = Comprobante.boleta(t, "B001", fecha, "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).crear(EMISION);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k" + numero + ".xml");
        comprobantes.guardar(c);
        if (estado == EstadoDocumento.ERROR_ENVIO) { c.marcarErrorEnvio("timeout"); comprobantes.guardar(c); }
        if (estado == EstadoDocumento.ACEPTADO) { c.marcarEnviado(); c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr.zip"); comprobantes.guardar(c); }
        return c;
    }

    private void rechazar(ComunicacionBaja fila) {
        fila.firmar("rc.xml");
        fila.marcarEnviada("T-1");
        fila.aplicarCdr(new Cdr("2987", "Una línea tiene errores", List.of()), "r.zip");
        bajas.guardar(fila);
    }

    @Test void lasFilasDeUnResumenCompartenCorrelativoYSeLeenPorLinea() {
        UUID t = tenantDePrueba();
        List<Comprobante> lote = List.of(boleta(t, 1, DIA, EstadoDocumento.FIRMADO), boleta(t, 2, DIA, EstadoDocumento.FIRMADO), boleta(t, 3, DIA, EstadoDocumento.ERROR_ENVIO));
        List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(lote, bajas.siguienteCorrelativo(t, HOY), SEIS_DIAS_DESPUES);
        filas.forEach(f -> { f.firmar("rc.xml"); bajas.guardar(f); });

        List<ComunicacionBaja> leidas = bajas.delResumen(t, HOY, 1);

        assertThat(leidas).extracting(ComunicacionBaja::id).containsExactlyElementsOf(filas.stream().map(ComunicacionBaja::id).toList());
        assertThat(leidas).extracting(ComunicacionBaja::linea).containsExactly(1, 2, 3);
        assertThat(leidas).extracting(ComunicacionBaja::lineas).containsOnly(3);
        assertThat(leidas).extracting(ComunicacionBaja::comprobanteId).containsExactlyElementsOf(lote.stream().map(Comprobante::id).toList());
        assertThat(leidas).extracting(ComunicacionBaja::xmlKey).containsOnly("rc.xml");
        assertThat(bajas.buscar(t, filas.get(1).id()).orElseThrow().deVariasLineas()).isTrue();
        assertThat(bajas.siguienteCorrelativo(t, HOY)).as("el correlativo del día avanza por resumen, no por fila").isEqualTo(2);
        assertThat(bajas.delResumen(t, HOY, 2)).isEmpty();
        assertThat(bajas.delResumen(UUID.randomUUID(), HOY, 1)).as("de otra empresa no se ve").isEmpty();
    }

    @Test void lasFilasSeActualizanTodasConElMismoTicketYElMismoCdr() {
        UUID t = tenantDePrueba();
        List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(List.of(boleta(t, 1, DIA, EstadoDocumento.FIRMADO), boleta(t, 2, DIA, EstadoDocumento.FIRMADO)), 1, SEIS_DIAS_DESPUES);
        filas.forEach(f -> { f.firmar("rc.xml"); bajas.guardar(f); });
        List<ComunicacionBaja> todas = bajas.delResumen(t, HOY, 1);
        ComunicacionBaja primera = todas.get(0);
        primera.marcarEnviada("T-77");
        primera.aplicarCdr(new Cdr("0", "aceptado", List.of()), "r.zip");
        todas.stream().skip(1).forEach(f -> f.copiarCicloDe(primera));

        todas.forEach(bajas::guardar);

        assertThat(bajas.delResumen(t, HOY, 1)).allSatisfy(f -> {
            assertThat(f.estado()).isEqualTo(EstadoBaja.ACEPTADA);
            assertThat(f.ticket()).isEqualTo("T-77");
            assertThat(f.cdrKey()).isEqualTo("r.zip");
        });
    }

    @Test void laMismaLineaDelMismoResumenNoSePuedeGuardarDosVecesYUnaBajaNoComparteCorrelativoConUnResumen() {
        UUID t = tenantDePrueba();
        ComunicacionBaja primera = ComunicacionBaja.altaEnResumen(boleta(t, 1, DIA, EstadoDocumento.FIRMADO), 1, SEIS_DIAS_DESPUES);
        ComunicacionBaja repetida = ComunicacionBaja.altaEnResumen(boleta(t, 2, DIA, EstadoDocumento.FIRMADO), 1, SEIS_DIAS_DESPUES);
        bajas.guardar(primera);

        assertThatThrownBy(() -> bajas.guardar(repetida)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test void lasLineasDeUnResumenSonDeUnoACientos() {
        UUID t = tenantDePrueba();
        ComunicacionBaja fila = ComunicacionBaja.altaEnResumen(boleta(t, 1, DIA, EstadoDocumento.FIRMADO), 1, SEIS_DIAS_DESPUES);
        bajas.guardar(fila);

        // Los CHECK de la migración: la línea cabe en el resumen y el resumen en 500.
        assertThatThrownBy(() -> jdbc.update("UPDATE comunicacion_baja SET linea = 501, lineas = 501 WHERE id = ?", fila.id())).hasMessageContaining("comunicacion_baja_linea_valida");
        assertThatThrownBy(() -> jdbc.update("UPDATE comunicacion_baja SET linea = 3, lineas = 2 WHERE id = ?", fila.id())).hasMessageContaining("comunicacion_baja_linea_valida");
    }

    @Test void elAgrupadorTraeSoloLoQueSunatTodaviaNoTieneDeEsaFechaYDeEsaEmpresa() {
        UUID t = tenantDePrueba();
        Comprobante cabeza = boleta(t, 1, DIA, EstadoDocumento.FIRMADO);
        Comprobante libre1 = boleta(t, 2, DIA, EstadoDocumento.FIRMADO);
        Comprobante libre2 = boleta(t, 3, DIA, EstadoDocumento.ERROR_ENVIO);
        boleta(t, 4, DIA.minusDays(1), EstadoDocumento.FIRMADO);   // otra fecha de emisión
        boleta(t, 5, DIA, EstadoDocumento.ACEPTADO);               // SUNAT ya la tiene
        Comprobante enCurso = boleta(t, 6, DIA, EstadoDocumento.FIRMADO);
        bajas.guardar(ComunicacionBaja.altaEnResumen(enCurso, 1, SEIS_DIAS_DESPUES));   // GENERADA: un resumen suyo en camino
        Comprobante rechazadaEnLote = boleta(t, 7, DIA, EstadoDocumento.FIRMADO);
        Comprobante suCompanera = boleta(t, 8, DIA, EstadoDocumento.FIRMADO);
        ComunicacionBaja.altasEnResumen(List.of(rechazadaEnLote, suCompanera), 2, SEIS_DIAS_DESPUES).forEach(this::rechazar);   // rechazado entero: se aísla de a una
        Comprobante rechazadaSola = boleta(t, 9, DIA, EstadoDocumento.FIRMADO);
        rechazar(ComunicacionBaja.altaEnResumen(rechazadaSola, 3, SEIS_DIAS_DESPUES));   // un resumen de una línea rechazado no impide agrupar de nuevo
        UUID otraEmpresa = tenantDePrueba();
        boleta(otraEmpresa, 1, DIA, EstadoDocumento.FIRMADO);

        List<Comprobante> encontradas = uow.ejecutar(() -> comprobantes.bloquearPorInformarDeLaFecha(t, DIA, cabeza.id(), 10));

        assertThat(encontradas).extracting(Comprobante::numero).containsExactly(2L, 3L, 9L);
        assertThat(encontradas).extracting(Comprobante::id).containsExactly(libre1.id(), libre2.id(), rechazadaSola.id());
        assertThat(encontradas.get(0).items()).as("vuelve completa, con sus ítems").hasSize(1);
    }

    /**
     * Lo que le cuesta a la base un resumen de 500 líneas, que el cálculo de CPU de {@code RendimientoDeBajasDeBoletaTest} no mide: recoger las 499 hermanas con su bloqueo,
     * marcarlas ENVIADO y guardar sus filas (lo que hace {@code informar}), y luego aplicarles el CDR (lo que hace {@code resolver}): bloquear, cambiar y guardar cada una. Con
     * Postgres de verdad, un hilo y 500 boletas de dos ítems. Falla si pasa de 60 s, que sería llevarse la transacción (y sus bloqueos) demasiado.
     */
    @Test void informarYResolverUnResumenDeQuinientasLineasCuestaPocosSegundosDeBase() {
        UUID t = tenantDePrueba();
        Comprobante cabeza = boleta(t, 1, DIA, EstadoDocumento.FIRMADO);
        for (long n = 2; n <= ComunicacionBaja.MAX_LINEAS_RESUMEN; n++) boleta(t, n, DIA, EstadoDocumento.FIRMADO);

        long t0 = System.nanoTime();
        uow.ejecutar(() -> {
            List<Comprobante> lote = new java.util.ArrayList<>(List.of(comprobantes.bloquear(t, cabeza.id()).orElseThrow()));
            lote.addAll(comprobantes.bloquearPorInformarDeLaFecha(t, DIA, cabeza.id(), ComunicacionBaja.MAX_LINEAS_RESUMEN - 1));
            assertThat(lote).hasSize(500);
            List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(lote, bajas.siguienteCorrelativo(t, HOY), SEIS_DIAS_DESPUES);
            for (int i = 0; i < lote.size(); i++) {
                filas.get(i).firmar("rc.xml");
                lote.get(i).informarEnResumen(filas.get(i).identificador(), false);
                comprobantes.guardar(lote.get(i));
                bajas.guardar(filas.get(i));
            }
            return null;
        });
        long informar = System.nanoTime();
        List<ComunicacionBaja> filas = bajas.delResumen(t, HOY, 1);
        filas.get(0).marcarEnviada("T-1");
        filas.get(0).aplicarCdr(new Cdr("0", "aceptado", List.of()), "r.zip");
        filas.stream().skip(1).forEach(f -> f.copiarCicloDe(filas.get(0)));
        uow.ejecutar(() -> {
            filas.forEach(bajas::guardar);
            for (ComunicacionBaja f : filas) {
                Comprobante c = comprobantes.bloquear(t, f.comprobanteId()).orElseThrow();
                c.aplicarCdr(f.cdr(), f.cdrKey());
                comprobantes.guardar(c);
            }
            return null;
        });
        long resolver = System.nanoTime();

        double segundosInformar = (informar - t0) / 1e9, segundosResolver = (resolver - informar) / 1e9;
        System.out.printf("Resumen de 500 líneas con Postgres: informar %.1f s · resolver %.1f s%n", segundosInformar, segundosResolver);
        assertThat(segundosInformar + segundosResolver).as("segundos de base de un resumen de 500 líneas").isLessThan(60);
        assertThat(comprobantes.buscar(t, cabeza.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void elAgrupadorRespetaElLimiteYElOrdenPorNumero() {
        UUID t = tenantDePrueba();
        Comprobante cabeza = boleta(t, 1, DIA, EstadoDocumento.FIRMADO);
        for (long n = 5; n >= 2; n--) boleta(t, n, DIA, EstadoDocumento.FIRMADO);

        assertThat(uow.ejecutar(() -> comprobantes.bloquearPorInformarDeLaFecha(t, DIA, cabeza.id(), 2))).extracting(Comprobante::numero).containsExactly(2L, 3L);
        assertThat(comprobantes.bloquearPorInformarDeLaFecha(t, DIA, cabeza.id(), 0)).isEmpty();
    }

    @Test void unaNotaDeCreditoQueNoVaEnElResumenNoSeAgrupa() {
        UUID t = tenantDePrueba();
        Comprobante cabeza = boleta(t, 1, DIA, EstadoDocumento.FIRMADO);
        // La NC con motivo 10 (ajuste de operaciones) de una boleta no tiene resumen diario: su plazo final es el del envío individual.
        Comprobante nc10 = Comprobante.nota(t, TipoDocumento.NOTA_CREDITO, "BC01", DIA, new Nota(TipoDocumento.BOLETA, "B001", 1, "10", "Ajuste"), new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).crear(EMISION);
        nc10.asignarNumero(1, "20100066603"); nc10.firmar("H", "nc.xml");
        comprobantes.guardar(nc10);
        Comprobante nc07 = Comprobante.nota(t, TipoDocumento.NOTA_CREDITO, "BC01", DIA, new Nota(TipoDocumento.BOLETA, "B001", 1, "07", "Devolución"), new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P3", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).crear(EMISION);
        nc07.asignarNumero(2, "20100066603"); nc07.firmar("H", "nc2.xml");
        comprobantes.guardar(nc07);

        assertThat(uow.ejecutar(() -> comprobantes.bloquearPorInformarDeLaFecha(t, DIA, cabeza.id(), 10))).extracting(Comprobante::id).containsExactly(nc07.id());
    }
}
