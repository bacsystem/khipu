package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** #611: la limpieza de sesiones borra lo vencido hace más que la retención, por lotes, y conserva la última sesión de cada usuario. */
class JdbcSesionPurgaTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-11-12T12:00:00Z");
    static final Instant CORTE = AHORA.minus(Duration.ofDays(7));

    JdbcSesionRepository repo = new JdbcSesionRepository(jdbc);

    UUID usuario() {
        UUID cuenta = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono) VALUES (?, 'Cuenta', ?, '987654321')", cuenta, cuenta + "@x.pe");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, cuenta_id, email, password_hash, rol) VALUES (?, ?, ?, 'hash', 'ADMIN')", id, cuenta, id + "@x.pe");
        return id;
    }

    UUID sesion(UUID usuario, Instant creada, Instant expira, boolean revocada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, revocada, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, usuario, id.toString().replace("-", ""), Timestamp.from(expira), revocada, Timestamp.from(creada));
        return id;
    }

    /** Una sesión de 30 días de vida que venció hace {@code diasVencida} días (respecto de {@link #AHORA}). */
    UUID vencidaHace(UUID usuario, long diasVencida, boolean revocada) {
        Instant expira = AHORA.minus(Duration.ofDays(diasVencida));
        return sesion(usuario, expira.minus(Duration.ofDays(30)), expira, revocada);
    }

    UUID vigente(UUID usuario, Instant creada) { return sesion(usuario, creada, AHORA.plus(Duration.ofDays(20)), false); }

    boolean existe(UUID sesion) { return jdbc.queryForObject("SELECT count(*) FROM sesion WHERE id = ?", Integer.class, sesion) == 1; }

    @Test void borraLasVencidasAntesDelCorteRevocadasOno() {
        UUID u = usuario();
        UUID reciente = vigente(u, AHORA.minusSeconds(60));
        UUID vencida = vencidaHace(u, 8, false);
        UUID revocada = vencidaHace(u, 9, true);

        int borradas = repo.purgarVencidas(CORTE, 1000);

        assertThat(borradas).isEqualTo(2);
        assertThat(existe(vencida)).isFalse();
        assertThat(existe(revocada)).isFalse();
        assertThat(existe(reciente)).isTrue();
    }

    @Test void noTocaLasVigentesNiLasVencidasDentroDelPlazo() {
        UUID u = usuario();
        UUID vigenteVieja = sesion(u, AHORA.minus(Duration.ofDays(10)), AHORA.plus(Duration.ofDays(20)), false);
        UUID vigenteRevocada = sesion(u, AHORA.minus(Duration.ofDays(11)), AHORA.plus(Duration.ofDays(19)), true);
        UUID vencidaDentroDelPlazo = vencidaHace(u, 6, false);
        UUID vencidaRevocadaDentroDelPlazo = vencidaHace(u, 3, true);
        UUID ultima = vigente(u, AHORA.minusSeconds(30));

        assertThat(repo.purgarVencidas(CORTE, 1000)).isZero();

        for (UUID s : List.of(vigenteVieja, vigenteRevocada, vencidaDentroDelPlazo, vencidaRevocadaDentroDelPlazo, ultima))
            assertThat(existe(s)).isTrue();
    }

    /** El backoffice deduce el último acceso de `max(created_at)`: un cliente inactivo no puede pasar a «nunca inició sesión». */
    @Test void conservaLaMasRecienteDeCadaUsuarioAunqueEsteVencida() {
        UUID inactivo = usuario(), activo = usuario();
        UUID unicaDelInactivo = vencidaHace(inactivo, 40, false);
        UUID anteriorDelInactivo = sesion(inactivo, AHORA.minus(Duration.ofDays(80)), AHORA.minus(Duration.ofDays(50)), true);
        UUID viejaDelActivo = vencidaHace(activo, 20, true);
        UUID ultimaDelActivo = vigente(activo, AHORA.minusSeconds(5));
        Instant ultimoAcceso = jdbc.queryForObject("SELECT max(created_at) FROM sesion WHERE usuario_id = ?", Timestamp.class, inactivo).toInstant();

        int borradas = repo.purgarVencidas(CORTE, 1000);

        assertThat(borradas).isEqualTo(2);
        assertThat(existe(unicaDelInactivo)).as("la más reciente del inactivo, vencida hace 40 días").isTrue();
        assertThat(existe(anteriorDelInactivo)).isFalse();
        assertThat(existe(viejaDelActivo)).isFalse();
        assertThat(existe(ultimaDelActivo)).isTrue();
        assertThat(jdbc.queryForObject("SELECT max(created_at) FROM sesion WHERE usuario_id = ?", Timestamp.class, inactivo).toInstant())
                .as("el último acceso no cambia").isEqualTo(ultimoAcceso);
        assertThat(repo.purgarVencidas(CORTE, 1000)).as("idempotente: la segunda pasada no encuentra nada").isZero();
    }

    @Test void unUsuarioConUnaSolaSesionVencidaLaConserva() {
        UUID u = usuario();
        UUID unica = vencidaHace(u, 100, true);

        assertThat(repo.purgarVencidas(CORTE, 1000)).isZero();
        assertThat(existe(unica)).isTrue();
    }

    @Test void borraComoMuchoUnLoteYEmpiezaPorLasMasViejas() {
        UUID u = usuario();
        UUID masVieja = vencidaHace(u, 30, false), media = vencidaHace(u, 20, false), nueva = vencidaHace(u, 10, false);
        UUID ultima = vigente(u, AHORA.minusSeconds(1));

        assertThat(repo.purgarVencidas(CORTE, 2)).isEqualTo(2);
        assertThat(existe(masVieja)).isFalse();
        assertThat(existe(media)).isFalse();
        assertThat(existe(nueva)).isTrue();

        assertThat(repo.purgarVencidas(CORTE, 2)).isEqualTo(1);
        assertThat(existe(nueva)).isFalse();
        assertThat(existe(ultima)).isTrue();
    }

    /** Con dos transacciones abiertas a la vez, cada una toma filas distintas: sin SKIP LOCKED la segunda esperaría a la primera. */
    @Test void dosTransaccionesAbiertasALaVezBorranFilasDistintas() throws Exception {
        UUID u = usuario();
        for (int i = 0; i < 6; i++) vencidaHace(u, 20 + i, false);
        vigente(u, AHORA.minusSeconds(1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch primeraBorro = new CountDownLatch(1), segundaTermino = new CountDownLatch(1);
            // La primera borra 4 y sigue con la transacción abierta (filas bloqueadas) hasta que la segunda terminó.
            Future<Integer> primera = pool.submit(() -> uow.ejecutar(() -> {
                int mias = repo.purgarVencidas(CORTE, 4);
                primeraBorro.countDown();
                try { segundaTermino.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return mias;
            }));
            assertThat(primeraBorro.await(30, TimeUnit.SECONDS)).isTrue();
            int segunda;
            try {
                segunda = pool.submit(() -> uow.ejecutar(() -> repo.purgarVencidas(CORTE, 50))).get(20, TimeUnit.SECONDS);
            } finally {
                segundaTermino.countDown();
            }

            assertThat(primera.get(30, TimeUnit.SECONDS)).isEqualTo(4);
            assertThat(segunda).as("la segunda no esperó a la primera ni repitió sus filas").isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Sin este índice cada lote recorrería la tabla entera: una fila por cada refresh de cada usuario activo. */
    @Test void elBorradoRealUsaElIndiceDeVencimiento() {
        // EXPLAIN de la sentencia que ejecuta el repositorio, con recorridos completos prohibidos: con las pocas filas de una prueba el planificador
        // preferiría recorrer la tabla aunque el índice sirva, y lo que se comprueba es que sirve.
        String plan = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) con -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET enable_seqscan = off");
            }
            try (java.sql.PreparedStatement ps = con.prepareStatement("EXPLAIN " + JdbcSesionRepository.PURGAR_VENCIDAS)) {
                ps.setTimestamp(1, Timestamp.from(CORTE));
                ps.setInt(2, 1000);
                StringBuilder texto = new StringBuilder();
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) texto.append(rs.getString(1)).append('\n');
                }
                return texto.toString();
            }
        });

        assertThat(plan).contains("ix_sesion_expira");
    }
}
