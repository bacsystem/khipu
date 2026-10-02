package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCuentasAdminRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcCuentasAdminRepository repo = new JdbcCuentasAdminRepository(jdbc);

    UUID cuenta(String nombre, String email, Instant creada) { return cuenta(UUID.randomUUID(), nombre, email, creada); }

    UUID cuenta(UUID id, String nombre, String email, Instant creada) {
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', ?)", id, nombre, email, Timestamp.from(creada));
        return id;
    }

    UUID usuario(UUID cuenta, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, cuenta_id, email, password_hash, rol) VALUES (?, ?, ?, 'hash', 'ADMIN')", id, cuenta, email);
        return id;
    }

    void sesion(UUID usuario, Instant cuando) {
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), usuario, UUID.randomUUID().toString().replace("-", ""), Timestamp.from(cuando.plusSeconds(86400)), Timestamp.from(cuando));
    }

    void empresa(UUID cuenta, String ruc, String razonSocial) {
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, ?, 'BETA', ?)", UUID.randomUUID(), ruc, razonSocial, cuenta);
    }

    List<String> emails(List<CuentaResumen> filas) { return filas.stream().map(CuentaResumen::email).toList(); }

    @Test void listaDeLaMasRecienteALaMasAntiguaYDesempataPorId() {
        cuenta("Vieja", "vieja@x.pe", T0);
        cuenta(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Dos", "dos@x.pe", T0.plusSeconds(60));
        cuenta(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Uno", "uno@x.pe", T0.plusSeconds(60));

        List<CuentaResumen> filas = repo.listar(Filtro.NINGUNO, 1, 20);

        assertThat(emails(filas)).containsExactly("uno@x.pe", "dos@x.pe", "vieja@x.pe");
    }

    @Test void devuelveLosDatosDeLaCuentaYCuentaSusEmpresas() {
        UUID con = cuenta("Mi negocio", "ana@negocio.pe", T0);
        empresa(con, "20100066603", "COMERCIAL ANDINA SAC");
        empresa(con, "20100066611", "ANDINA NORTE SAC");
        cuenta("Sin empresas", "sin@x.pe", T0.minusSeconds(60));

        List<CuentaResumen> filas = repo.listar(Filtro.NINGUNO, 1, 20);

        CuentaResumen ana = filas.get(0);
        assertThat(ana.id()).isEqualTo(con);
        assertThat(ana.nombre()).isEqualTo("Mi negocio");
        assertThat(ana.email()).isEqualTo("ana@negocio.pe");
        assertThat(ana.telefono()).isEqualTo("987654321");
        assertThat(ana.creadaEn()).isEqualTo(T0);
        assertThat(ana.empresas()).isEqualTo(2);
        assertThat(filas.get(1).empresas()).isZero();
    }

    @Test void elUltimoAccesoEsLaSesionMasRecienteDeCualquierUsuarioDeLaCuenta() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID u1 = usuario(cuenta, "ana@negocio.pe");
        UUID u2 = usuario(cuenta, "luis@negocio.pe");
        sesion(u1, T0.plusSeconds(100));
        sesion(u2, T0.plusSeconds(900));
        sesion(u1, T0.plusSeconds(300));

        assertThat(repo.listar(Filtro.NINGUNO, 1, 20).get(0).ultimoAcceso()).isEqualTo(T0.plusSeconds(900));
    }

    @Test void sinSesionesElUltimoAccesoEsNulo() {
        UUID cuenta = cuenta("Nueva", "nueva@x.pe", T0);
        usuario(cuenta, "nueva@x.pe");

        assertThat(repo.listar(Filtro.NINGUNO, 1, 20).get(0).ultimoAcceso()).isNull();
    }

    @Test void buscaPorCorreoYPorNombreSinDistinguirMayusculas() {
        cuenta("Panadería Sol", "ana@sol.pe", T0);
        cuenta("Ferretería Luna", "luis@luna.pe", T0.plusSeconds(1));

        assertThat(emails(repo.listar(new Filtro("LUIS@"), 1, 20))).containsExactly("luis@luna.pe");
        assertThat(emails(repo.listar(new Filtro("panader"), 1, 20))).containsExactly("ana@sol.pe");
    }

    @Test void buscaElRucPorPrefijoYNoPorUnFragmentoInterno() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        empresa(cuenta, "20100066603", "COMERCIAL ANDINA SAC");

        assertThat(emails(repo.listar(new Filtro("2010006"), 1, 20))).containsExactly("ana@negocio.pe");
        assertThat(repo.listar(new Filtro("0066"), 1, 20)).isEmpty();
    }

    @Test void buscaPorRazonSocialDeUnaEmpresaYDevuelveLaCuentaUnaSolaVez() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        empresa(cuenta, "20100066603", "COMERCIAL ANDINA SAC");
        empresa(cuenta, "20100066611", "ANDINA NORTE SAC");
        cuenta("Otra", "otra@x.pe", T0.plusSeconds(1));

        Filtro filtro = new Filtro("andina");

        assertThat(emails(repo.listar(filtro, 1, 20))).containsExactly("ana@negocio.pe");
        assertThat(repo.contar(filtro)).isEqualTo(1);
    }

    @Test void losComodinesDeLikeSeTomanLiteralmente() {
        cuenta("Uno", "a_b@x.pe", T0);
        cuenta("Dos", "axb@x.pe", T0.plusSeconds(1));
        cuenta("Tres", "100%@x.pe", T0.plusSeconds(2));

        assertThat(emails(repo.listar(new Filtro("a_b"), 1, 20))).containsExactly("a_b@x.pe");
        assertThat(emails(repo.listar(new Filtro("%"), 1, 20))).containsExactly("100%@x.pe");
        assertThat(repo.contar(new Filtro("_"))).isEqualTo(1);
    }

    @Test void paginaYCuentaRespetandoElFiltro() {
        for (int i = 1; i <= 5; i++) cuenta("Cuenta " + i, "c" + i + "@x.pe", T0.plusSeconds(i));
        cuenta("Otra", "otra@y.pe", T0.plusSeconds(10));

        assertThat(emails(repo.listar(Filtro.NINGUNO, 2, 2))).containsExactly("c4@x.pe", "c3@x.pe");
        assertThat(repo.contar(Filtro.NINGUNO)).isEqualTo(6);

        Filtro filtro = new Filtro("@x.pe");
        assertThat(repo.contar(filtro)).isEqualTo(5);
        assertThat(emails(repo.listar(filtro, 3, 2))).containsExactly("c1@x.pe");
        assertThat(repo.listar(filtro, 4, 2)).isEmpty();
    }
}
