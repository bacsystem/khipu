package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** #498: el rechazo dice cuánto es, qué cuenta y qué hacer; la cantidad concuerda en número. */
class RecursoDelPlanTest {
    @Test void laCantidadConcuerdaEnNumero() {
        assertThat(RecursoDelPlan.API_KEYS.cantidad(1)).isEqualTo("1 API key");
        assertThat(RecursoDelPlan.API_KEYS.cantidad(2)).isEqualTo("2 API keys");
        assertThat(RecursoDelPlan.USUARIOS.cantidad(1)).isEqualTo("1 usuario");
        assertThat(RecursoDelPlan.USUARIOS.cantidad(0)).isEqualTo("0 usuarios");
        assertThat(RecursoDelPlan.RUCS.cantidad(3)).isEqualTo("3 RUCs");
    }

    @Test void cadaRecursoDiceComoLiberarUnLugar() {
        for (RecursoDelPlan r : RecursoDelPlan.values()) assertThat(r.paraLiberar()).as(r.name()).contains("plan");
        assertThat(RecursoDelPlan.API_KEYS.paraLiberar()).contains("Revoca");
        assertThat(RecursoDelPlan.USUARIOS.paraLiberar()).contains("Desactiva").contains("invitación");
    }

    @Test void losUsuariosAclaranQueLasInvitacionesPendientesCuentan() {
        assertThat(RecursoDelPlan.USUARIOS.loQueCuenta(2)).contains("invitación pendiente");
    }

    /** F1: «1 API key activas» era un error de concordancia en el mensaje que ve el cliente al primer rechazo. */
    @Test void loQueCuentaConcuerdaConLaCantidad() {
        assertThat(RecursoDelPlan.API_KEYS.cantidad(1) + RecursoDelPlan.API_KEYS.loQueCuenta(1)).isEqualTo("1 API key activa");
        assertThat(RecursoDelPlan.API_KEYS.cantidad(2) + RecursoDelPlan.API_KEYS.loQueCuenta(2)).isEqualTo("2 API keys activas");
        assertThat(RecursoDelPlan.USUARIOS.cantidad(1) + RecursoDelPlan.USUARIOS.loQueCuenta(1)).isEqualTo("1 usuario (activo o con invitación pendiente)");
        assertThat(RecursoDelPlan.RUCS.loQueCuenta(1)).isEmpty();
    }
}
