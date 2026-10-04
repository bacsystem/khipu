package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {
    static Plan plan(String nombre, String precio, int rucs, int retencion, EstadoPlan estado, boolean porDefecto) {
        return new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), Limite.de(300), rucs, Limite.de(1), Limite.de(2), retencion, estado, porDefecto);
    }

    static Plan plan() { return plan("Emprende", "29", 1, 5, EstadoPlan.ACTIVO, false); }

    static String codigo(Runnable r) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(DomainException.class, r::run).codigo();
    }

    @Test void unPlanValidoGuardaSusDatos() {
        Plan p = plan();

        assertThat(p.nombre()).isEqualTo("Emprende");
        assertThat(p.precioMensual()).isEqualByComparingTo("29.00");
        assertThat(p.rucs()).isEqualTo(1);
        assertThat(p.retencionAnios()).isEqualTo(5);
        assertThat(p.activo()).isTrue();
    }

    @Test void elNombreSeRecortaYEsObligatorio() {
        assertThat(plan("  Negocio  ", "69", 3, 5, EstadoPlan.ACTIVO, false).nombre()).isEqualTo("Negocio");
        assertThat(codigo(() -> plan(" ", "69", 3, 5, EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_REQUERIDO");
        assertThat(codigo(() -> plan(null, "69", 3, 5, EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_REQUERIDO");
    }

    @Test void elNombreTieneUnTope() {
        assertThat(plan("x".repeat(40), "1", 1, 1, EstadoPlan.ACTIVO, false).nombre()).hasSize(40);
        assertThat(codigo(() -> plan("x".repeat(41), "1", 1, 1, EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_INVALIDO");
    }

    @Test void gratisCuestaCeroPeroNoPuedeSerNegativo() {
        assertThat(plan("Gratis", "0", 1, 1, EstadoPlan.ACTIVO, true).precioMensual()).isEqualByComparingTo("0");
        assertThat(codigo(() -> plan("Malo", "-0.01", 1, 1, EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
    }

    /** Un precio con tres decimales no existe en soles: se rechaza en vez de redondearlo en silencio. */
    @Test void elPrecioTieneComoMuchoDosDecimales() {
        assertThat(plan("Medio", "29.5", 1, 1, EstadoPlan.ACTIVO, false).precioMensual()).isEqualByComparingTo("29.50");
        assertThat(codigo(() -> plan("Raro", "29.999", 1, 1, EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
    }

    /** El precio siempre lleva dos decimales: dos planes de «29» y «29.00» son el mismo y se comparan (y se muestran) igual. */
    @Test void elPrecioSeNormalizaADosDecimales() {
        assertThat(plan("A", "29", 1, 1, EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("29.00");
        assertThat(plan("B", "29.5", 1, 1, EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("29.50");
        assertThat(plan("C", "1E+2", 1, 1, EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("100.00");
    }

    @Test void sinPrecioNoHayPlan() {
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", null, Limite.de(1), 1, Limite.de(1), Limite.de(1), 1, EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
    }

    @Test void losRucYLaRetencionSonPositivos() {
        assertThat(codigo(() -> plan("X", "1", 0, 1, EstadoPlan.ACTIVO, false))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> plan("X", "1", 1, 0, EstadoPlan.ACTIVO, false))).isEqualTo("RETENCION_INVALIDA");
    }

    @Test void losLimitesYElEstadoSonObligatorios() {
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, null, 1, Limite.de(1), Limite.de(1), 1, EstadoPlan.ACTIVO, false))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, Limite.de(1), 1, null, Limite.de(1), 1, EstadoPlan.ACTIVO, false))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, Limite.de(1), 1, Limite.de(1), null, 1, EstadoPlan.ACTIVO, false))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, Limite.de(1), 1, Limite.de(1), Limite.de(1), 1, null, false))).isEqualTo("ESTADO_INVALIDO");
    }

    @Test void unPlanDesactivadoNoEstaActivo() {
        assertThat(plan("Viejo", "10", 1, 1, EstadoPlan.INACTIVO, false).activo()).isFalse();
    }

    @Test void desactivarUnPlanLoSacaDeLaOferta() {
        assertThat(plan().desactivar().activo()).isFalse();
    }

    /** Las cuentas nuevas nacen con el plan por defecto: sin él una cuenta quedaría sin plan. */
    @Test void elPlanPorDefectoNoSePuedeDesactivarNiNacerInactivo() {
        Plan base = plan("Gratis", "0", 1, 1, EstadoPlan.ACTIVO, true);

        assertThat(codigo(base::desactivar)).isEqualTo("PLAN_POR_DEFECTO");
        assertThat(codigo(() -> plan("Gratis", "0", 1, 1, EstadoPlan.INACTIVO, true))).isEqualTo("PLAN_POR_DEFECTO");
    }
}
