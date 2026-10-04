package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que garantiza el esquema de planes y suscripciones (#189), con Postgres real: los cuatro planes de la página de precios, que toda cuenta nace con un plan
 * y que nunca puede haber dos suscripciones activas, sea cual sea el código que escriba.
 */
class EsquemaDePlanesTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    UUID cuenta(String email, Instant creada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(creada));
        return id;
    }

    UUID plan(String nombre) { return jdbc.queryForObject("SELECT id FROM plan WHERE nombre = ?", UUID.class, nombre); }

    void nuevoPlan(String nombre, String precio, Integer docs, int rucs, boolean porDefecto, String estado) {
        jdbc.update("INSERT INTO plan (nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, estado, por_defecto) VALUES (?, ?::numeric, ?, ?, 1, 1, 1, ?, ?)",
                nombre, precio, docs, rucs, estado, porDefecto);
    }

    @Test void estanLosCuatroPlanesDeLaPaginaDePrecios() {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, estado FROM plan ORDER BY precio_mensual");

        assertThat(filas).extracting(f -> f.get("nombre")).containsExactly("Gratis", "Emprende", "Negocio", "Pro");
        assertThat(filas).extracting(f -> f.get("precio_mensual").toString()).containsExactly("0.00", "29.00", "69.00", "129.00");
        assertThat(filas).extracting(f -> f.get("documentos_al_mes")).containsExactly(30, 300, 1500, null);
        assertThat(filas).extracting(f -> f.get("rucs")).containsExactly(1, 1, 3, 10);
        assertThat(filas).extracting(f -> f.get("usuarios")).containsExactly(1, 1, 3, null);
        assertThat(filas).extracting(f -> f.get("api_keys")).containsExactly(1, 2, 5, null);
        assertThat(filas).extracting(f -> f.get("retencion_anios")).containsExactly(1, 5, 5, 5);
        assertThat(filas).extracting(f -> f.get("estado")).containsOnly("ACTIVO");
    }

    @Test void gratisEsElPlanPorDefectoYElUnico() {
        assertThat(jdbc.queryForList("SELECT nombre FROM plan WHERE por_defecto", String.class)).containsExactly("Gratis");
    }

    @Test void unaCuentaNuevaNaceConElPlanGratisActivo() {
        UUID c = cuenta("ana@negocio.pe", T0);

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT plan_id, inicia_en, vence_en, dias_de_gracia, termina_en FROM suscripcion WHERE cuenta_id = ?", c);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).get("plan_id")).isEqualTo(plan("Gratis"));
        assertThat(((Timestamp) filas.get(0).get("inicia_en")).toInstant()).isEqualTo(T0);
        assertThat(filas.get(0).get("vence_en")).isNull();
        assertThat(filas.get(0).get("dias_de_gracia")).isEqualTo(0);
        assertThat(filas.get(0).get("termina_en")).isNull();
    }

    /** Cada cuenta recibe la suya: la del trigger no se comparte ni se pisa entre cuentas. */
    @Test void cadaCuentaNuevaTieneSuPropiaSuscripcion() {
        UUID a = cuenta("a@negocio.pe", T0);
        UUID b = cuenta("b@negocio.pe", T0.plusSeconds(5));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ?", Integer.class, b)).isEqualTo(1);
    }

    /** Actualizar una cuenta (el upsert del repositorio) no abre otra suscripción. */
    @Test void actualizarUnaCuentaNoCreaOtraSuscripcion() {
        UUID c = cuenta("ana@negocio.pe", T0);

        new JdbcCuentaRepository(jdbc).guardar(new pe.factura.domain.cuenta.Cuenta(c, "Otro nombre", "ana@negocio.pe", "987654321"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ?", Integer.class, c)).isEqualTo(1);
    }

    @Test void dosSuscripcionesActivasNoSePermiten() {
        UUID c = cuenta("ana@negocio.pe", T0);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en) VALUES (?, ?, ?)", c, plan("Pro"), Timestamp.from(T0.plusSeconds(1))))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test void cerradaLaActivaSePuedeAbrirOtra() {
        UUID c = cuenta("ana@negocio.pe", T0);

        jdbc.update("UPDATE suscripcion SET termina_en = ? WHERE cuenta_id = ?", Timestamp.from(T0.plusSeconds(10)), c);
        jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en) VALUES (?, ?, ?)", c, plan("Pro"), Timestamp.from(T0.plusSeconds(10)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", Integer.class, c)).isEqualTo(1);
    }

    @Test void dosCuentasPuedenTenerElMismoPlan() {
        cuenta("a@negocio.pe", T0);
        cuenta("b@negocio.pe", T0);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE plan_id = ? AND termina_en IS NULL", Integer.class, plan("Gratis"))).isEqualTo(2);
    }

    @Test void unaSuscripcionNoPuedeVencerAntesDeEmpezarNiTerminarAntes() {
        UUID c = cuenta("ana@negocio.pe", T0);
        jdbc.update("UPDATE suscripcion SET termina_en = ? WHERE cuenta_id = ?", Timestamp.from(T0), c);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en, vence_en) VALUES (?, ?, ?, ?)",
                c, plan("Pro"), Timestamp.from(T0), Timestamp.from(T0))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en, termina_en) VALUES (?, ?, ?, ?)",
                c, plan("Pro"), Timestamp.from(T0), Timestamp.from(T0.minusSeconds(1)))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en, dias_de_gracia) VALUES (?, ?, ?, -1)",
                c, plan("Pro"), Timestamp.from(T0))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void elNombreDelPlanEsUnicoSinImportarMayusculas() {
        assertThatThrownBy(() -> nuevoPlan("EMPRENDE", "10", 10, 1, false, "ACTIVO")).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> nuevoPlan("emprende", "10", 10, 1, false, "ACTIVO")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test void soloPuedeHaberUnPlanPorDefecto() {
        assertThatThrownBy(() -> nuevoPlan("Otro base", "0", 10, 1, true, "ACTIVO")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test void elPlanPorDefectoNoPuedeEstarInactivo() {
        assertThatThrownBy(() -> jdbc.update("UPDATE plan SET estado = 'INACTIVO' WHERE por_defecto")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void losLimitesYElPrecioSePiden() {
        assertThatThrownBy(() -> nuevoPlan("Cero", "10", 0, 1, false, "ACTIVO")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> nuevoPlan("SinRuc", "10", 10, 0, false, "ACTIVO")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> nuevoPlan("Negativo", "-1", 10, 1, false, "ACTIVO")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> nuevoPlan("Raro", "10", 10, 1, false, "SUSPENDIDO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    void cambioProgramado(Integer documentos, int rucs, Integer usuarios, Integer keys, int retencion) {
        jdbc.update("INSERT INTO plan_cambio_programado (plan_id, aplica_desde, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios) VALUES (?, ?, ?, ?, ?, ?, ?)",
                plan("Emprende"), Timestamp.from(T0), documentos, rucs, usuarios, keys, retencion);
    }

    /** El cambio programado respeta los mismos topes que el plan: mayores que cero o ilimitados, nunca cero. */
    @Test void elCambioProgramadoRespetaLosMismosTopes() {
        assertThatThrownBy(() -> cambioProgramado(0, 1, 1, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cambioProgramado(1, 0, 1, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cambioProgramado(1, 1, 0, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cambioProgramado(1, 1, 1, 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> cambioProgramado(1, 1, 1, 1, 0)).isInstanceOf(DataIntegrityViolationException.class);

        cambioProgramado(null, 1, null, null, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_cambio_programado", Integer.class)).isEqualTo(1);
        jdbc.update("DELETE FROM plan_cambio_programado");
    }

    @Test void unPlanTieneComoMuchoUnCambioProgramado() {
        cambioProgramado(5, 1, 1, 1, 1);

        assertThatThrownBy(() -> cambioProgramado(6, 1, 1, 1, 1)).isInstanceOf(DuplicateKeyException.class);
        jdbc.update("DELETE FROM plan_cambio_programado");
    }

    /** Sin plan por defecto una cuenta nueva no puede nacer: falla entera en vez de quedar sin plan. */
    @Test void sinPlanPorDefectoNoSePuedeCrearUnaCuenta() {
        jdbc.update("UPDATE plan SET por_defecto = FALSE WHERE nombre = 'Gratis'");
        try {
            assertThatThrownBy(() -> cuenta("ana@negocio.pe", T0)).hasMessageContaining("plan por defecto");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cuenta WHERE email = 'ana@negocio.pe'", Integer.class)).isZero();
        } finally {
            jdbc.update("UPDATE plan SET por_defecto = TRUE WHERE nombre = 'Gratis'");
        }
    }
}
