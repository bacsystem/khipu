package pe.factura.application.service;

import pe.factura.domain.documento.TipoDocumento;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ResumirComprobantesUseCase;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Dia;
import pe.factura.application.port.in.ResumirComprobantesUseCase.PorDia;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Resumen;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Total;
import pe.factura.application.port.out.ResumenDeComprobantesRepository;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Celda;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Facturado;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static pe.factura.domain.documento.EstadoDocumento.*;

/** El resumen de comprobantes de una empresa (#15): qué estados son «emitidos», «aceptados» y cada clase de atención, y que el rango llega tal cual al repositorio. */
class ResumirComprobantesServiceTest {
    static final UUID EMPRESA = UUID.randomUUID();

    /** Guarda con qué lo consultaron y devuelve lo que se le diga. */
    static class Resumenes implements ResumenDeComprobantesRepository {
        final List<Object[]> consultas = new ArrayList<>();
        Map<EstadoDocumento, Long> porEstado = new EnumMap<>(EstadoDocumento.class);
        List<Facturado> facturado = List.of();
        long boletasSinEnviar;
        final List<Object[]> consultasPorDia = new ArrayList<>();
        final List<Celda> celdas = new ArrayList<>();
        public Agregados resumir(UUID t, LocalDate desde, LocalDate hasta, LocalDate hoy) {
            consultas.add(new Object[]{t, desde, hasta, hoy});
            return new Agregados(porEstado, facturado, boletasSinEnviar);
        }
        public List<Celda> porDia(UUID t, LocalDate desde, LocalDate hasta) {
            consultasPorDia.add(new Object[]{t, desde, hasta});
            return celdas;
        }
    }

    Resumenes resumenes = new Resumenes();
    ResumirComprobantesService service = new ResumirComprobantesService(resumenes, Fakes.CLOCK);

    ResumirComprobantesServiceTest cuenta(EstadoDocumento e, long n) { resumenes.porEstado.put(e, n); return this; }

    Resumen resumir() { return service.resumir(EMPRESA, null, null); }

    @Test void unaEmpresaSinComprobantesTieneTodoEnCero() {
        Resumen r = resumir();

        assertThat(r.emitidos()).isZero();
        assertThat(r.aceptadosConCdr()).isZero();
        assertThat(r.rechazados()).isZero();
        assertThat(r.erroresDeEnvio()).isZero();
        assertThat(r.fueraDePlazo()).isZero();
        assertThat(r.boletasSinEnviar()).isZero();
        assertThat(r.atencionRequerida()).isZero();
        assertThat(r.facturado()).isEmpty();
    }

    @Test void emitidosCuentaTodoLoFirmadoEnAdelanteYNoLoQueNuncaSeFirmo() {
        cuenta(RECIBIDO, 1).cuenta(INVALIDO, 2).cuenta(FIRMADO, 4).cuenta(ERROR_ENVIO, 8).cuenta(PENDIENTE_AGRUPACION, 16).cuenta(ENVIADO, 32)
                .cuenta(ACEPTADO, 64).cuenta(ACEPTADO_CON_OBS, 128).cuenta(RECHAZADO, 256).cuenta(ANULADO, 512).cuenta(FUERA_DE_PLAZO, 1024);

        assertThat(resumir().emitidos()).isEqualTo(4 + 8 + 16 + 32 + 64 + 128 + 256 + 512 + 1024);
    }

    @Test void aceptadosConCdrSumaLosAceptadosConYSinObservacionesYNadaMas() {
        cuenta(ACEPTADO, 10).cuenta(ACEPTADO_CON_OBS, 3).cuenta(ENVIADO, 5).cuenta(ANULADO, 7).cuenta(RECHAZADO, 9);

        assertThat(resumir().aceptadosConCdr()).isEqualTo(13);
    }

    /** #24: «pendientes» del dashboard. Lo que está en camino lo dice el dominio (`estaEnCamino`): firmado, enviado y la boleta que espera su resumen; no lo que falló. */
    @Test void enCaminoCuentaLosEstadosQueElDominioDiceQueEstanEnCamino() {
        for (EstadoDocumento e : EstadoDocumento.values()) {
            resumenes.porEstado.clear();
            cuenta(e, 1);
            assertThat(resumir().enCamino()).as(e.name()).isEqualTo(e.estaEnCamino() ? 1 : 0);
        }
        resumenes.porEstado.clear();
        cuenta(FIRMADO, 2).cuenta(ENVIADO, 3).cuenta(PENDIENTE_AGRUPACION, 4).cuenta(ERROR_ENVIO, 50).cuenta(ACEPTADO, 100);
        assertThat(resumir().enCamino()).isEqualTo(9);
    }

    @Test void laAtencionRequeridaSeDesgloSaEnRechazadosErroresDeEnvioYFueraDePlazo() {
        cuenta(RECHAZADO, 3).cuenta(ERROR_ENVIO, 2).cuenta(FUERA_DE_PLAZO, 1).cuenta(ENVIADO, 50).cuenta(ACEPTADO, 100).cuenta(ANULADO, 4);

        Resumen r = resumir();

        assertThat(r.rechazados()).isEqualTo(3);
        assertThat(r.erroresDeEnvio()).isEqualTo(2);
        assertThat(r.fueraDePlazo()).isEqualTo(1);
        assertThat(r.atencionRequerida()).isEqualTo(6);
    }

    /**
     * #356: la boleta que el cliente dejó FIRMADA sin enviar y ya pasó el 5.º día es «atención requerida» aunque su estado, solo, no lo diga. Es una cuenta aparte de las
     * de arriba: el envío que falla ya cuenta como error de envío, y una boleta no se suma dos veces.
     */
    @Test void laBoletaSinEnviarSumaALaAtencionRequeridaSinTocarLasOtras() {
        cuenta(RECHAZADO, 3).cuenta(ERROR_ENVIO, 2).cuenta(FIRMADO, 40);
        resumenes.boletasSinEnviar = 4;

        Resumen r = resumir();

        assertThat(r.boletasSinEnviar()).as("de las 40 FIRMADAS solo cuentan las que el repositorio dice").isEqualTo(4);
        assertThat(r.erroresDeEnvio()).isEqualTo(2);
        assertThat(r.atencionRequerida()).isEqualTo(3 + 2 + 4);
    }

    /** La fecha de hoy es la del reloj del servicio, no la de la base: de ella depende qué boleta ya pide atención. */
    @Test void elRepositorioRecibeLaFechaDeHoyDelReloj() {
        resumir();

        assertThat(resumenes.consultas.get(0)[3]).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    /** Si el dominio agrega un estado que pide atención, el desglose tiene que crecer con él: este test lo obliga a decidirlo. */
    @Test void lasTresClasesDeAtencionSonExactamenteLosEstadosQueRequierenAtencion() {
        for (EstadoDocumento e : EstadoDocumento.values()) {
            resumenes.porEstado.clear();
            cuenta(e, 1);
            Resumen r = resumir();
            assertThat(r.atencionRequerida()).as(e.name()).isEqualTo(e.requiereAtencion() ? 1 : 0);
        }
    }

    @Test void unEstadoQueSoloTieneUnaClaseNoSeCuentaEnLasOtras() {
        cuenta(ERROR_ENVIO, 5);

        Resumen r = resumir();

        assertThat(r.erroresDeEnvio()).isEqualTo(5);
        assertThat(r.rechazados()).isZero();
        assertThat(r.fueraDePlazo()).isZero();
        assertThat(r.aceptadosConCdr()).isZero();
    }

    @Test void lasMonedasSeCopianTalCualEnSuOrden() {
        resumenes.facturado = List.of(new Facturado("PEN", new BigDecimal("123.45")), new Facturado("USD", new BigDecimal("-5.00")));

        assertThat(resumir().facturado()).containsExactly(new Total("PEN", new BigDecimal("123.45")), new Total("USD", new BigDecimal("-5.00")));
    }

    @Test void elRangoYLaEmpresaLlegandAlRepositorioTalCualYElResumenLosDevuelve() {
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);

        Resumen r = service.resumir(EMPRESA, desde, hasta);

        assertThat(resumenes.consultas).hasSize(1);
        assertThat(resumenes.consultas.get(0)).containsExactly(EMPRESA, desde, hasta, LocalDate.of(2026, 9, 13));
        assertThat(r.desde()).isEqualTo(desde);
        assertThat(r.hasta()).isEqualTo(hasta);
    }

    @Test void unRangoAbiertoLlegaComoNulos() {
        service.resumir(EMPRESA, null, LocalDate.of(2026, 9, 30));
        service.resumir(EMPRESA, LocalDate.of(2026, 9, 1), null);

        assertThat(resumenes.consultas.get(0)[1]).isNull();
        assertThat(resumenes.consultas.get(1)[2]).isNull();
    }

    @Test void unSoloDiaEsUnRangoValidoYUnoAlReverEsRangoInvalido() {
        LocalDate dia = LocalDate.of(2026, 9, 15);
        service.resumir(EMPRESA, dia, dia);

        DomainException e = catchThrowableOfType(DomainException.class, () -> service.resumir(EMPRESA, dia.plusDays(1), dia));

        assertThat(e.codigo()).isEqualTo("RANGO_INVALIDO");
        assertThat(resumenes.consultas).as("el rango inválido no llega a consultar").hasSize(1);
    }

    // --- la emisión por día (#24) -----------------------------------------------------------------------------------------------------------

    static final LocalDate PRIMERO = LocalDate.of(2026, 10, 1);
    static final LocalDate TREINTA_Y_UNO = LocalDate.of(2026, 10, 31);

    ResumirComprobantesServiceTest celda(int dia, EstadoDocumento e, String moneda, long n, String neto) { return celda(dia, "01", e, moneda, n, neto); }

    ResumirComprobantesServiceTest celda(int dia, String tipo, EstadoDocumento e, String moneda, long n, String neto) {
        resumenes.celdas.add(new Celda(LocalDate.of(2026, 10, dia), tipo, e, moneda, n, neto == null ? null : new BigDecimal(neto)));
        return this;
    }

    PorDia porDia() { return service.porDia(EMPRESA, PRIMERO, TREINTA_Y_UNO); }

    /** Factura, boleta y sus notas suman; las notas de crédito ya vienen restando del repositorio. */
    @Test void lasVentasCuentanLasCuatroYLasNotasDeCreditoRestan() {
        celda(4, "01", ACEPTADO, "PEN", 1, "1000").celda(4, "03", ACEPTADO, "PEN", 1, "200").celda(4, "07", ACEPTADO, "PEN", 1, "-300").celda(4, "08", ACEPTADO, "PEN", 1, "50");

        assertThat(porDia().dias()).containsExactly(new Dia(LocalDate.of(2026, 10, 4), 4, List.of(new Total("PEN", new BigDecimal("950")))));
    }

    /**
     * Lo que no es una venta no entra ni en lo emitido ni en lo facturado: los tipos que agregan otras ramas (04 liquidación de compra, 20 retención, 40 percepción) y
     * cualquier código desconocido. El día que solo tiene esos ni aparece.
     */
    @Test void losTiposQueNoSonVentasNoEntranNiEnEmitidosNiEnFacturado() {
        celda(6, "01", ACEPTADO, "PEN", 1, "100");
        for (String tipo : new String[]{"04", "20", "40", "09", "31", "99", null}) celda(6, tipo, ACEPTADO, "PEN", 10, "5000").celda(8, tipo, ACEPTADO, "PEN", 10, "5000");

        assertThat(porDia().dias()).containsExactly(new Dia(LocalDate.of(2026, 10, 6), 1, List.of(new Total("PEN", new BigDecimal("100")))));
    }

    @Test void losTiposDeVentaSonExactamenteFacturaBoletaYSusNotas() {
        assertThat(ResumirComprobantesService.TIPOS_DE_VENTA).containsExactlyInAnyOrder("01", "03", "07", "08");
        // La misma definición que el resumen y el SIRE: la del dominio, código por código.
        for (TipoDocumento t : TipoDocumento.values()) assertThat(ResumirComprobantesService.esVenta(t.codigo())).as(t.name()).isEqualTo(t.esVenta());
    }

    @Test void sinComprobantesNoHayDiasYElRangoVuelveTalCual() {
        PorDia r = porDia();

        assertThat(r.dias()).isEmpty();
        assertThat(r.desde()).isEqualTo(PRIMERO);
        assertThat(r.hasta()).isEqualTo(TREINTA_Y_UNO);
        assertThat(resumenes.consultasPorDia.get(0)).containsExactly(EMPRESA, PRIMERO, TREINTA_Y_UNO);
    }

    /** Lo emitido de un día suma sus estados con la regla del resumen: todo lo firmado en adelante, rechazado y anulado incluidos; nunca lo que no se firmó. */
    @Test void cadaDiaSumaSusEmitidosConLaReglaDelDominio() {
        celda(5, ACEPTADO, "PEN", 3, "300").celda(5, RECHAZADO, "PEN", 2, "200").celda(5, ANULADO, "PEN", 1, "100").celda(5, RECIBIDO, null, 4, null).celda(5, INVALIDO, null, 8, null);

        assertThat(porDia().dias()).containsExactly(new Dia(LocalDate.of(2026, 10, 5), 6, List.of(new Total("PEN", new BigDecimal("300")))));
    }

    /** Lo facturado de un día suma solo los estados que cuentan como facturados, por moneda y ordenado por moneda, y se suma entre estados de la misma moneda. */
    @Test void loFacturadoDeUnDiaSumaLosEstadosQueCuentanPorMonedaYOrdenado() {
        celda(7, ACEPTADO, "USD", 1, "50.50").celda(7, ACEPTADO, "PEN", 2, "100.10").celda(7, ENVIADO, "PEN", 1, "0.90").celda(7, FUERA_DE_PLAZO, "PEN", 1, "999")
                .celda(7, ERROR_ENVIO, "USD", 1, "-0.50");

        Dia dia = porDia().dias().get(0);

        assertThat(dia.emitidos()).isEqualTo(6);
        assertThat(dia.facturado()).containsExactly(new Total("PEN", new BigDecimal("101.00")), new Total("USD", new BigDecimal("50.00")));
    }

    /** Los estados que facturan son exactamente los del dominio, uno por uno: si se agrega uno, esto lo sigue sin tocarse. */
    @Test void losEstadosQueFacturanEnUnDiaSonLosDelDominio() {
        for (EstadoDocumento e : EstadoDocumento.values()) {
            resumenes.celdas.clear();
            celda(9, e, "PEN", 1, "10");
            List<Dia> dias = porDia().dias();
            boolean hayDia = e.fueEmitido() || e.cuentaComoFacturado();
            assertThat(dias).as(e.name()).hasSize(hayDia ? 1 : 0);
            if (hayDia) {
                assertThat(dias.get(0).emitidos()).as(e.name()).isEqualTo(e.fueEmitido() ? 1 : 0);
                assertThat(dias.get(0).facturado()).as(e.name()).hasSize(e.cuentaComoFacturado() ? 1 : 0);
            }
        }
    }

    @Test void losDiasVanOrdenadosYUnDiaSoloConLoQueNoSeFirmoNoAparece() {
        celda(20, ACEPTADO, "PEN", 1, "1").celda(3, ACEPTADO, "PEN", 1, "1").celda(12, RECIBIDO, null, 5, null).celda(15, RECHAZADO, "PEN", 1, "7");

        List<Dia> dias = porDia().dias();

        assertThat(dias).extracting(Dia::fecha).containsExactly(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20));
        assertThat(dias.get(1).facturado()).as("un rechazado se emitió pero no factura").isEmpty();
    }

    @Test void unDiaYSesentaYDosDiasSonRangosValidos() {
        service.porDia(EMPRESA, PRIMERO, PRIMERO);
        service.porDia(EMPRESA, PRIMERO, PRIMERO.plusDays(ResumirComprobantesUseCase.MAXIMO_DIAS_POR_DIA - 1));

        assertThat(resumenes.consultasPorDia).hasSize(2);
    }

    @Test void masDeSesentaYDosDiasEsRangoInvalidoYNoConsulta() {
        DomainException e = catchThrowableOfType(DomainException.class, () -> service.porDia(EMPRESA, PRIMERO, PRIMERO.plusDays(ResumirComprobantesUseCase.MAXIMO_DIAS_POR_DIA)));

        assertThat(e.codigo()).isEqualTo("RANGO_INVALIDO");
        assertThat(e.getMessage()).contains("62 días").contains("63");
        assertThat(resumenes.consultasPorDia).isEmpty();
    }

    @Test void unRangoAlRevesEsRangoInvalidoYNoConsulta() {
        DomainException e = catchThrowableOfType(DomainException.class, () -> service.porDia(EMPRESA, TREINTA_Y_UNO, PRIMERO));

        assertThat(e.codigo()).isEqualTo("RANGO_INVALIDO");
        assertThat(resumenes.consultasPorDia).isEmpty();
    }

    @Test void sinUnoDeLosExtremosEsParametroInvalido() {
        assertThat(catchThrowableOfType(DomainException.class, () -> service.porDia(EMPRESA, null, TREINTA_Y_UNO)).codigo()).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(catchThrowableOfType(DomainException.class, () -> service.porDia(EMPRESA, PRIMERO, null)).codigo()).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(resumenes.consultasPorDia).isEmpty();
    }
}
