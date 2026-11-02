package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SondeoDeSunat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sondea los servicios de SUNAT con un {@code GET} al WSDL de cada uno ({@code ?wsdl}): si contesta con un éxito dentro del plazo, está arriba. No envía ningún
 * comprobante ni usa credenciales. Los servicios se sondean a la vez (el peor caso es un plazo, no la suma de todos) y el resultado se **guarda un rato**: el monitor se
 * refresca solo cada pocos segundos y no debe llamar a SUNAT en cada refresco ni con cada administrador que lo mire.
 */
public class SondeoDeSunatHttp implements SondeoDeSunat {
    private final Map<Servicio, String> urls;
    private final Duration plazo;
    private final Duration vigencia;
    private final Clock clock;
    private final HttpClient http;

    private List<Resultado> guardado;
    private Instant guardadoEn;

    /** {@code urls}: las de cada servicio; las que están vacías no se sondean. */
    public SondeoDeSunatHttp(Map<Servicio, String> urls, Duration plazo, Duration vigencia, Clock clock) {
        this(urls, plazo, vigencia, clock, HttpClient.newBuilder().connectTimeout(plazo).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SondeoDeSunatHttp(Map<Servicio, String> urls, Duration plazo, Duration vigencia, Clock clock, HttpClient http) {
        this.urls = new EnumMap<>(Servicio.class);
        urls.forEach((s, u) -> { if (u != null && !u.isBlank()) this.urls.put(s, u.strip()); });
        this.plazo = plazo;
        this.vigencia = vigencia;
        this.clock = clock;
        this.http = http;
    }

    @Override public synchronized List<Resultado> sondear() {
        Instant ahora = clock.instant();
        if (guardado != null && guardadoEn != null && ahora.isBefore(guardadoEn.plus(vigencia))) return guardado;
        Map<Servicio, CompletableFuture<Resultado>> pedidos = new EnumMap<>(Servicio.class);
        urls.forEach((s, u) -> pedidos.put(s, CompletableFuture.supplyAsync(() -> sondear(s, u))));
        List<Resultado> resultados = new ArrayList<>();
        for (Servicio s : Servicio.values()) if (pedidos.containsKey(s)) resultados.add(pedidos.get(s).join());
        guardado = List.copyOf(resultados);
        guardadoEn = ahora;
        return guardado;
    }

    private Resultado sondear(Servicio servicio, String url) {
        long inicio = System.nanoTime();
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(url + (url.contains("?") ? "&wsdl" : "?wsdl"))).timeout(plazo).GET().build();
            int estado = http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return estado >= 200 && estado < 300 ? new Resultado(servicio, true, ms, null) : new Resultado(servicio, false, ms, "HTTP " + estado);
        } catch (HttpTimeoutException e) {
            return new Resultado(servicio, false, null, "No contestó en " + plazo.toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(servicio, false, null, "Sondeo interrumpido");
        } catch (IOException | IllegalArgumentException e) {
            return new Resultado(servicio, false, null, "Sin conexión");
        }
    }
}
