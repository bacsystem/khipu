package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlanTest {
    /** 15 de octubre de 2026, 10:00 en Lima. El ciclo siguiente empieza el 1 de noviembre a las 00:00 de Lima (05:00Z). */
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final Instant PROXIMO_CICLO = Instant.parse("2026-11-01T05:00:00Z");

    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);

    static Plan plan(String nombre, String precio, EstadoPlan estado, boolean porDefecto) {
        return new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), LIMITES, estado, porDefecto);
    }

    static Plan plan() { return plan("Emprende", "29", EstadoPlan.ACTIVO, false); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    @Test void unPlanValidoGuardaSusDatos() {
        Plan p = plan();

        assertThat(p.nombre()).isEqualTo("Emprende");
        assertThat(p.precioMensual()).isEqualByComparingTo("29.00");
        assertThat(p.limites()).isEqualTo(LIMITES);
        assertThat(p.programado()).isNull();
        assertThat(p.activo()).isTrue();
    }

    @Test void elNombreSeRecortaYEsObligatorio() {
        assertThat(plan("  Negocio  ", "69", EstadoPlan.ACTIVO, false).nombre()).isEqualTo("Negocio");
        assertThat(codigo(() -> plan(" ", "69", EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_REQUERIDO");
        assertThat(codigo(() -> plan(null, "69", EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_REQUERIDO");
    }

    @Test void elNombreTieneUnTope() {
        assertThat(plan("x".repeat(40), "1", EstadoPlan.ACTIVO, false).nombre()).hasSize(40);
        assertThat(codigo(() -> plan("x".repeat(41), "1", EstadoPlan.ACTIVO, false))).isEqualTo("NOMBRE_INVALIDO");
    }

    @Test void gratisCuestaCeroPeroNoPuedeSerNegativo() {
        assertThat(plan("Gratis", "0", EstadoPlan.ACTIVO, true).precioMensual()).isEqualByComparingTo("0");
        assertThat(codigo(() -> plan("Malo", "-0.01", EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
    }

    /** Un precio con tres decimales no existe en soles: se rechaza en vez de redondearlo en silencio. */
    @Test void elPrecioTieneComoMuchoDosDecimales() {
        assertThat(plan("Medio", "29.5", EstadoPlan.ACTIVO, false).precioMensual()).isEqualByComparingTo("29.50");
        assertThat(codigo(() -> plan("Raro", "29.999", EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
    }

    /** El precio siempre lleva dos decimales: dos planes de «29» y «29.00» son el mismo y se comparan (y se muestran) igual. */
    @Test void elPrecioSeNormalizaADosDecimales() {
        assertThat(plan("A", "29", EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("29.00");
        assertThat(plan("B", "29.5", EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("29.50");
        assertThat(plan("C", "1E+2", EstadoPlan.ACTIVO, false).precioMensual().toPlainString()).isEqualTo("100.00");
    }

    @Test void sinPrecioLimitesOEstadoNoHayPlan() {
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", null, LIMITES, EstadoPlan.ACTIVO, false))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, null, EstadoPlan.ACTIVO, false))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Plan(UUID.randomUUID(), "X", BigDecimal.ONE, LIMITES, null, false))).isEqualTo("ESTADO_INVALIDO");
    }

    @Test void sinIdNoHayPlan() {
        assertThat(codigo(() -> new Plan(null, "X", BigDecimal.ONE, LIMITES, EstadoPlan.ACTIVO, false))).isEqualTo("PLAN_INVALIDO");
    }

    @Test void unPlanDesactivadoNoEstaActivo() {
        assertThat(plan("Viejo", "10", EstadoPlan.INACTIVO, false).activo()).isFalse();
    }

    @Test void desactivarUnPlanLoSacaDeLaOferta() {
        Plan p = plan();

        Plan inactivo = p.desactivar();

        assertThat(inactivo.activo()).isFalse();
        assertThat(inactivo.id()).isEqualTo(p.id());
        assertThat(inactivo.limites()).isEqualTo(p.limites());
        assertThat(p.activo()).isTrue();
    }

    @Test void activarLoDevuelveALaOferta() {
        assertThat(plan("Viejo", "10", EstadoPlan.INACTIVO, false).activar().activo()).isTrue();
    }

    @Test void noSeDesactivaUnoInactivoNiSeActivaUnoActivo() {
        assertThat(codigo(() -> plan("Viejo", "10", EstadoPlan.INACTIVO, false).desactivar())).isEqualTo("PLAN_YA_INACTIVO");
        assertThat(codigo(() -> plan().activar())).isEqualTo("PLAN_YA_ACTIVO");
    }

    /** Las cuentas nuevas nacen con el plan por defecto: sin él una cuenta quedaría sin plan. */
    @Test void elPlanPorDefectoNoSePuedeDesactivarNiNacerInactivo() {
        Plan base = plan("Gratis", "0", EstadoPlan.ACTIVO, true);

        assertThat(codigo(base::desactivar)).isEqualTo("PLAN_POR_DEFECTO");
        assertThat(codigo(() -> plan("Gratis", "0", EstadoPlan.INACTIVO, true))).isEqualTo("PLAN_POR_DEFECTO");
    }

    // --- cambios de límites: al ciclo siguiente (#190) ------------------------------------------------------------------------------------------

    static Limites masDocumentos() { return new Limites(Limite.de(500), 1, Limite.de(1), Limite.de(2), 5); }

    /** Subir un límite a mitad de mes no regala documentos del ciclo en curso: el límite de ahora no se mueve. */
    @Test void editarLosLimitesLosProgramaParaElCicloSiguiente() {
        Plan p = plan();

        Plan editado = p.editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);

        assertThat(editado.limites()).isEqualTo(LIMITES);
        assertThat(editado.programado()).isEqualTo(new CambioDeLimites(masDocumentos(), PROXIMO_CICLO));
    }

    /** Y bajarlo tampoco le corta a nadie a mitad de mes. */
    @Test void bajarUnLimiteTambienEsParaElCicloSiguiente() {
        Limites menos = new Limites(Limite.de(100), 1, Limite.de(1), Limite.de(2), 5);

        Plan editado = plan().editar("Emprende", new BigDecimal("29"), menos, AHORA);

        assertThat(editado.limites().documentosAlMes()).isEqualTo(Limite.de(300));
        assertThat(editado.programado().limites().documentosAlMes()).isEqualTo(Limite.de(100));
    }

    @Test void elNombreElPrecioSeCambianAlInstante() {
        Plan editado = plan().editar("Emprende Plus", new BigDecimal("35.50"), LIMITES, AHORA);

        assertThat(editado.nombre()).isEqualTo("Emprende Plus");
        assertThat(editado.precioMensual()).isEqualByComparingTo("35.50");
        assertThat(editado.programado()).isNull();
    }

    @Test void editarSinCambiarLosLimitesNoProgramaNada() {
        assertThat(plan().editar("Otro", new BigDecimal("30"), LIMITES, AHORA).programado()).isNull();
    }

    /** Volver a poner los límites de ahora cancela el cambio que estaba programado. */
    @Test void ponerLosLimitesActualesCancelaElCambioProgramado() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);

        Plan cancelado = conCambio.editar("Emprende", new BigDecimal("29"), LIMITES, AHORA.plusSeconds(60));

        assertThat(cancelado.programado()).isNull();
        assertThat(cancelado.limites()).isEqualTo(LIMITES);
    }

    /** Un segundo cambio antes de que llegue el ciclo reemplaza al primero: nunca hay dos pendientes. */
    @Test void unSegundoCambioReemplazaAlProgramado() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);
        Limites otros = new Limites(Limite.sinLimite(), 1, Limite.de(1), Limite.de(2), 5);

        Plan segundo = conCambio.editar("Emprende", new BigDecimal("29"), otros, AHORA.plusSeconds(60));

        assertThat(segundo.programado().limites()).isEqualTo(otros);
        assertThat(segundo.limites()).isEqualTo(LIMITES);
    }

    @Test void editarConDatosInvalidosNoCambiaElOriginal() {
        Plan p = plan();

        assertThat(codigo(() -> p.editar(" ", new BigDecimal("29"), LIMITES, AHORA))).isEqualTo("NOMBRE_REQUERIDO");
        assertThat(codigo(() -> p.editar("X", new BigDecimal("-1"), LIMITES, AHORA))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(() -> p.editar("X", new BigDecimal("1"), null, AHORA))).isEqualTo("LIMITE_INVALIDO");
        assertThat(p.nombre()).isEqualTo("Emprende");
    }

    @Test void editarConservaElEstadoYSiEsElPorDefecto() {
        Plan base = plan("Gratis", "0", EstadoPlan.ACTIVO, true);
        Plan inactivo = plan("Viejo", "10", EstadoPlan.INACTIVO, false);

        assertThat(base.editar("Gratis", BigDecimal.ZERO, LIMITES, AHORA).porDefecto()).isTrue();
        assertThat(inactivo.editar("Viejo", BigDecimal.TEN, LIMITES, AHORA).activo()).isFalse();
    }

    /** Sacar un plan de la oferta (o devolverlo) no cancela el cambio de límites que ya estaba decidido. */
    @Test void desactivarYActivarConservanElCambioProgramado() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);

        Plan inactivo = conCambio.desactivar();

        assertThat(inactivo.programado()).isEqualTo(conCambio.programado());
        assertThat(inactivo.activar().programado()).isEqualTo(conCambio.programado());
    }

    @Test void antesDelCicloSiguienteElPlanVigenteSigueConLosLimitesDeAhora() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);

        Plan vigente = conCambio.vigenteEn(PROXIMO_CICLO.minusMillis(1));

        assertThat(vigente.limites()).isEqualTo(LIMITES);
        assertThat(vigente.programado()).isNotNull();
    }

    /** En el instante exacto de inicio del ciclo el cambio ya manda. */
    @Test void llegadoElCicloSiguienteElCambioPasaAVigente() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);

        Plan vigente = conCambio.vigenteEn(PROXIMO_CICLO);

        assertThat(vigente.limites()).isEqualTo(masDocumentos());
        assertThat(vigente.programado()).isNull();
        assertThat(vigente.nombre()).isEqualTo(conCambio.nombre());
        assertThat(vigente.id()).isEqualTo(conCambio.id());
    }

    @Test void sinCambioProgramadoElPlanVigenteEsElMismo() {
        Plan p = plan();

        assertThat(p.vigenteEn(PROXIMO_CICLO.plusSeconds(999_999))).isEqualTo(p);
    }

    /** Editar después de que un cambio ya entró en vigor parte de los límites nuevos, no de los viejos: no «vuelve» a programarlos ni los cancela por error. */
    @Test void editarParteDeLosLimitesQueYaEstanEnVigor() {
        Plan conCambio = plan().editar("Emprende", new BigDecimal("29"), masDocumentos(), AHORA);
        Instant despues = PROXIMO_CICLO.plusSeconds(3600);

        Plan sinTocarLimites = conCambio.editar("Emprende", new BigDecimal("40"), masDocumentos(), despues);

        assertThat(sinTocarLimites.limites()).isEqualTo(masDocumentos());
        assertThat(sinTocarLimites.programado()).isNull();
    }

    @Test void unCambioProgramadoNecesitaSusDatos() {
        assertThat(codigo(() -> new CambioDeLimites(null, PROXIMO_CICLO))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new CambioDeLimites(LIMITES, null))).isEqualTo("CAMBIO_INVALIDO");
    }
}
