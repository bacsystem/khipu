package pe.factura.adapters.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura de los planes (#189) con Postgres real: lo que se guarda como NULL vuelve como «sin límite», y el orden y el plan por defecto son los esperados. */
class JdbcPlanRepositoryTest extends PersistenciaTestBase {
    JdbcPlanRepository repo = new JdbcPlanRepository(jdbc);

    /** `plan` no se vacía entre tests (los cuatro son de la migración): un plan de más que quede por un test fallido rompería los que cuentan los cuatro. */
    @AfterEach void quitarLosPlanesDeLosTests() {
        jdbc.update("DELETE FROM plan WHERE nombre NOT IN ('Gratis', 'Emprende', 'Negocio', 'Pro')");
    }

    @Test void listaLosCuatroPlanesDelMasBaratoAlMasCaro() {
        assertThat(repo.listar()).extracting(Plan::nombre).containsExactly("Gratis", "Emprende", "Negocio", "Pro");
    }

    @Test void losLimitesFinitosVuelvenConSuCifra() {
        Plan negocio = repo.listar().stream().filter(p -> p.nombre().equals("Negocio")).findFirst().orElseThrow();

        assertThat(negocio.precioMensual()).isEqualByComparingTo("69");
        assertThat(negocio.documentosAlMes()).isEqualTo(Limite.de(1500));
        assertThat(negocio.rucs()).isEqualTo(3);
        assertThat(negocio.usuarios()).isEqualTo(Limite.de(3));
        assertThat(negocio.apiKeys()).isEqualTo(Limite.de(5));
        assertThat(negocio.retencionAnios()).isEqualTo(5);
        assertThat(negocio.estado()).isEqualTo(EstadoPlan.ACTIVO);
        assertThat(negocio.porDefecto()).isFalse();
    }

    @Test void proNoTieneTopeDeDocumentosNiDeUsuariosNiDeKeys() {
        Plan pro = repo.listar().stream().filter(p -> p.nombre().equals("Pro")).findFirst().orElseThrow();

        assertThat(pro.documentosAlMes().ilimitado()).isTrue();
        assertThat(pro.usuarios().ilimitado()).isTrue();
        assertThat(pro.apiKeys().ilimitado()).isTrue();
        assertThat(pro.rucs()).isEqualTo(10);
    }

    @Test void elPlanPorDefectoEsGratis() {
        Plan base = repo.porDefecto();

        assertThat(base.nombre()).isEqualTo("Gratis");
        assertThat(base.porDefecto()).isTrue();
        assertThat(base.precioMensual()).isEqualByComparingTo("0");
    }

    @Test void buscaPorIdYDevuelveVacioSiNoExiste() {
        Plan emprende = repo.listar().get(1);

        assertThat(repo.buscar(emprende.id())).contains(emprende);
        assertThat(repo.buscar(UUID.randomUUID())).isEmpty();
    }

    @Test void losInactivosTambienSeListan() {
        jdbc.update("INSERT INTO plan (nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, estado) VALUES ('Viejo', 10, 50, 1, 1, 1, 1, 'INACTIVO')");

        List<Plan> todos = repo.listar();

        assertThat(todos).extracting(Plan::nombre).contains("Viejo");
        assertThat(todos.stream().filter(p -> p.nombre().equals("Viejo")).findFirst().orElseThrow().activo()).isFalse();
    }

    @Test void aIgualPrecioOrdenaPorNombre() {
        jdbc.update("INSERT INTO plan (nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios) VALUES ('Abeja', 29, 50, 1, 1, 1, 1)");

        List<String> nombres = repo.listar().stream().map(Plan::nombre).toList();

        assertThat(nombres.indexOf("Abeja")).isLessThan(nombres.indexOf("Emprende"));
    }
}
