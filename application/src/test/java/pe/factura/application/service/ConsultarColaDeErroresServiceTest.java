package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.ErrorDeEmision;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Pagina;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.application.port.out.ColaDeErroresRepository.Fila;
import pe.factura.application.port.out.ColaDeErroresRepository.Ubicacion;
import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La cola global de errores (#196): la clase y el fault de cada fila salen de las reglas del dominio, y el filtro y la página llegan tal cual al repositorio. */
class ConsultarColaDeErroresServiceTest {
    static final UUID EMPRESA = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");

    /** Guarda con qué lo consultaron y devuelve lo que se le diga. */
    static class Cola implements ColaDeErroresRepository {
        final List<Object[]> consultas = new ArrayList<>();
        final List<Object[]> conteos = new ArrayList<>();
        List<Fila> filas = new ArrayList<>();
        long total;
        public List<Fila> listar(Filtro f, int pagina, int porPagina) { consultas.add(new Object[]{f, pagina, porPagina}); return filas; }
        public long contar(Filtro f) { conteos.add(new Object[]{f}); return total; }
        public Optional<Ubicacion> ubicar(UUID id) { throw new AssertionError("listar no ubica"); }
    }

    Cola cola = new Cola();
    ConsultarColaDeErroresService service = new ConsultarColaDeErroresService(cola);

    static Fila fila(EstadoDocumento estado, String ultimoError, String cdrCodigo, String cdrDescripcion, Instant siguiente) {
        return new Fila(UUID.randomUUID(), EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "Ana", "20100066603-01-F001-7", "01", "F001", 7, LocalDate.of(2026, 10, 12), estado, 3,
                ultimoError, cdrCodigo, cdrDescripcion, siguiente, AHORA);
    }

    ErrorDeEmision unico(Fila f) {
        cola.filas = List.of(f);
        return service.listar(new Filtro(null, null, null), 1, 20).errores().get(0);
    }

    @Test void unErrorDeEnvioDiceSuClaseSuFaultSusIntentosYCuandoSeReintenta() {
        Instant proximo = AHORA.plusSeconds(600);

        ErrorDeEmision e = unico(fila(EstadoDocumento.ERROR_ENVIO, "0109 - El sistema no puede responder", null, null, proximo));

        assertThat(e.clase()).isEqualTo(ClaseDeError.ERROR_DE_ENVIO);
        assertThat(e.fault()).isEqualTo(new FaultSunat("0109", "El sistema no puede responder"));
        assertThat(e.intentos()).isEqualTo(3);
        assertThat(e.proximoIntento()).isEqualTo(proximo);
        assertThat(e.accionable()).isTrue();
    }

    @Test void unErrorDeFormatoTomaElFaultDelCdrYNoSePuedeAccionar() {
        ErrorDeEmision e = unico(fila(EstadoDocumento.RECHAZADO, null, "1033", "El comprobante fue registrado previamente", null));

        assertThat(e.clase()).isEqualTo(ClaseDeError.ERROR_DE_FORMATO);
        assertThat(e.fault()).isEqualTo(new FaultSunat("1033", "El comprobante fue registrado previamente"));
        assertThat(e.accionable()).isFalse();
        assertThat(e.proximoIntento()).isNull();
    }

    @Test void unFueraDePlazoTomaSuFaultDelUltimoErrorYNoSePuedeAccionar() {
        ErrorDeEmision e = unico(fila(EstadoDocumento.FUERA_DE_PLAZO, "2108 - Presentación fuera de fecha", null, null, null));

        assertThat(e.clase()).isEqualTo(ClaseDeError.FUERA_DE_PLAZO);
        assertThat(e.fault().codigo()).isEqualTo("2108");
        assertThat(e.accionable()).isFalse();
    }

    @Test void unFalloNuestroNoTieneCodigoDeSunat() {
        ErrorDeEmision e = unico(fila(EstadoDocumento.ERROR_ENVIO, "INFRA - storage no disponible", null, null, null));

        assertThat(e.fault()).isEqualTo(new FaultSunat(null, "INFRA - storage no disponible"));
    }

    @Test void trasladaLosDatosDeLaEmpresaYDelComprobanteTalCual() {
        Fila f = fila(EstadoDocumento.ERROR_ENVIO, "x", null, null, null);

        ErrorDeEmision e = unico(f);

        assertThat(e.comprobanteId()).isEqualTo(f.comprobanteId());
        assertThat(e.empresaId()).isEqualTo(EMPRESA);
        assertThat(e.ruc()).isEqualTo("20100066603");
        assertThat(e.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(e.cuentaId()).isEqualTo(CUENTA);
        assertThat(e.cuentaNombre()).isEqualTo("Ana");
        assertThat(e.nombreArchivo()).isEqualTo("20100066603-01-F001-7");
        assertThat(e.tipo()).isEqualTo("01");
        assertThat(e.serie()).isEqualTo("F001");
        assertThat(e.numero()).isEqualTo(7);
        assertThat(e.fechaEmision()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(e.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(e.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test void devuelveElTotalSinPaginarYConservaElOrdenDelRepositorio() {
        cola.filas = List.of(fila(EstadoDocumento.ERROR_ENVIO, "a", null, null, null), fila(EstadoDocumento.FUERA_DE_PLAZO, "b", null, null, null));
        cola.total = 57;

        Pagina p = service.listar(new Filtro(null, null, null), 2, 20);

        assertThat(p.total()).isEqualTo(57);
        assertThat(p.errores()).extracting(ErrorDeEmision::clase).containsExactly(ClaseDeError.ERROR_DE_ENVIO, ClaseDeError.FUERA_DE_PLAZO);
    }

    @Test void unaColaVaciaEsUnaPaginaVacia() {
        Pagina p = service.listar(new Filtro(null, null, null), 1, 20);

        assertThat(p.errores()).isEmpty();
        assertThat(p.total()).isZero();
    }

    @Test void elFiltroYLaPaginaLlegamAlRepositorioYElTotalCuentaConElMismoFiltro() {
        service.listar(new Filtro(ClaseDeError.ERROR_DE_ENVIO, EMPRESA, "andina"), 3, 50);

        assertThat(cola.consultas).hasSize(1);
        assertThat(cola.consultas.get(0)).containsExactly(new Filtro(ClaseDeError.ERROR_DE_ENVIO, EMPRESA, "andina"), 3, 50);
        assertThat(cola.conteos.get(0)).containsExactly(new Filtro(ClaseDeError.ERROR_DE_ENVIO, EMPRESA, "andina"));
    }

    @Test void elTextoSeRecortaYUnoEnBlancoNoBusca() {
        service.listar(new Filtro(null, null, "  20100066603  "), 1, 20);
        service.listar(new Filtro(null, null, "   "), 1, 20);
        service.listar(new Filtro(null, null, ""), 1, 20);

        assertThat(((Filtro) cola.consultas.get(0)[0]).texto()).isEqualTo("20100066603");
        assertThat(((Filtro) cola.consultas.get(1)[0]).texto()).isNull();
        assertThat(((Filtro) cola.consultas.get(2)[0]).texto()).isNull();
    }

    /** El repositorio filtra con el mismo criterio que {@code ClaseDeError}; si alguna vez devolviera algo que no es de la cola, se avisa en lugar de inventar una clase. */
    @Test void unaFilaQueNoEsDeLaColaEsUnError() {
        cola.filas = List.of(fila(EstadoDocumento.RECHAZADO, null, "2324", "duplicado", null));

        assertThatThrownBy(() -> service.listar(new Filtro(null, null, null), 1, 20)).isInstanceOf(IllegalStateException.class);
    }
}
