package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.application.port.out.PadronRucRepository.ResultadoCarga;
import pe.factura.domain.DomainException;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #361: la carga del padrón con {@code COPY}, su mezcla con lo que ya había y que una carga mala no toque el padrón vigente. */
class JdbcPadronRucRepositoryTest extends PersistenciaTestBase {
    final JdbcPadronRucRepository repo = new JdbcPadronRucRepository(jdbc);

    @BeforeEach void vaciarPadron() {
        jdbc.update("TRUNCATE padron_ruc, padron_ruc_carga, carga_padron_ruc");
    }

    static Contribuyente c(String ruc, String nombre) { return new Contribuyente(ruc, nombre, "ACTIVO", "HABIDO", "150101", "AV. PERU NRO. 1"); }

    @Test void laPrimeraCargaDejaTodoYSeEncuentraPorRuc() {
        ResultadoCarga r = repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("10452159428", "GARCIA CHANCO CARLOS")), 2);

        assertThat(r).isEqualTo(new ResultadoCarga(2, 2, 0, 0));
        Contribuyente t = repo.buscar("20100066603").orElseThrow();
        assertThat(t.razonSocial()).isEqualTo("TIENDAS S.A.C.");
        assertThat(t.direccion()).isEqualTo("AV. PERU NRO. 1");
        assertThat(t.ubigeo()).isEqualTo("150101");
        assertThat(repo.buscar("20999999999")).isEmpty();
    }

    @Test void lasColumnasNulasSeGuardanComoNulas() {
        repo.reemplazar(Stream.of(new Contribuyente("10452159428", "GARCIA", "ACTIVO", "HABIDO", null, null)), 1);

        Contribuyente g = repo.buscar("10452159428").orElseThrow();
        assertThat(g.ubigeo()).isNull();
        assertThat(g.direccion()).isNull();
    }

    @Test void conUnaCargaAnteriorAgregaLoNuevoActualizaLoQueCambioYBorraLoQueYaNoViene() {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("20131312955", "SUNAT"), c("20601234565", "SE FUE")), 3);

        ResultadoCarga r = repo.reemplazar(Stream.of(
                new Contribuyente("20100066603", "TIENDAS S.A.C.", "BAJA DE OFICIO", "NO HABIDO", "150101", "AV. PERU NRO. 1"),   // cambió
                c("20131312955", "SUNAT"),                                                                                      // igual
                c("20123456786", "NUEVA S.A.C.")), 3);                                                                          // nueva; 20601234565 ya no viene

        assertThat(r).isEqualTo(new ResultadoCarga(3, 1, 1, 1));
        assertThat(repo.buscar("20100066603").orElseThrow().estado()).isEqualTo("BAJA DE OFICIO");
        assertThat(repo.buscar("20123456786")).isPresent();
        assertThat(repo.buscar("20601234565")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM padron_ruc", Integer.class)).isEqualTo(3);
    }

    @Test void unaCargaIgualNoReescribeNingunaFila() {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("20131312955", "SUNAT")), 2);
        String antes = jdbc.queryForObject("SELECT string_agg(ctid::text, ',' ORDER BY ruc) FROM padron_ruc", String.class);

        ResultadoCarga r = repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("20131312955", "SUNAT")), 2);

        assertThat(r).isEqualTo(new ResultadoCarga(2, 0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT string_agg(ctid::text, ',' ORDER BY ruc) FROM padron_ruc", String.class)).as("las mismas filas físicas: no hubo UPDATE").isEqualTo(antes);
    }

    @Test void unArchivoConMenosDelMinimoNoTocaElPadronVigente() {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("20131312955", "SUNAT"), c("20601234565", "OTRA")), 3);

        assertThatThrownBy(() -> repo.reemplazar(Stream.of(c("20100066603", "CAMBIADA")), 3))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PADRON_INCOMPLETO");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM padron_ruc", Integer.class)).isEqualTo(3);
        assertThat(repo.buscar("20100066603").orElseThrow().razonSocial()).isEqualTo("TIENDAS S.A.C.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM padron_ruc_carga", Integer.class)).as("la tabla de paso no se queda con filas").isZero();
    }

    @Test void siLaLecturaFallaAMitadNoQuedaNadaAMedias() {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C."), c("20131312955", "SUNAT")), 2);

        assertThatThrownBy(() -> repo.reemplazar(Stream.concat(Stream.of(c("20100066603", "CAMBIADA")), Stream.generate(() -> { throw new IllegalStateException("se cortó la lectura"); })), 1))
                .hasMessageContaining("se cortó la lectura");

        assertThat(repo.buscar("20100066603").orElseThrow().razonSocial()).isEqualTo("TIENDAS S.A.C.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM padron_ruc", Integer.class)).isEqualTo(2);
        // La conexión quedó usable y sin transacción abierta: una carga siguiente funciona.
        assertThat(repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C.")), 1).filas()).isEqualTo(1);
    }

    @Test void unRucRepetidoEnElArchivoHaceFallarLaCargaSinTocarElPadron() {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C.")), 1);

        assertThatThrownBy(() -> repo.reemplazar(Stream.of(c("20131312955", "A"), c("20131312955", "B")), 1)).isInstanceOf(RuntimeException.class);

        assertThat(repo.buscar("20131312955")).isEmpty();
        assertThat(repo.buscar("20100066603")).isPresent();
    }

    @Test void losCaracteresDelFormatoDeCopyLlegaSinAlterarse() {
        String raro = "A\tB\\N\nC\rD \\ E \\t F ñ Ñ ° \"comillas\" 'simples' |barra|";

        repo.reemplazar(Stream.of(new Contribuyente("20100066603", raro, "ACTIVO", "HABIDO", "150101", raro)), 1);

        Contribuyente leido = repo.buscar("20100066603").orElseThrow();
        assertThat(leido.razonSocial()).isEqualTo(raro);
        assertThat(leido.direccion()).isEqualTo(raro);
    }

    @Test void unTextoNuloEnElFormatoEsBackslashN() {
        assertThat(JdbcPadronRucRepository.campo(null)).isEqualTo("\\N");
        assertThat(JdbcPadronRucRepository.campo("\\N")).as("el texto «\\N» no es un nulo").isEqualTo("\\\\N");
    }

    @Test void conOtraCargaCorriendoNoEmpiezaNiTocaNada() throws Exception {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C.")), 1);
        try (Connection otra = ds.getConnection()) {
            otra.setAutoCommit(false);
            try (Statement st = otra.createStatement()) {
                st.execute("SELECT pg_advisory_xact_lock(361361)");

                assertThatThrownBy(() -> repo.reemplazar(Stream.of(c("20131312955", "NUEVA")), 1))
                        .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CARGA_EN_CURSO");
            } finally {
                otra.rollback();
            }
        }

        assertThat(repo.buscar("20131312955")).isEmpty();
        assertThat(repo.reemplazar(Stream.of(c("20131312955", "NUEVA")), 1).filas()).as("liberado el candado, carga").isEqualTo(1);
    }

    @Test void sinCargasNoHayUltimaExitosa() {
        assertThat(repo.ultimaCargaExitosa()).isEmpty();
    }

    @Test void laUltimaCargaExitosaIgnoraLasFallidasYTomaLaMasReciente() {
        Instant t = Instant.parse("2026-10-15T09:30:00Z").truncatedTo(ChronoUnit.MILLIS);
        repo.registrar(new CargaRegistrada(t.minusSeconds(600), t.minusSeconds(500), true, "vieja", 100, 1, 2, 3, 4, null));
        repo.registrar(new CargaRegistrada(t, t.plusSeconds(60), true, "reciente", 200, 5, 6, 7, 8, null));
        repo.registrar(new CargaRegistrada(t.plusSeconds(3600), t.plusSeconds(3660), false, "fallida", 0, 0, 0, 0, 0, "se cayó"));

        CargaRegistrada u = repo.ultimaCargaExitosa().orElseThrow();

        assertThat(u).isEqualTo(new CargaRegistrada(t, t.plusSeconds(60), true, "reciente", 200, 5, 6, 7, 8, null));
        assertThat(jdbc.queryForList("SELECT error FROM carga_padron_ruc WHERE resultado = 'FALLIDA'", String.class)).isEqualTo(List.of("se cayó"));
    }

    @Test void buscarPorRucUsaElIndiceDeLaClavePrimaria() throws Exception {
        repo.reemplazar(Stream.of(c("20100066603", "TIENDAS S.A.C.")), 1);

        // Una sola conexión para que el SET valga: con una fila el planificador prefiere recorrer la tabla, y lo que se prueba es que el índice sirve a la consulta del repositorio
        // (un parámetro de texto contra la columna: con CHAR(11) el índice no se usaría).
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute("SET enable_seqscan = off");
            var plan = new StringBuilder();
            try (var rs = st.executeQuery("EXPLAIN SELECT ruc FROM padron_ruc WHERE ruc = ('20100066603'::text)::varchar")) {
                while (rs.next()) plan.append(rs.getString(1)).append('\n');
            }
            assertThat(plan.toString()).contains("padron_ruc_pkey");
        }
    }
}
