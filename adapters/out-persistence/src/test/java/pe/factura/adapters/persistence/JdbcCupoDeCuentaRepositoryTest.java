package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.plan.RecursoDelPlan;
import pe.factura.domain.plan.UsoDeRecursos;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Lo que ocupa el cupo del plan de una cuenta (#498), con Postgres real: las API keys activas de todas sus empresas, los usuarios activos más las invitaciones pendientes
 * que no vencieron, las empresas; y el bloqueo que impide que dos altas simultáneas tomen el último lugar libre.
 */
class JdbcCupoDeCuentaRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");
    static final AtomicLong RUC = new AtomicLong(700);

    JdbcCupoDeCuentaRepository repo = new JdbcCupoDeCuentaRepository(jdbc);

    UUID cuenta() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, id + "@negocio.pe", Timestamp.from(Instant.parse("2026-09-01T10:00:00Z")));
        return id;
    }

    UUID empresa(UUID cuenta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, 'EMPRESA SAC', 'BETA', ?)", id, "20" + String.format("%09d", RUC.getAndIncrement()), cuenta);
        return id;
    }

    void apiKey(UUID empresa, boolean activa) {
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa) VALUES (?, ?, ?, 'fk_abc', ?)", UUID.randomUUID(), empresa, UUID.randomUUID().toString().replace("-", ""), activa);
    }

    UUID usuario(UUID cuenta, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, cuenta_id, email, password_hash, rol, activo) VALUES (?, ?, ?, 'hash', 'ADMIN', ?)", id, cuenta, id + "@negocio.pe", activo);
        return id;
    }

    void invitacion(UUID cuenta, String estado, Instant expiraEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO invitacion (id, cuenta_id, email, rol, token_hash, expira_en, estado, envios, created_at) VALUES (?, ?, ?, 'EMISOR', ?, ?, ?, 1, ?)",
                id, cuenta, id + "@invitado.pe", id.toString().replace("-", ""), Timestamp.from(expiraEn), estado, Timestamp.from(AHORA.minus(Duration.ofDays(1))));
    }

    long ocupados(UUID cuenta, RecursoDelPlan recurso) { return repo.ocupados(cuenta, recurso, AHORA); }

    @Test void lasApiKeysSonLasActivasDeTodasLasEmpresasDeLaCuentaYNadaDeOtra() {
        UUID c = cuenta();
        UUID e1 = empresa(c);
        UUID e2 = empresa(c);
        apiKey(e1, true);
        apiKey(e1, false);
        apiKey(e2, true);
        apiKey(empresa(cuenta()), true);

        assertThat(ocupados(c, RecursoDelPlan.API_KEYS)).as("revocar libera el lugar y se suman las dos empresas").isEqualTo(2);
    }

    @Test void losUsuariosSonLosActivosMasLasInvitacionesPendientesQueNoVencieron() {
        UUID c = cuenta();
        usuario(c, true);
        usuario(c, true);
        usuario(c, false);
        usuario(cuenta(), true);
        invitacion(c, "PENDIENTE", AHORA.plus(Duration.ofDays(3)));
        invitacion(c, "PENDIENTE", AHORA.minus(Duration.ofHours(1)));
        invitacion(c, "ACEPTADA", AHORA.plus(Duration.ofDays(3)));
        invitacion(c, "REVOCADA", AHORA.plus(Duration.ofDays(3)));

        assertThat(ocupados(c, RecursoDelPlan.USUARIOS)).as("2 activos + 1 invitación vigente; el inactivo, la vencida, la aceptada y la revocada no").isEqualTo(3);
    }

    @Test void unaInvitacionQueVenceJustoAhoraYaNoOcupaLugar() {
        UUID c = cuenta();
        invitacion(c, "PENDIENTE", AHORA);

        assertThat(ocupados(c, RecursoDelPlan.USUARIOS)).isZero();
    }

    @Test void losRucsSonLasEmpresasDeLaCuenta() {
        UUID c = cuenta();
        empresa(c);
        empresa(c);
        empresa(cuenta());

        assertThat(ocupados(c, RecursoDelPlan.RUCS)).isEqualTo(2);
    }

    @Test void unaCuentaVaciaOcupaCero() {
        assertThat(repo.uso(cuenta(), AHORA)).isEqualTo(new UsoDeRecursos(0, 0, 0));
    }

    @Test void usoJuntaLosTresRecursos() {
        UUID c = cuenta();
        UUID e = empresa(c);
        apiKey(e, true);
        usuario(c, true);
        invitacion(c, "PENDIENTE", AHORA.plus(Duration.ofDays(1)));

        assertThat(repo.uso(c, AHORA)).isEqualTo(new UsoDeRecursos(1, 2, 1));
    }

    @Test void bloquearFueraDeUnaTransaccionEsUnError() {
        assertThatThrownBy(() -> repo.bloquear(cuenta(), RecursoDelPlan.API_KEYS)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Dos altas a la vez de la misma cuenta se turnan: la que llega segundo espera el commit de la primera y ve su lugar ocupado. Sin el bloqueo las dos contarían 0 y
     * las dos entrarían en un plan de 1.
     */
    @Test void dosAltasSimultaneasDeLaMismaCuentaSeTurnan() throws Exception {
        UUID c = cuenta();
        UUID e = empresa(c);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> a = pool.submit(() -> altaConTope(c, e));
            Future<Long> b = pool.submit(() -> altaConTope(c, e));
            long vistoPorA = a.get(20, TimeUnit.SECONDS);
            long vistoPorB = b.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(vistoPorA, vistoPorB)).as("uno vio 0 y el otro vio la key del primero").containsExactlyInAnyOrder(0L, 1L);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Lo que hace el servicio: bloquear, contar y, si cabe, crear; todo en la misma transacción. */
    private long altaConTope(UUID cuenta, UUID empresa) {
        return uow.ejecutar(() -> {
            repo.bloquear(cuenta, RecursoDelPlan.API_KEYS);
            long vistas = repo.ocupados(cuenta, RecursoDelPlan.API_KEYS, AHORA);
            try { Thread.sleep(300); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            apiKey(empresa, true);
            return vistas;
        });
    }

    /** Un recurso no bloquea a otro, ni una cuenta a otra. */
    @Test void elBloqueoEsPorCuentaYPorRecurso() throws Exception {
        UUID c1 = cuenta();
        UUID c2 = cuenta();
        uow.ejecutar(() -> {
            repo.bloquear(c1, RecursoDelPlan.API_KEYS);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<?> otro = pool.submit(() -> uow.ejecutar(() -> {
                    repo.bloquear(c1, RecursoDelPlan.USUARIOS);
                    repo.bloquear(c2, RecursoDelPlan.API_KEYS);
                }));
                otro.get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new AssertionError("el bloqueo de otro recurso u otra cuenta no debe esperar", ex);
            } finally {
                pool.shutdownNow();
            }
        });
    }
}
