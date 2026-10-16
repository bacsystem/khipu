package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CargarPadronRucUseCase;
import pe.factura.application.port.out.FuentePadronRuc;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.application.port.out.PadronRucRepository.ResultadoCarga;
import pe.factura.domain.DomainException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Carga el padrón reducido de SUNAT (#361). Un archivo cortado no puede dejar el padrón vacío ni a medias: la carga exige que traiga al menos el
 * {@value #PORCENTAJE_MINIMO} % de las filas de la última carga buena, y la base recién se reemplaza si el archivo se leyó entero. Cada intento, bueno o malo, queda en
 * el registro de cargas.
 */
@RequiredArgsConstructor
public class CargarPadronRucService implements CargarPadronRucUseCase {
    /** El padrón crece o baja unas pocas décimas por día; una caída de más de esto es un archivo roto, no SUNAT dando de baja a un décimo de los contribuyentes. */
    static final int PORCENTAJE_MINIMO = 90;
    /** Para la primera carga, que no tiene con qué compararse: el padrón real tiene decenas de millones de filas, un archivo de miles es una descarga equivocada. */
    private final long minimoPrimeraCarga;
    private final FuentePadronRuc fuente;
    private final PadronRucRepository padron;
    private final Clock clock;

    @Override public CargaRegistrada cargar() {
        Instant inicio = clock.instant();
        String origen = "(sin abrir)";
        AtomicLong descartadas = new AtomicLong();
        try (FuentePadronRuc.Descarga descarga = fuente.abrir()) {
            origen = descarga.origen();
            long minimo = padron.ultimaCargaExitosa().map(c -> c.filas() * PORCENTAJE_MINIMO / 100).orElse(minimoPrimeraCarga);
            ResultadoCarga r;
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(descarga.contenido(), StandardCharsets.ISO_8859_1), 1 << 16);
                 Stream<String> lineas = lector.lines()) {
                r = padron.reemplazar(lineas
                        .filter(l -> !l.isBlank() && !FormatoDelPadron.esEncabezado(l))
                        .map(l -> {
                            var c = FormatoDelPadron.parsear(l);
                            if (c.isEmpty()) descartadas.incrementAndGet();
                            return c;
                        })
                        .flatMap(java.util.Optional::stream), minimo);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            CargaRegistrada carga = new CargaRegistrada(inicio, clock.instant(), true, origen, r.filas(), descartadas.get(), r.nuevas(), r.cambiadas(), r.borradas(), null);
            padron.registrar(carga);
            return carga;
        } catch (DomainException e) {
            // CARGA_EN_CURSO no es un fallo de esta carga: otra la está haciendo y esa dejará su propio registro.
            if (!"CARGA_EN_CURSO".equals(e.codigo())) registrarFallo(inicio, origen, descartadas.get(), e.codigo() + ": " + e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            registrarFallo(inicio, origen, descartadas.get(), "No se pudo leer el archivo del padrón: " + e.getMessage(), e);
            throw new DomainException("PADRON_NO_DISPONIBLE", "No se pudo leer el archivo del padrón: " + e.getMessage());
        } catch (RuntimeException e) {
            registrarFallo(inicio, origen, descartadas.get(), e.toString(), e);
            throw e;
        }
    }

    /** Deja el fallo en el registro sin tapar la causa: si ni eso se puede escribir, el error del registro viaja como suprimido de la causa. */
    private void registrarFallo(Instant inicio, String origen, long descartadas, String error, Exception causa) {
        try {
            padron.registrar(new CargaRegistrada(inicio, clock.instant(), false, origen, 0, descartadas, 0, 0, 0, error));
        } catch (RuntimeException e) {
            causa.addSuppressed(e);
        }
    }
}
