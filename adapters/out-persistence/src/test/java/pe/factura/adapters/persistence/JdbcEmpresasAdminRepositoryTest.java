package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.domain.tenant.Entorno;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Listado de empresas del backoffice (#185), con Postgres real. «Hoy» llega de afuera: aquí es el 3 de octubre de 2026. */
class JdbcEmpresasAdminRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    static final LocalDate HOY = LocalDate.of(2026, 10, 3);

    JdbcEmpresasAdminRepository repo = new JdbcEmpresasAdminRepository(jdbc);

    UUID cuenta(String nombre, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', ?)", id, nombre, email, Timestamp.from(T0));
        return id;
    }

    UUID empresa(UUID cuenta, String ruc, String razonSocial, Instant creada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id, created_at) VALUES (?, ?, ?, 'BETA', ?, ?)", id, ruc, razonSocial, cuenta, Timestamp.from(creada));
        return id;
    }

    UUID empresa(String ruc) { return empresa(null, ruc, "EMPRESA " + ruc, T0); }

    /** Un certificado cargado; {@code hasta} nulo = se cargó sin conocer su vigencia. */
    void certificado(UUID empresa, String hasta) {
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = ?::date WHERE id = ?", new byte[]{1}, new byte[]{2}, hasta, empresa);
    }

    void serie(UUID empresa, String codigo, boolean activa) {
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, activa) VALUES (?, '01', ?, ?)", empresa, codigo, activa);
    }

    int numero = 0;

    void documento(UUID empresa, String fechaEmision) {
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                VALUES (?, ?, '01', 'F001', ?, ?::date, 'ACEPTADO', ?)""", UUID.randomUUID(), empresa, ++numero, fechaEmision, "doc-" + numero);
    }

    List<String> rucs(List<EmpresaResumen> filas) { return filas.stream().map(EmpresaResumen::ruc).toList(); }

    EmpresaResumen fila(String ruc) {
        return repo.listar(Filtro.NINGUNO, HOY, 1, 100).stream().filter(e -> e.ruc().equals(ruc)).findFirst().orElseThrow();
    }

    @Test void listaDeLaMasRecienteALaMasAntiguaYDesempataPorId() {
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, created_at) VALUES ('00000000-0000-0000-0000-000000000002', '20100000002', 'DOS', 'BETA', ?)", Timestamp.from(T0.plusSeconds(60)));
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, created_at) VALUES ('00000000-0000-0000-0000-000000000001', '20100000001', 'UNO', 'BETA', ?)", Timestamp.from(T0.plusSeconds(60)));
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, created_at) VALUES (gen_random_uuid(), '20100000009', 'VIEJA', 'BETA', ?)", Timestamp.from(T0));

        assertThat(rucs(repo.listar(Filtro.NINGUNO, HOY, 1, 20))).containsExactly("20100000001", "20100000002", "20100000009");
    }

    @Test void traeLosDatosDeLaEmpresaYDeSuCuenta() {
        UUID ana = cuenta("Mi negocio", "ana@negocio.pe");
        UUID e = empresa(ana, "20100066603", "COMERCIAL ANDINA SAC", T0);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION', sol_usuario_enc = ?, sol_clave_enc = ? WHERE id = ?", new byte[]{3}, new byte[]{4}, e);

        EmpresaResumen r = fila("20100066603");

        assertThat(r.id()).isEqualTo(e);
        assertThat(r.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(r.cuentaId()).isEqualTo(ana);
        assertThat(r.cuentaNombre()).isEqualTo("Mi negocio");
        assertThat(r.entorno()).isEqualTo(Entorno.PRODUCCION);
        assertThat(r.tieneCredencialesSol()).isTrue();
    }

    /** Las empresas dadas de alta por una integración (`POST /v1/admin/tenants`) no tienen cuenta: igual salen en el listado. */
    @Test void unaEmpresaSinCuentaTambienSale() {
        empresa("20100066611");

        EmpresaResumen r = fila("20100066611");

        assertThat(r.cuentaId()).isNull();
        assertThat(r.cuentaNombre()).isNull();
        assertThat(repo.contar(Filtro.NINGUNO, HOY)).isEqualTo(1);
    }

    @Test void lasCredencialesSolALasMitadesNoCuentanComoCargadas() {
        UUID e = empresa("20100066611");
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ? WHERE id = ?", new byte[]{3}, e);

        assertThat(fila("20100066611").tieneCredencialesSol()).isFalse();
    }

    // --- estado del certificado ----------------------------------------------------------------------------------------------------

    /** Un caso por borde: sin certificado, sin fecha, venció ayer, vence hoy, 29 y 30 días (la épica: «por vencer» es MENOS de 30). */
    private void sembrarUnaDeCadaEstado() {
        empresa("20100000001");                                            // sin certificado
        certificado(empresa("20100000002"), null);                         // cargado, sin vigencia
        certificado(empresa("20100000003"), "2026-10-02");                 // venció ayer
        certificado(empresa("20100000004"), "2026-10-03");                 // vence hoy: todavía vale
        certificado(empresa("20100000005"), "2026-11-01");                 // 29 días
        certificado(empresa("20100000006"), "2026-11-02");                 // 30 días
        certificado(empresa("20100000007"), "2027-10-03");                 // un año
    }

    @Test void elEstadoYLosDiasRestantesDelCertificadoSalenDeHoy() {
        sembrarUnaDeCadaEstado();

        assertThat(fila("20100000001")).satisfies(e -> {
            assertThat(e.certificado()).isEqualTo(EstadoCertificado.SIN_CERTIFICADO);
            assertThat(e.certificadoVigenteHasta()).isNull();
            assertThat(e.certificadoDiasRestantes()).isNull();
        });
        assertThat(fila("20100000002")).satisfies(e -> {
            assertThat(e.certificado()).isEqualTo(EstadoCertificado.SIN_FECHA);
            assertThat(e.certificadoDiasRestantes()).isNull();
        });
        assertThat(fila("20100000003")).satisfies(e -> {
            assertThat(e.certificado()).isEqualTo(EstadoCertificado.VENCIDO);
            assertThat(e.certificadoDiasRestantes()).isEqualTo(-1);
            assertThat(e.certificadoVigenteHasta()).isEqualTo(LocalDate.of(2026, 10, 2));
        });
        assertThat(fila("20100000004")).satisfies(e -> {
            assertThat(e.certificado()).as("el último día de vigencia todavía vale").isEqualTo(EstadoCertificado.POR_VENCER);
            assertThat(e.certificadoDiasRestantes()).isZero();
        });
        assertThat(fila("20100000005")).satisfies(e -> {
            assertThat(e.certificado()).isEqualTo(EstadoCertificado.POR_VENCER);
            assertThat(e.certificadoDiasRestantes()).isEqualTo(29);
        });
        assertThat(fila("20100000006")).satisfies(e -> {
            assertThat(e.certificado()).as("con 30 días justos todavía es vigente").isEqualTo(EstadoCertificado.VIGENTE);
            assertThat(e.certificadoDiasRestantes()).isEqualTo(30);
        });
        assertThat(fila("20100000007").certificado()).isEqualTo(EstadoCertificado.VIGENTE);
    }

    @Test void elEstadoCambiaConLaFechaDeHoyQueSePasa() {
        certificado(empresa("20100000004"), "2026-10-03");

        EmpresaResumen manana = repo.listar(Filtro.NINGUNO, HOY.plusDays(1), 1, 20).get(0);

        assertThat(manana.certificado()).isEqualTo(EstadoCertificado.VENCIDO);
        assertThat(manana.certificadoDiasRestantes()).isEqualTo(-1);
    }

    @Test void cadaEstadoDelFiltroTraeSoloLasEmpresasDeEseEstado() {
        sembrarUnaDeCadaEstado();

        assertThat(rucs(repo.listar(new Filtro(null, EstadoCertificado.SIN_CERTIFICADO), HOY, 1, 20))).containsExactly("20100000001");
        assertThat(rucs(repo.listar(new Filtro(null, EstadoCertificado.SIN_FECHA), HOY, 1, 20))).containsExactly("20100000002");
        assertThat(rucs(repo.listar(new Filtro(null, EstadoCertificado.VENCIDO), HOY, 1, 20))).containsExactly("20100000003");
        assertThat(rucs(repo.listar(new Filtro(null, EstadoCertificado.POR_VENCER), HOY, 1, 20))).containsExactlyInAnyOrder("20100000004", "20100000005");
        assertThat(rucs(repo.listar(new Filtro(null, EstadoCertificado.VIGENTE), HOY, 1, 20))).containsExactlyInAnyOrder("20100000006", "20100000007");
    }

    /** El filtro y la columna comparten una sola regla: lo que el filtro deja pasar es exactamente lo que la fila dice ser. */
    @Test void elFiltroYLaColumnaCoincidenParaCadaEmpresa() {
        sembrarUnaDeCadaEstado();

        for (EstadoCertificado estado : EstadoCertificado.values())
            assertThat(repo.listar(new Filtro(null, estado), HOY, 1, 20)).as(estado.name()).allSatisfy(e -> assertThat(e.certificado()).isEqualTo(estado));
    }

    @Test void filtraPorEntorno() {
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE id = ?", empresa("20100000001"));
        empresa("20100000002");

        assertThat(rucs(repo.listar(new Filtro(Entorno.PRODUCCION, null), HOY, 1, 20))).containsExactly("20100000001");
        assertThat(rucs(repo.listar(new Filtro(Entorno.BETA, null), HOY, 1, 20))).containsExactly("20100000002");
    }

    @Test void losFiltrosSeCombinanYElTotalLosRefleja() {
        sembrarUnaDeCadaEstado();
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE ruc IN ('20100000003', '20100000004')");
        Filtro vencidasEnProduccion = new Filtro(Entorno.PRODUCCION, EstadoCertificado.VENCIDO);

        assertThat(rucs(repo.listar(vencidasEnProduccion, HOY, 1, 20))).containsExactly("20100000003");
        assertThat(repo.contar(vencidasEnProduccion, HOY)).isEqualTo(1);
        assertThat(repo.contar(new Filtro(Entorno.PRODUCCION, null), HOY)).isEqualTo(2);
        assertThat(repo.contar(Filtro.NINGUNO, HOY)).isEqualTo(7);
    }

    // --- series, comprobantes del mes y última emisión ------------------------------------------------------------------------------

    @Test void lasSeriesSonLasActivasDeLaEmpresa() {
        UUID a = empresa(null, "20100000001", "A", T0);
        UUID b = empresa(null, "20100000002", "B", T0);
        serie(a, "F001", true);
        serie(a, "F002", true);
        serie(a, "F003", false);
        serie(b, "F001", true);

        assertThat(fila("20100000001").series()).isEqualTo(2);
        assertThat(fila("20100000002").series()).isEqualTo(1);
    }

    @Test void sinSeriesNiComprobantesLosNumerosSonCeroYLaUltimaEmisionNula() {
        empresa("20100000001");

        EmpresaResumen r = fila("20100000001");

        assertThat(r.series()).isZero();
        assertThat(r.comprobantesDelMes()).isZero();
        assertThat(r.ultimaEmision()).isNull();
    }

    /** Del 1 al 31 de octubre de 2026 (el mes de «hoy»): el 30 de septiembre y el 1 de noviembre quedan fuera. */
    @Test void losComprobantesDelMesSonLosDeEmisionEnElMesDeHoy() {
        UUID a = empresa(null, "20100000001", "A", T0);
        UUID b = empresa(null, "20100000002", "B", T0);
        for (String f : List.of("2026-09-30", "2026-10-01", "2026-10-03", "2026-10-31", "2026-11-01")) documento(a, f);
        documento(b, "2026-10-15");

        assertThat(fila("20100000001").comprobantesDelMes()).isEqualTo(3);
        assertThat(fila("20100000002").comprobantesDelMes()).as("no se mezclan las empresas").isEqualTo(1);
    }

    @Test void elMesSigueLaFechaDeHoyQueSePasa() {
        UUID a = empresa(null, "20100000001", "A", T0);
        documento(a, "2026-09-30");
        documento(a, "2026-10-01");

        EmpresaResumen enSeptiembre = repo.listar(Filtro.NINGUNO, LocalDate.of(2026, 9, 15), 1, 20).get(0);

        assertThat(enSeptiembre.comprobantesDelMes()).isEqualTo(1);
    }

    @Test void laUltimaEmisionEsLaFechaDelDocumentoMasReciente() {
        UUID a = empresa(null, "20100000001", "A", T0);
        UUID b = empresa(null, "20100000002", "B", T0);
        documento(a, "2026-08-10");
        documento(a, "2026-09-20");
        documento(a, "2026-09-05");
        documento(b, "2026-10-02");

        assertThat(fila("20100000001").ultimaEmision()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(fila("20100000002").ultimaEmision()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    // --- paginado --------------------------------------------------------------------------------------------------------------------

    @Test void paginaYCuentaRespetandoElFiltro() {
        for (int i = 1; i <= 5; i++) {
            UUID e = empresa(null, "2010000000" + i, "E" + i, T0.plusSeconds(i));
            jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE id = ?", e);
        }
        empresa("20100000099");
        Filtro filtro = new Filtro(Entorno.PRODUCCION, null);

        assertThat(repo.contar(filtro, HOY)).isEqualTo(5);
        assertThat(rucs(repo.listar(filtro, HOY, 1, 2))).containsExactly("20100000005", "20100000004");
        assertThat(rucs(repo.listar(filtro, HOY, 3, 2))).containsExactly("20100000001");
        assertThat(repo.listar(filtro, HOY, 4, 2)).isEmpty();
    }

    // --- índices ---------------------------------------------------------------------------------------------------------------------

    /** Ver {@code JdbcCuentasAdminRepositoryTest#plan}: con los recorridos completos prohibidos, el índice existe y sirve a esa consulta. */
    private String plan(String consulta) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) con -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET enable_seqscan = off");
                StringBuilder texto = new StringBuilder();
                try (java.sql.ResultSet rs = st.executeQuery("EXPLAIN " + consulta)) {
                    while (rs.next()) texto.append(rs.getString(1)).append('\n');
                }
                return texto.toString();
            }
        });
    }

    @Test void elOrdenDelListadoSeApoyaEnUnIndice() {
        assertThat(plan("SELECT id FROM tenant ORDER BY created_at DESC, id LIMIT 10")).contains("ix_tenant_alta");
    }

    @Test void laUltimaEmisionYLosComprobantesDelMesSeLeenPorElIndiceDeLaEmpresa() {
        UUID t = UUID.randomUUID();
        assertThat(plan("SELECT max(fecha_emision) FROM documento WHERE tenant_id = '" + t + "'")).contains("ix_documento_tenant_fecha");
        assertThat(plan("SELECT count(*) FROM documento WHERE tenant_id = '" + t + "' AND fecha_emision >= '2026-10-01' AND fecha_emision < '2026-11-01'"))
                .contains("ix_documento_tenant_fecha");
    }
}
