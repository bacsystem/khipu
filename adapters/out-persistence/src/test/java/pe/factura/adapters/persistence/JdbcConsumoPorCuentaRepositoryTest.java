package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.ConsumoPorCuentaRepository.Consulta;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.application.port.out.ConsumoPorCuentaRepository.Registro;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El consumo de todas las cuentas contra el límite de su plan (#193), con Postgres real. Cuenta como #192 (solo aceptados, por fecha de emisión, en el mes), y lo que se
 * prueba aquí es lo de la tabla: un plan por cuenta (el de hoy, con los límites que mandan hoy), el orden, los dos filtros con sus bordes exactos, las bajas fuera y la
 * paginación estable.
 */
class JdbcConsumoPorCuentaRepositoryTest extends PersistenciaTestBase {
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    static final LocalDate MITAD = LocalDate.of(2026, 10, 15);
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final AtomicLong RUC = new AtomicLong(500);
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcConsumoPorCuentaRepository repo = new JdbcConsumoPorCuentaRepository(jdbc);

    UUID cuenta(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', ?)", id, nombre, nombre.toLowerCase().replace(' ', '.') + "@negocio.pe", Timestamp.from(AHORA.minus(Duration.ofDays(60))));
        return id;
    }

    UUID empresa(UUID cuenta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, 'EMPRESA SAC', 'BETA', ?)", id, "20" + String.format("%09d", RUC.getAndIncrement()), cuenta);
        return id;
    }

    void aceptados(UUID empresa, int n) { documentos(empresa, "ACEPTADO", n, MITAD); }

    /** Una sola sentencia con `generate_series`: miles de INSERT sueltos, cada uno con su conexión, agotan los puertos. */
    void documentos(UUID empresa, String estado, int n, LocalDate fecha) {
        if (n == 0) return;
        long primero = NUMERO.getAndAdd(n);
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) SELECT gen_random_uuid(), ?, '01', 'F001', g, ?, ?, 'x' FROM generate_series(?::bigint, ?::bigint) g",
                empresa, java.sql.Date.valueOf(fecha), estado, primero, primero + n - 1);
    }

    UUID plan(String nombre) { return jdbc.queryForObject("SELECT id FROM plan WHERE nombre = ?", UUID.class, nombre); }

    /** Pasa la cuenta a ese plan: cierra la suscripción que tenía y abre otra, con el vencimiento y la gracia dados. */
    void enPlan(UUID cuenta, String plan, Instant vence, int gracia) {
        // Empezó hace 30 días, o antes si ya había vencido (el vencimiento tiene que ser posterior al inicio).
        Instant inicia = vence == null ? AHORA.minus(Duration.ofDays(30)) : vence.minus(Duration.ofDays(1)).isBefore(AHORA.minus(Duration.ofDays(30))) ? vence.minus(Duration.ofDays(1)) : AHORA.minus(Duration.ofDays(30));
        jdbc.update("UPDATE suscripcion SET termina_en = ? WHERE cuenta_id = ? AND termina_en IS NULL", Timestamp.from(inicia), cuenta);
        jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en, vence_en, dias_de_gracia) VALUES (?, ?, ?, ?, ?)",
                cuenta, plan(plan), Timestamp.from(inicia), vence == null ? null : Timestamp.from(vence), gracia);
    }

    Consulta consulta(FiltroDeConsumo f, OrdenDeConsumo o) { return new Consulta(OCTUBRE, AHORA, f, o, 80); }

    Consulta todas() { return consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE); }

    Registro de(List<Registro> filas, UUID cuenta) { return filas.stream().filter(r -> r.cuentaId().equals(cuenta)).findFirst().orElseThrow(); }

    // --- la fila ----------------------------------------------------------------------------------------------------------------------------

    @Test void cadaCuentaTraeSuPlanDeHoySuTopeYLoQueConsumioEnElMes() {
        UUID c = cuenta("Ana");
        enPlan(c, "Emprende", AHORA.plus(Duration.ofDays(20)), 5);
        aceptados(empresa(c), 120);

        Registro r = de(repo.listar(todas(), 1, 20), c);

        assertThat(r.nombre()).isEqualTo("Ana");
        assertThat(r.email()).isEqualTo("ana@negocio.pe");
        assertThat(r.planId()).isEqualTo(plan("Emprende"));
        assertThat(r.planNombre()).isEqualTo("Emprende");
        assertThat(r.documentos()).isEqualTo(120);
        assertThat(r.limite()).isEqualTo(300);
        assertThat(r.venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(20)));
        assertThat(r.diasDeGracia()).isEqualTo(5);
    }

    @Test void unaCuentaSinEmpresasApareceConCero() {
        UUID c = cuenta("Sin empresas");

        Registro r = de(repo.listar(todas(), 1, 20), c);

        assertThat(r.documentos()).isZero();
        assertThat(r.planNombre()).isEqualTo("Gratis");
        assertThat(r.limite()).isEqualTo(30);
        assertThat(r.venceEn()).isNull();
    }

    @Test void unPlanSinTopeDeDocumentosTraeLimiteNulo() {
        UUID c = cuenta("Ana");
        enPlan(c, "Pro", AHORA.plus(Duration.ofDays(20)), 0);

        assertThat(de(repo.listar(todas(), 1, 20), c).limite()).isNull();
    }

    @Test void sumaLasEmpresasDeLaCuentaYNoMezclaLasDeOtra() {
        UUID a = cuenta("Ana");
        UUID b = cuenta("Beto");
        aceptados(empresa(a), 10);
        aceptados(empresa(a), 5);
        aceptados(empresa(b), 7);

        List<Registro> filas = repo.listar(todas(), 1, 20);

        assertThat(de(filas, a).documentos()).isEqualTo(15);
        assertThat(de(filas, b).documentos()).isEqualTo(7);
    }

    @Test void siguenLasReglasDelContadorSoloAceptadosYSoloElMes() {
        UUID c = cuenta("Ana");
        UUID e = empresa(c);
        aceptados(e, 3);
        documentos(e, "ACEPTADO_CON_OBS", 2, MITAD);
        documentos(e, "ANULADO", 1, MITAD); // aceptado y luego dado de baja: ya consumió (#192)
        for (String no : List.of("RECHAZADO", "ERROR_ENVIO", "ENVIADO", "FUERA_DE_PLAZO")) documentos(e, no, 4, MITAD);
        documentos(e, "ACEPTADO", 6, LocalDate.of(2026, 9, 30));
        documentos(e, "ACEPTADO", 6, LocalDate.of(2026, 11, 1));

        assertThat(de(repo.listar(todas(), 1, 20), c).documentos()).isEqualTo(6);
    }

    /** Los bordes del mes son del mes: el día 1 y el último día cuentan; el día anterior y el siguiente, no. */
    @Test void elPrimeroYElUltimoDiaDelMesCuentanYSusVecinosNo() {
        UUID c = cuenta("Ana");
        UUID e = empresa(c);
        documentos(e, "ACEPTADO", 1, LocalDate.of(2026, 10, 1));
        documentos(e, "ACEPTADO", 10, LocalDate.of(2026, 10, 31));
        documentos(e, "ACEPTADO", 100, LocalDate.of(2026, 9, 30));
        documentos(e, "ACEPTADO", 1000, LocalDate.of(2026, 11, 1));

        assertThat(de(repo.listar(todas(), 1, 20), c).documentos()).isEqualTo(11);
    }

    @Test void soloCuentaLaSuscripcionVigenteNoElHistorial() {
        UUID c = cuenta("Ana");
        enPlan(c, "Negocio", AHORA.plus(Duration.ofDays(20)), 0);
        enPlan(c, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);

        List<Registro> filas = repo.listar(todas(), 1, 20);

        assertThat(filas.stream().filter(r -> r.cuentaId().equals(c))).hasSize(1);
        assertThat(de(filas, c).planNombre()).isEqualTo("Emprende");
    }

    // --- las bajas y las suspendidas --------------------------------------------------------------------------------------------------------

    @Test void unaCuentaDeBajaNoSaleEnNingunFiltro() {
        UUID c = cuenta("De baja");
        aceptados(empresa(c), 500);
        enPlan(c, "Emprende", AHORA.minus(Duration.ofDays(40)), 0);
        jdbc.update("UPDATE cuenta SET baja_en = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(1))), c);

        for (FiltroDeConsumo f : FiltroDeConsumo.values()) {
            assertThat(repo.listar(consulta(f, OrdenDeConsumo.PORCENTAJE), 1, 20)).as(f.name()).extracting(Registro::cuentaId).doesNotContain(c);
            assertThat(repo.todas(consulta(f, OrdenDeConsumo.PORCENTAJE))).extracting(Registro::cuentaId).doesNotContain(c);
        }
        assertThat(repo.contar(todas())).isZero();
    }

    @Test void unaCuentaSuspendidaSiSale() {
        UUID c = cuenta("Suspendida");
        jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(1))), c);

        assertThat(repo.listar(todas(), 1, 20)).extracting(Registro::cuentaId).contains(c);
    }

    // --- el límite que manda hoy ------------------------------------------------------------------------------------------------------------

    /** Un cambio de límites que todavía no llegó no se adelanta; uno que ya llegó, sí aunque nadie haya editado el plan. */
    @Test void elTopeEsElQueMandaHoyConLosCambiosDeLimitesYaLlegados() {
        UUID c = cuenta("Ana");
        enPlan(c, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        jdbc.update("INSERT INTO plan_cambio_programado (plan_id, aplica_desde, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios) VALUES (?, ?, 900, 1, 1, 2, 5)",
                plan("Emprende"), Timestamp.from(AHORA.plus(Duration.ofDays(5))));
        assertThat(de(repo.listar(todas(), 1, 20), c).limite()).as("todavía no llegó").isEqualTo(300);

        jdbc.update("UPDATE plan_cambio_programado SET aplica_desde = ?", Timestamp.from(AHORA));
        assertThat(de(repo.listar(todas(), 1, 20), c).limite()).as("en el instante exacto ya manda").isEqualTo(900);

        jdbc.update("UPDATE plan_cambio_programado SET aplica_desde = ?, documentos_al_mes = NULL", Timestamp.from(AHORA.minusSeconds(60)));
        assertThat(de(repo.listar(todas(), 1, 20), c).limite()).as("el programado puede quitar el tope").isNull();
        jdbc.update("DELETE FROM plan_cambio_programado");
    }

    @Test void elCambioProgramadoDeUnPlanNoTocaLasCuentasDeOtro() {
        UUID c = cuenta("Ana");
        enPlan(c, "Negocio", AHORA.plus(Duration.ofDays(20)), 0);
        jdbc.update("INSERT INTO plan_cambio_programado (plan_id, aplica_desde, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios) VALUES (?, ?, 5, 1, 1, 2, 5)",
                plan("Emprende"), Timestamp.from(AHORA.minusSeconds(60)));

        assertThat(de(repo.listar(todas(), 1, 20), c).limite()).isEqualTo(1500);
        jdbc.update("DELETE FROM plan_cambio_programado");
    }

    // --- el orden ---------------------------------------------------------------------------------------------------------------------------

    @Test void porPorcentajeVaDeMasAMenosYLosPlanesSinTopeAlFinal() {
        UUID baja = cuenta("Baja");        // Emprende: 30 de 300 = 10 %
        UUID alta = cuenta("Alta");        // Emprende: 270 de 300 = 90 %
        UUID gratisLlena = cuenta("Gratis llena"); // Gratis: 30 de 30 = 100 %
        UUID libre = cuenta("Libre");      // Pro: sin tope, 9999 documentos
        enPlan(baja, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        enPlan(alta, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        enPlan(libre, "Pro", AHORA.plus(Duration.ofDays(20)), 0);
        aceptados(empresa(baja), 30);
        aceptados(empresa(alta), 270);
        aceptados(empresa(gratisLlena), 30);
        aceptados(empresa(libre), 9999);

        List<Registro> filas = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE), 1, 20);

        assertThat(filas).extracting(Registro::cuentaId).containsExactly(gratisLlena, alta, baja, libre);
    }

    @Test void conElMismoPorcentajeGanaElQueConsumioMasYLuegoElNombre() {
        UUID chica = cuenta("Zeta chica");      // Gratis 15 de 30 = 50 %
        UUID grande = cuenta("Alfa grande");    // Emprende 150 de 300 = 50 %
        UUID igualA = cuenta("Beta igual");     // Emprende 150 de 300 = 50 %
        enPlan(grande, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        enPlan(igualA, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        aceptados(empresa(chica), 15);
        aceptados(empresa(grande), 150);
        aceptados(empresa(igualA), 150);

        List<Registro> filas = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE), 1, 20);

        assertThat(filas).extracting(Registro::nombre).containsExactly("Alfa grande", "Beta igual", "Zeta chica");
    }

    /** Con todo igual, el orden lo decide el id: así dos cuentas que se llaman igual no cambian de lugar entre una página y la siguiente. */
    @Test void conTodoIgualIncluidoElNombreGanaElIdMenorSinImportarElOrdenDeAlta() {
        UUID menor = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID mayor = UUID.fromString("00000000-0000-4000-8000-000000000002");
        for (UUID id : List.of(mayor, menor)) {
            jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Igual', ?, '987654321', ?)", id, id + "@negocio.pe", Timestamp.from(AHORA.minus(Duration.ofDays(60))));
        }

        for (OrdenDeConsumo orden : OrdenDeConsumo.values()) {
            assertThat(repo.listar(consulta(FiltroDeConsumo.TODAS, orden), 1, 20)).as(orden.name()).extracting(Registro::cuentaId).containsExactly(menor, mayor);
        }
    }

    @Test void porDocumentosVaDeMasAMenosAunqueElPorcentajeSeaMenor() {
        UUID mucho = cuenta("Mucho");     // Pro, sin tope: 5000 documentos
        UUID poco = cuenta("Poco");       // Gratis: 29 de 30 = 96 %
        enPlan(mucho, "Pro", AHORA.plus(Duration.ofDays(20)), 0);
        aceptados(empresa(mucho), 5000);
        aceptados(empresa(poco), 29);

        List<Registro> filas = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.DOCUMENTOS), 1, 20);

        assertThat(filas).extracting(Registro::cuentaId).containsExactly(mucho, poco);
    }

    // --- cerca del límite -------------------------------------------------------------------------------------------------------------------

    @Test void cercaDelLimiteEmpiezaEnElOchentaInclusiveYLosPlanesSinTopeNoEntran() {
        UUID justoAntes = cuenta("A 79");   // Emprende 239 de 300 = 79 %
        UUID justo = cuenta("B 80");        // Emprende 240 de 300 = 80 %
        UUID pasada = cuenta("C 120");      // Emprende 360 de 300 = 120 %
        UUID libre = cuenta("D libre");     // Pro
        for (UUID c : List.of(justoAntes, justo, pasada)) enPlan(c, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        enPlan(libre, "Pro", AHORA.plus(Duration.ofDays(20)), 0);
        aceptados(empresa(justoAntes), 239);
        aceptados(empresa(justo), 240);
        aceptados(empresa(pasada), 360);
        aceptados(empresa(libre), 100_000);

        Consulta q = consulta(FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.PORCENTAJE);

        assertThat(repo.listar(q, 1, 20)).extracting(Registro::cuentaId).containsExactly(pasada, justo);
        assertThat(repo.contar(q)).isEqualTo(2);
    }

    @Test void cercaDelLimiteUsaElUmbralQueSeLePide() {
        UUID c = cuenta("Ana");
        enPlan(c, "Emprende", AHORA.plus(Duration.ofDays(20)), 0);
        aceptados(empresa(c), 150);

        assertThat(repo.contar(new Consulta(OCTUBRE, AHORA, FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.PORCENTAJE, 50))).isEqualTo(1);
        assertThat(repo.contar(new Consulta(OCTUBRE, AHORA, FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.PORCENTAJE, 51))).isZero();
    }

    @Test void unaCuentaSinConsumoNuncaEstaCercaDelLimite() {
        cuenta("Ana");

        assertThat(repo.contar(consulta(FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.PORCENTAJE))).isZero();
    }

    // --- plan vencido -----------------------------------------------------------------------------------------------------------------------

    @Test void planVencidoSonLasQueYaPasaronSuVencimientoEnGraciaOSinEllaYElInstanteExactoCuenta() {
        UUID enGracia = cuenta("A gracia");
        UUID sinGracia = cuenta("B vencida");
        UUID justo = cuenta("C justo");
        UUID alDia = cuenta("D al dia");
        UUID gratis = cuenta("E gratis");
        enPlan(enGracia, "Emprende", AHORA.minus(Duration.ofDays(2)), 5);
        enPlan(sinGracia, "Emprende", AHORA.minus(Duration.ofDays(30)), 0);
        enPlan(justo, "Emprende", AHORA, 0);
        enPlan(alDia, "Emprende", AHORA.plusSeconds(1), 0);

        List<Registro> r = repo.listar(consulta(FiltroDeConsumo.PLAN_VENCIDO, OrdenDeConsumo.PORCENTAJE), 1, 20);

        assertThat(r).extracting(Registro::cuentaId).containsExactlyInAnyOrder(enGracia, sinGracia, justo);
        assertThat(r).extracting(Registro::cuentaId).doesNotContain(alDia, gratis);
    }

    /** El filtro de SQL y la regla del dominio dicen lo mismo: «vencido» en el filtro es «no vigente» para `Suscripcion.estadoDeLaVigente`. */
    @Test void elFiltroDeVencidosCoincideConLaRegladelDominio() {
        for (long segundos : new long[]{-86_400, -1, 0, 1, 86_400}) {
            UUID c = cuenta("Cuenta " + segundos);
            enPlan(c, "Emprende", AHORA.plusSeconds(segundos), 3);
        }
        List<UUID> vencidos = repo.todas(consulta(FiltroDeConsumo.PLAN_VENCIDO, OrdenDeConsumo.PORCENTAJE)).stream().map(Registro::cuentaId).toList();

        for (Registro r : repo.todas(todas())) {
            boolean noVigente = pe.factura.domain.plan.Suscripcion.estadoDeLaVigente(r.venceEn(), r.diasDeGracia(), AHORA) != pe.factura.domain.plan.EstadoSuscripcion.VIGENTE;
            assertThat(vencidos.contains(r.cuentaId())).as(r.nombre()).isEqualTo(noVigente);
        }
    }

    // --- paginación -------------------------------------------------------------------------------------------------------------------------

    @Test void laPaginacionEsEstableYElTotalCuentaTodas() {
        for (int i = 1; i <= 5; i++) {
            UUID c = cuenta("Cuenta " + i);
            aceptados(empresa(c), i);
        }

        List<UUID> pagina1 = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.DOCUMENTOS), 1, 2).stream().map(Registro::cuentaId).toList();
        List<UUID> pagina2 = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.DOCUMENTOS), 2, 2).stream().map(Registro::cuentaId).toList();
        List<UUID> pagina3 = repo.listar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.DOCUMENTOS), 3, 2).stream().map(Registro::cuentaId).toList();

        assertThat(pagina1).hasSize(2);
        assertThat(pagina2).hasSize(2);
        assertThat(pagina3).hasSize(1);
        assertThat(repo.contar(todas())).isEqualTo(5);
        List<UUID> juntas = new java.util.ArrayList<>(pagina1);
        juntas.addAll(pagina2);
        juntas.addAll(pagina3);
        assertThat(juntas).containsExactlyElementsOf(repo.todas(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.DOCUMENTOS)).stream().map(Registro::cuentaId).toList());
        assertThat(juntas).doesNotHaveDuplicates();
    }

    @Test void unaPaginaFueraDeRangoEstaVacia() {
        cuenta("Ana");

        assertThat(repo.listar(todas(), 5, 20)).isEmpty();
    }

    @Test void sinCuentasNoHayNada() {
        assertThat(repo.listar(todas(), 1, 20)).isEmpty();
        assertThat(repo.contar(todas())).isZero();
        assertThat(repo.todas(todas())).isEmpty();
    }

    @Test void elContarAplicaElMismoFiltroQueListar() {
        UUID c = cuenta("Ana");
        enPlan(c, "Emprende", AHORA.minus(Duration.ofDays(3)), 0);
        cuenta("Beto");

        assertThat(repo.contar(consulta(FiltroDeConsumo.PLAN_VENCIDO, OrdenDeConsumo.PORCENTAJE))).isEqualTo(1);
        assertThat(repo.contar(consulta(FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE))).isEqualTo(2);
    }
}
