package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.factura.domain.plan.MedioDePago;
import pe.factura.domain.plan.Pago;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los pagos registrados a mano (#194), con Postgres real: lo que se guarda vuelve igual, el historial sale del más reciente al más antiguo sin que dos páginas se pisen,
 * el mismo apunte repetido no se guarda dos veces y la base rechaza lo que el dominio ya rechaza.
 */
class JdbcPagoRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    JdbcPagoRepository repo = new JdbcPagoRepository(jdbc);
    JdbcSuscripcionRepository suscripciones = new JdbcSuscripcionRepository(jdbc);

    UUID cuenta(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(T0));
        return id;
    }

    UUID suscripcionDe(UUID cuenta) { return suscripciones.deLaCuenta(cuenta).orElseThrow().activa().id(); }

    Pago pago(UUID cuenta, LocalDate fecha, Instant registrado, String monto, String referencia) {
        return Pago.registrar(UUID.randomUUID(), cuenta, suscripcionDe(cuenta), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal(monto), MedioDePago.YAPE, fecha, referencia,
                "una nota", registrado, null);
    }

    Pago pago(UUID cuenta, String referencia) { return pago(cuenta, LocalDate.of(2026, 10, 14), T0.plusSeconds(60), "29.00", referencia); }

    // --- guardar y leer ---------------------------------------------------------------------------------------------------------------------

    @Test void unPagoGuardadoVuelveIgualConTodosSusDatos() {
        UUID c = cuenta("ana@negocio.pe");
        Pago p = Pago.registrar(UUID.randomUUID(), c, suscripcionDe(c), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("29.50"), MedioDePago.TRANSFERENCIA,
                LocalDate.of(2026, 10, 14), "OP-9", "Pagó por el BCP", T0.plusSeconds(60), Instant.parse("2026-11-01T05:00:00Z"));

        assertThat(repo.registrar(p)).isTrue();

        assertThat(repo.deLaCuenta(c, 1, 10)).containsExactly(p);
    }

    @Test void sinReferenciaNiNotaNiExtensionVuelveConNulos() {
        UUID c = cuenta("ana@negocio.pe");
        Pago p = Pago.registrar(UUID.randomUUID(), c, suscripcionDe(c), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("10"), MedioDePago.EFECTIVO,
                LocalDate.of(2026, 10, 14), null, null, T0.plusSeconds(60), null);
        repo.registrar(p);

        Pago leido = repo.deLaCuenta(c, 1, 10).get(0);

        assertThat(leido.referencia()).isNull();
        assertThat(leido.nota()).isNull();
        assertThat(leido.extendioHasta()).isNull();
        assertThat(leido.monto().toPlainString()).isEqualTo("10.00");
    }

    @Test void cadaMedioSeGuardaYVuelve() {
        UUID c = cuenta("ana@negocio.pe");
        for (MedioDePago medio : MedioDePago.values()) {
            Pago p = Pago.registrar(UUID.randomUUID(), c, suscripcionDe(c), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, medio, LocalDate.of(2026, 10, 14), medio.name(), null,
                    T0.plusSeconds(60), null);
            assertThat(repo.registrar(p)).as(medio.name()).isTrue();
        }

        assertThat(repo.deLaCuenta(c, 1, 20)).extracting(Pago::medio).containsExactlyInAnyOrder(MedioDePago.values());
    }

    @Test void losPagosDeUnaCuentaNoSalenEnLaDeOtra() {
        UUID a = cuenta("ana@negocio.pe");
        UUID b = cuenta("beto@negocio.pe");
        repo.registrar(pago(a, "A-1"));
        repo.registrar(pago(b, "B-1"));
        repo.registrar(pago(b, "B-2"));

        assertThat(repo.deLaCuenta(a, 1, 10)).hasSize(1);
        assertThat(repo.deLaCuenta(b, 1, 10)).hasSize(2);
        assertThat(repo.contarDeLaCuenta(a)).isEqualTo(1);
        assertThat(repo.contarDeLaCuenta(b)).isEqualTo(2);
        assertThat(repo.contarDeLaCuenta(UUID.randomUUID())).isZero();
    }

    // --- el orden y las páginas -------------------------------------------------------------------------------------------------------------

    @Test void elHistorialVaDelMasRecienteAlMasAntiguoPorFechaDePagoYLuegoPorRegistro() {
        UUID c = cuenta("ana@negocio.pe");
        Pago viejo = pago(c, LocalDate.of(2026, 8, 5), T0.plusSeconds(500), "1", "v");
        Pago medio1 = pago(c, LocalDate.of(2026, 9, 5), T0.plusSeconds(100), "2", "m1");
        Pago medio2 = pago(c, LocalDate.of(2026, 9, 5), T0.plusSeconds(200), "3", "m2");
        Pago nuevo = pago(c, LocalDate.of(2026, 10, 5), T0.plusSeconds(1), "4", "n");
        for (Pago p : List.of(viejo, nuevo, medio1, medio2)) repo.registrar(p);

        assertThat(repo.deLaCuenta(c, 1, 10)).containsExactly(nuevo, medio2, medio1, viejo);
    }

    @Test void conTodoIgualGanaElIdMenor() {
        UUID c = cuenta("ana@negocio.pe");
        UUID s = suscripcionDe(c);
        Pago menor = new Pago(UUID.fromString("00000000-0000-4000-8000-000000000001"), c, s, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, MedioDePago.YAPE,
                LocalDate.of(2026, 10, 14), "a", null, T0, null);
        Pago mayor = new Pago(UUID.fromString("00000000-0000-4000-8000-000000000002"), c, s, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, MedioDePago.YAPE,
                LocalDate.of(2026, 10, 14), "b", null, T0, null);
        repo.registrar(mayor);
        repo.registrar(menor);

        assertThat(repo.deLaCuenta(c, 1, 10)).containsExactly(menor, mayor);
    }

    @Test void laPaginacionNoRepiteNiPierdePagosYElTotalEsElDeTodos() {
        UUID c = cuenta("ana@negocio.pe");
        for (int i = 1; i <= 5; i++) repo.registrar(pago(c, LocalDate.of(2026, 10, i), T0.plusSeconds(i), String.valueOf(i), "r" + i));

        List<Pago> p1 = repo.deLaCuenta(c, 1, 2);
        List<Pago> p2 = repo.deLaCuenta(c, 2, 2);
        List<Pago> p3 = repo.deLaCuenta(c, 3, 2);

        assertThat(p1).extracting(p -> p.fechaDePago().getDayOfMonth()).containsExactly(5, 4);
        assertThat(p2).extracting(p -> p.fechaDePago().getDayOfMonth()).containsExactly(3, 2);
        assertThat(p3).extracting(p -> p.fechaDePago().getDayOfMonth()).containsExactly(1);
        assertThat(repo.deLaCuenta(c, 4, 2)).isEmpty();
        assertThat(repo.contarDeLaCuenta(c)).isEqualTo(5);
    }

    // --- el mismo apunte repetido -----------------------------------------------------------------------------------------------------------

    @Test void elMismoApunteRepetidoNoSeGuardaDosVeces() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.registrar(pago(c, "OP-123"))).isTrue();
        assertThat(repo.registrar(pago(c, "OP-123"))).isFalse();

        assertThat(repo.contarDeLaCuenta(c)).isEqualTo(1);
    }

    @Test void laReferenciaRepetidaNoDistingueMayusculas() {
        UUID c = cuenta("ana@negocio.pe");
        repo.registrar(pago(c, "op-123"));

        assertThat(repo.registrar(pago(c, "OP-123"))).isFalse();
    }

    @Test void otraReferenciaOtroMedioOOtraCuentaSiSeGuardan() {
        UUID a = cuenta("ana@negocio.pe");
        UUID b = cuenta("beto@negocio.pe");
        repo.registrar(pago(a, "OP-123"));
        UUID s = suscripcionDe(a);
        Pago otroMedio = Pago.registrar(UUID.randomUUID(), a, s, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, MedioDePago.PLIN, LocalDate.of(2026, 10, 14), "OP-123", null, T0, null);

        assertThat(repo.registrar(pago(a, "OP-124"))).as("otra referencia").isTrue();
        assertThat(repo.registrar(otroMedio)).as("otro medio").isTrue();
        assertThat(repo.registrar(pago(b, "OP-123"))).as("otra cuenta").isTrue();
    }

    @Test void sinReferenciaNingunPagoSeConsideraRepetido() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.registrar(pago(c, (String) null))).isTrue();
        assertThat(repo.registrar(pago(c, (String) null))).isTrue();

        assertThat(repo.contarDeLaCuenta(c)).isEqualTo(2);
    }

    // --- lo que la base no deja pasar --------------------------------------------------------------------------------------------------------

    @Test void unPagoDeUnaCuentaOUnaSuscripcionQueNoExistenNoSeGuarda() {
        UUID c = cuenta("ana@negocio.pe");
        Pago sinCuenta = Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), suscripcionDe(c), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, MedioDePago.YAPE,
                LocalDate.of(2026, 10, 14), null, null, T0, null);
        Pago sinSuscripcion = Pago.registrar(UUID.randomUUID(), c, UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BigDecimal.TEN, MedioDePago.YAPE,
                LocalDate.of(2026, 10, 14), null, null, T0, null);

        assertThatThrownBy(() -> repo.registrar(sinCuenta)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repo.registrar(sinSuscripcion)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void laBaseRechazaUnMontoNoPositivoUnPeriodoAlReves_UnPeriodoDeMasDeUnAnioYUnMedioDesconocido() {
        UUID c = cuenta("ana@negocio.pe");
        UUID s = suscripcionDe(c);
        String sql = "INSERT INTO pago (id, cuenta_id, suscripcion_id, periodo_desde, periodo_hasta, monto, medio, fecha_de_pago, registrado_en) VALUES (gen_random_uuid(), ?, ?, ?::date, ?::date, ?, ?, '2026-10-14', now())";

        assertThatThrownBy(() -> jdbc.update(sql, c, s, "2026-10-01", "2026-10-31", 0, "YAPE")).as("monto cero").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, c, s, "2026-10-01", "2026-10-31", -5, "YAPE")).as("monto negativo").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, c, s, "2026-10-31", "2026-10-01", 5, "YAPE")).as("periodo al revés").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, c, s, "2026-10-01", "2027-10-01", 5, "YAPE")).as("más de un año").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, c, s, "2026-10-01", "2026-10-31", 5, "BITCOIN")).as("medio").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(sql, c, s, "2026-10-01", "2027-09-30", 5, "YAPE")).as("un año justo sí").isEqualTo(1);
        assertThat(jdbc.update(sql, c, s, "2026-10-01", "2026-10-01", 5, "YAPE")).as("un solo día sí").isEqualTo(1);
    }

    // --- extender el vencimiento de la suscripción ------------------------------------------------------------------------------------------

    UUID cuentaConPlanDePago(String email, Instant vence) {
        UUID c = cuenta(email);
        UUID plan = jdbc.queryForObject("SELECT id FROM plan WHERE nombre = 'Emprende'", UUID.class);
        jdbc.update("UPDATE suscripcion SET plan_id = ?, vence_en = ?, dias_de_gracia = 3 WHERE cuenta_id = ? AND termina_en IS NULL", plan, Timestamp.from(vence), c);
        return c;
    }

    @Test void extenderMueveElVencimientoDeLaActivaYNadaMas() {
        Instant vence = T0.plus(Duration.ofDays(10));
        UUID c = cuentaConPlanDePago("ana@negocio.pe", vence);
        var antes = suscripciones.deLaCuenta(c).orElseThrow().activa();
        Instant nuevo = vence.plus(Duration.ofDays(30));

        assertThat(suscripciones.extenderVencimiento(antes.id(), vence, nuevo)).isTrue();

        var despues = suscripciones.deLaCuenta(c).orElseThrow().activa();
        assertThat(despues.venceEn()).isEqualTo(nuevo);
        assertThat(despues.id()).isEqualTo(antes.id());
        assertThat(despues.planId()).isEqualTo(antes.planId());
        assertThat(despues.diasDeGracia()).isEqualTo(3);
        assertThat(despues.iniciaEn()).isEqualTo(antes.iniciaEn());
        assertThat(despues.terminaEn()).isNull();
    }

    @Test void siElVencimientoYaNoEsElQueSeVioNoSeCambiaNada() {
        Instant vence = T0.plus(Duration.ofDays(10));
        UUID c = cuentaConPlanDePago("ana@negocio.pe", vence);
        UUID s = suscripcionDe(c);

        assertThat(suscripciones.extenderVencimiento(s, vence.plusSeconds(1), vence.plus(Duration.ofDays(30)))).isFalse();

        assertThat(suscripciones.deLaCuenta(c).orElseThrow().activa().venceEn()).isEqualTo(vence);
    }

    @Test void unaSuscripcionYaReemplazadaNoSeExtiende() {
        Instant vence = T0.plus(Duration.ofDays(10));
        UUID c = cuentaConPlanDePago("ana@negocio.pe", vence);
        UUID s = suscripcionDe(c);
        jdbc.update("UPDATE suscripcion SET termina_en = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(60)), s);

        assertThat(suscripciones.extenderVencimiento(s, vence, vence.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test void unPlanSinVencimientoNoSePuedeExtender() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(suscripciones.extenderVencimiento(suscripcionDe(c), T0, T0.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test void extenderLaSuscripcionDeUnaCuentaNoTocaLaDeOtra() {
        Instant vence = T0.plus(Duration.ofDays(10));
        UUID a = cuentaConPlanDePago("ana@negocio.pe", vence);
        UUID b = cuentaConPlanDePago("beto@negocio.pe", vence);

        suscripciones.extenderVencimiento(suscripcionDe(a), vence, vence.plus(Duration.ofDays(30)));

        assertThat(suscripciones.deLaCuenta(b).orElseThrow().activa().venceEn()).isEqualTo(vence);
    }
}
