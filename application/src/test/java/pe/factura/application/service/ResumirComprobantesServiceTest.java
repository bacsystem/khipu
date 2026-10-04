package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Resumen;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Total;
import pe.factura.application.port.out.ResumenDeComprobantesRepository;
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
        public Agregados resumir(UUID t, LocalDate desde, LocalDate hasta) { consultas.add(new Object[]{t, desde, hasta}); return new Agregados(porEstado, facturado); }
    }

    Resumenes resumenes = new Resumenes();
    ResumirComprobantesService service = new ResumirComprobantesService(resumenes);

    ResumirComprobantesServiceTest cuenta(EstadoDocumento e, long n) { resumenes.porEstado.put(e, n); return this; }

    Resumen resumir() { return service.resumir(EMPRESA, null, null); }

    @Test void unaEmpresaSinComprobantesTieneTodoEnCero() {
        Resumen r = resumir();

        assertThat(r.emitidos()).isZero();
        assertThat(r.aceptadosConCdr()).isZero();
        assertThat(r.rechazados()).isZero();
        assertThat(r.erroresDeEnvio()).isZero();
        assertThat(r.fueraDePlazo()).isZero();
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

    @Test void laAtencionRequeridaSeDesgloSaEnRechazadosErroresDeEnvioYFueraDePlazo() {
        cuenta(RECHAZADO, 3).cuenta(ERROR_ENVIO, 2).cuenta(FUERA_DE_PLAZO, 1).cuenta(ENVIADO, 50).cuenta(ACEPTADO, 100).cuenta(ANULADO, 4);

        Resumen r = resumir();

        assertThat(r.rechazados()).isEqualTo(3);
        assertThat(r.erroresDeEnvio()).isEqualTo(2);
        assertThat(r.fueraDePlazo()).isEqualTo(1);
        assertThat(r.atencionRequerida()).isEqualTo(6);
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
        assertThat(resumenes.consultas.get(0)).containsExactly(EMPRESA, desde, hasta);
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
}
