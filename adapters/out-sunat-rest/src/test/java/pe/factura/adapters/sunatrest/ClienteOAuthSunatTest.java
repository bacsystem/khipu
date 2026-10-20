package pe.factura.adapters.sunatrest;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/** H4 (#318): la caché de tokens pide uno solo por credencial a la vez, sin que una credencial lenta frene a las demás, y descarta los vencidos. */
@WireMockTest
class ClienteOAuthSunatTest {
    final RestValidezGatewayTest.Reloj reloj = new RestValidezGatewayTest.Reloj();

    static PedidoDeToken pedido(WireMockRuntimeInfo wm, String id) {
        return PedidoDeToken.clientCredentials(wm.getHttpBaseUrl() + "/v1/clientesextranet/" + id + "/oauth2/token/", "scope", id, "secreto-" + id);
    }

    static void token(String id, String valor, int retardoMs) {
        stubFor(post("/v1/clientesextranet/" + id + "/oauth2/token/")
                .willReturn(okJson("{\"access_token\":\"" + valor + "\",\"expires_in\":3600}").withFixedDelay(retardoMs)));
    }

    @Test void unaCredencialLentaNoFrenaAOtra(WireMockRuntimeInfo wm) throws Exception {
        token("lenta", "tk-lenta", 2000);
        token("rapida", "tk-rapida", 0);
        ClienteOAuthSunat oauth = new ClienteOAuthSunat(Duration.ofSeconds(1), Duration.ofSeconds(5), reloj);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<String> lenta = hilos.submit(() -> oauth.token(pedido(wm, "lenta")));
            // Que el pedido lento ya esté en vuelo (con su cerrojo tomado) antes de pedir el otro.
            for (int i = 0; i < 100 && findAll(postRequestedFor(urlMatching(".*/lenta/.*"))).isEmpty(); i++) Thread.sleep(20);

            long antes = System.nanoTime();
            assertThat(oauth.token(pedido(wm, "rapida"))).isEqualTo("tk-rapida");
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - antes);

            assertThat(ms).as("la credencial rápida no esperó a la lenta (%d ms)", ms).isLessThan(1500);
            assertThat(lenta.isDone()).as("la lenta seguía en vuelo").isFalse();
            assertThat(lenta.get(10, TimeUnit.SECONDS)).isEqualTo("tk-lenta");
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test void dosConsultasALaVezDeLaMismaCredencialPidenUnSoloToken(WireMockRuntimeInfo wm) throws Exception {
        token("misma", "tk-misma", 500);
        ClienteOAuthSunat oauth = new ClienteOAuthSunat(Duration.ofSeconds(1), Duration.ofSeconds(5), reloj);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture<?>[] todas = new CompletableFuture<?>[4];
            for (int i = 0; i < todas.length; i++)
                todas[i] = CompletableFuture.supplyAsync(() -> { esperar(largada); return oauth.token(pedido(wm, "misma")); }, hilos);
            largada.countDown();
            CompletableFuture.allOf(todas).get(10, TimeUnit.SECONDS);

            for (CompletableFuture<?> f : todas) assertThat(f.get()).isEqualTo("tk-misma");
            verify(1, postRequestedFor(urlEqualTo("/v1/clientesextranet/misma/oauth2/token/")));
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test void alGuardarUnTokenSeDescartanLosVencidos(WireMockRuntimeInfo wm) {
        token("a", "tk-a", 0);
        token("b", "tk-b", 0);
        ClienteOAuthSunat oauth = new ClienteOAuthSunat(Duration.ofSeconds(1), Duration.ofSeconds(5), reloj);

        oauth.token(pedido(wm, "a"));
        reloj.avanzar(Duration.ofHours(2));
        oauth.token(pedido(wm, "b"));

        assertThat(oauth.enCache()).as("el de «a» venció y se descartó").isEqualTo(1);
    }

    private static void esperar(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
