package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.in.VisibilidadDeBajas;

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

    // --- #214: tildes ------------------------------------------------------------------------------------------------------------

    @Test void lasTildesNoImportanEnNingunSentido() {
        cuenta("Librería El Saber", "libro@x.pe", T0);
        cuenta("Taller Mecanico Rojas", "taller@x.pe", T0.plusSeconds(1));

        for (String q : List.of("libreria", "LIBRERIA", "librería", "LIBRERÍA", "LiBrErÍa"))
            assertThat(emails(repo.listar(new Filtro(q), 1, 20))).as("«%s»", q).containsExactly("libro@x.pe");
        for (String q : List.of("mecanico", "mecánico", "MECÁNICO"))
            assertThat(emails(repo.listar(new Filtro(q), 1, 20))).as("«%s»", q).containsExactly("taller@x.pe");
        assertThat(repo.contar(new Filtro("libreria"))).isEqualTo(1);
    }

    @Test void tambienEnLaRazonSocialYConDieresisYOtrosAcentos() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        empresa(cuenta, "20100066603", "INVERSIONES ÁVILA Y AGÜERO SAC");
        cuenta("Crèperie Sofía", "crepe@x.pe", T0.plusSeconds(1));

        assertThat(emails(repo.listar(new Filtro("avila"), 1, 20))).containsExactly("ana@negocio.pe");
        assertThat(emails(repo.listar(new Filtro("aguero"), 1, 20))).containsExactly("ana@negocio.pe");
        assertThat(emails(repo.listar(new Filtro("creperie"), 1, 20))).containsExactly("crepe@x.pe");
        assertThat(repo.contar(new Filtro("ávila"))).isEqualTo(1);
    }

    /** La ñ es otra letra, no una n con tilde: «peña» y «pena» son palabras distintas (decisión de #214). */
    @Test void laEnieNoSeConfundeConLaEne() {
        cuenta("Peña Hermanos", "hermanos@x.pe", T0);
        cuenta("Pena Sur", "sur@x.pe", T0.plusSeconds(1));

        assertThat(emails(repo.listar(new Filtro("peña"), 1, 20))).containsExactly("hermanos@x.pe");
        assertThat(emails(repo.listar(new Filtro("PEÑA"), 1, 20))).containsExactly("hermanos@x.pe");
        assertThat(emails(repo.listar(new Filtro("pena"), 1, 20))).containsExactly("sur@x.pe");
    }

    /** Una tilde puede llegar como un carácter (í) o como letra + acento combinado (i + ́): las dos formas buscan lo mismo. */
    @Test void unaTildeEscritaComoAcentoCombinadoTambienSeIgnora() {
        cuenta("Librería El Saber", "libro@x.pe", T0);

        assertThat(emails(repo.listar(new Filtro("librería"), 1, 20))).containsExactly("libro@x.pe");
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

    /**
     * Plan de una consulta con los recorridos completos desactivados: si el índice existe, el planificador lo usa; si no,
     * no tiene alternativa. Con las pocas filas de un test el planificador elegiría un recorrido completo aunque el índice
     * esté, por eso se le prohíbe: lo que se comprueba es que el índice sirve para esa consulta, no que gane con poco volumen.
     */
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

    @Test void elUltimoAccesoBuscaLosUsuariosDeLaCuentaPorIndice() {
        // La subconsulta de `ultimo_acceso` filtra `usuario` por `cuenta_id` en cada fila de la página.
        assertThat(plan("SELECT id FROM usuario WHERE cuenta_id = '" + UUID.randomUUID() + "'")).contains("ix_usuario_cuenta");
    }

    @Test void laSesionMasRecienteDeUnUsuarioSeLeeDelIndiceSinRecorrerSusSesiones() {
        // `max(created_at)` por usuario: con (usuario_id, created_at) es una lectura al final del índice, no un recorrido de todas sus sesiones.
        assertThat(plan("SELECT max(created_at) FROM sesion WHERE usuario_id = '" + UUID.randomUUID() + "'")).contains("ix_sesion_acceso");
    }

    @Test void sesionTieneUnSoloIndiceSobreUsuarioYLasBusquedasPorUsuarioSiguenIndexadas() {
        // `ix_sesion_acceso` cubre las búsquedas solo por `usuario_id` (revocar sesiones, borrado en cascada): un segundo índice con la misma
        // columna inicial solo encarece cada inicio de sesión y cada refresco.
        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'sesion' AND indexdef LIKE '%(usuario_id%'", String.class);
        assertThat(indices).containsExactly("ix_sesion_acceso");
        assertThat(plan("SELECT id FROM sesion WHERE usuario_id = '" + UUID.randomUUID() + "'")).contains("ix_sesion_acceso");
    }

    // --- #181: detalle de una cuenta ----------------------------------------------------------------------------------------------

    UUID empresaConId(UUID cuenta, String ruc, String razonSocial) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, ?, 'BETA', ?)", id, ruc, razonSocial, cuenta);
        return id;
    }

    void comprobante(UUID tenant, String serie, long numero, String fecha, String estado, String total, Instant creado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, created_at)
                VALUES (?, ?, '01', ?, ?, ?::date, ?, ?, ?)""", id, tenant, serie, numero, fecha, estado, serie + "-" + numero, Timestamp.from(creado));
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre,
                                         total_gravado, total_exonerado, total_inafecto, total_igv, total)
                VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE SAC', 0, 0, 0, 0, ?::numeric)""", id, total);
    }

    @Test void unaCuentaQueNoExisteNoTieneDetalle() {
        assertThat(repo.detalle(UUID.randomUUID())).isEmpty();
    }

    @Test void elDetalleTraeLosDatosDeLaCuenta() {
        UUID id = cuenta("Mi negocio", "ana@negocio.pe", T0);

        var d = repo.detalle(id).orElseThrow();

        assertThat(d.id()).isEqualTo(id);
        assertThat(d.nombre()).isEqualTo("Mi negocio");
        assertThat(d.email()).isEqualTo("ana@negocio.pe");
        assertThat(d.telefono()).isEqualTo("987654321");
        assertThat(d.creadaEn()).isEqualTo(T0);
        assertThat(d.usuarios()).isEmpty();
        assertThat(d.empresas()).isEmpty();
        assertThat(d.comprobantes()).isEmpty();
        assertThat(d.eventos()).isEmpty();
    }

    @Test void losUsuariosTraenRolVerificacionYUltimoAccesoDeCadaUno() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID ana = usuario(cuenta, "ana@negocio.pe");
        UUID luis = usuario(cuenta, "luis@negocio.pe");
        jdbc.update("UPDATE usuario SET rol = 'EMISOR', activo = false WHERE id = ?", luis);
        jdbc.update("UPDATE usuario SET correo_verificado_at = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(60)), ana);
        sesion(ana, T0.plusSeconds(100));
        sesion(ana, T0.plusSeconds(500));
        UUID otra = cuenta("Otra", "otra@x.pe", T0);
        sesion(usuario(otra, "otro@x.pe"), T0.plusSeconds(900));

        var usuarios = repo.detalle(cuenta).orElseThrow().usuarios();

        assertThat(usuarios).extracting(u -> u.email()).containsExactly("ana@negocio.pe", "luis@negocio.pe");
        var a = usuarios.get(0);
        assertThat(a.rol()).isEqualTo("ADMIN");
        assertThat(a.activo()).isTrue();
        assertThat(a.correoVerificadoEn()).isEqualTo(T0.plusSeconds(60));
        assertThat(a.ultimoAcceso()).as("la más reciente de SUS sesiones, no la de otra cuenta").isEqualTo(T0.plusSeconds(500));
        var l = usuarios.get(1);
        assertThat(l.rol()).isEqualTo("EMISOR");
        assertThat(l.activo()).isFalse();
        assertThat(l.correoVerificadoEn()).isNull();
        assertThat(l.ultimoAcceso()).isNull();
    }

    @Test void lasEmpresasDicenSiTienenCertificadoYCredencialesSolSinExponerlos() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID completa = empresaConId(cuenta, "20100066603", "COMERCIAL ANDINA SAC");
        UUID vacia = empresaConId(cuenta, "20100066611", "ANDINA NORTE SAC");
        UUID soloSol = empresaConId(cuenta, "20100066629", "ANDINA SUR SAC");
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = '2027-03-01', sol_usuario_enc = ?, sol_clave_enc = ?, entorno = 'PRODUCCION' WHERE id = ?",
                new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}, completa);
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ?, sol_clave_enc = ? WHERE id = ?", new byte[]{3}, new byte[]{4}, soloSol);
        empresaConId(cuenta("Ajena", "ajena@x.pe", T0), "20100066637", "NO ES DE ESTA CUENTA");

        var empresas = repo.detalle(cuenta).orElseThrow().empresas();

        assertThat(empresas).extracting(e -> e.ruc()).containsExactlyInAnyOrder("20100066603", "20100066611", "20100066629");
        var c = empresas.stream().filter(e -> e.id().equals(completa)).findFirst().orElseThrow();
        assertThat(c.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(c.entorno()).isEqualTo("PRODUCCION");
        assertThat(c.tieneCertificado()).isTrue();
        assertThat(c.certificadoVigenteHasta()).isEqualTo(java.time.LocalDate.of(2027, 3, 1));
        assertThat(c.tieneCredencialesSol()).isTrue();
        var v = empresas.stream().filter(e -> e.id().equals(vacia)).findFirst().orElseThrow();
        assertThat(v.entorno()).isEqualTo("BETA");
        assertThat(v.tieneCertificado()).isFalse();
        assertThat(v.certificadoVigenteHasta()).isNull();
        assertThat(v.tieneCredencialesSol()).isFalse();
        var s = empresas.stream().filter(e -> e.id().equals(soloSol)).findFirst().orElseThrow();
        assertThat(s.tieneCertificado()).isFalse();
        assertThat(s.tieneCredencialesSol()).isTrue();
    }

    /** Unas credenciales a medias (solo el usuario SOL) no sirven para enviar: no cuentan como cargadas. */
    @Test void unaClaveSolSinUsuarioONoCuentaComoCargada() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID e = empresaConId(cuenta, "20100066603", "COMERCIAL ANDINA SAC");
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ? WHERE id = ?", new byte[]{3}, e);

        assertThat(repo.detalle(cuenta).orElseThrow().empresas().get(0).tieneCredencialesSol()).isFalse();
    }

    @Test void losComprobantesRecientesSonLosUltimosDeTodasLasEmpresasDeLaCuentaYNoDeOtras() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID a = empresaConId(cuenta, "20100066603", "EMPRESA A");
        UUID b = empresaConId(cuenta, "20100066611", "EMPRESA B");
        UUID ajena = empresaConId(cuenta("Ajena", "ajena@x.pe", T0), "20100066629", "EMPRESA AJENA");
        for (int i = 1; i <= 12; i++) comprobante(a, "F001", i, "2026-09-" + String.format("%02d", i), "ACEPTADO", "118.00", T0.plusSeconds(i));
        comprobante(b, "F001", 1, "2026-09-20", "RECHAZADO", "50.00", T0.plusSeconds(100));
        comprobante(ajena, "F001", 1, "2026-09-25", "ACEPTADO", "999.00", T0.plusSeconds(200));

        var cs = repo.detalle(cuenta).orElseThrow().comprobantes();

        assertThat(cs).as("tope de 10, de los más recientes a los más antiguos").hasSize(10);
        assertThat(cs.get(0).ruc()).isEqualTo("20100066611");
        assertThat(cs.get(0).estado()).isEqualTo("RECHAZADO");
        assertThat(cs.get(0).total()).isEqualByComparingTo("50.00");
        assertThat(cs.get(0).moneda()).isEqualTo("PEN");
        assertThat(cs.get(0).empresaId()).isEqualTo(b);
        assertThat(cs.get(1)).satisfies(c -> {
            assertThat(c.ruc()).isEqualTo("20100066603");
            assertThat(c.serie()).isEqualTo("F001");
            assertThat(c.numero()).isEqualTo(12);
            assertThat(c.fechaEmision()).isEqualTo(java.time.LocalDate.of(2026, 9, 12));
            assertThat(c.tipo()).isEqualTo("01");
        });
        assertThat(cs).extracting(c -> c.ruc()).doesNotContain("20100066629");
        assertThat(cs.stream().filter(c -> c.ruc().equals("20100066603")).map(c -> c.numero()).filter(n -> n < 4))
                .as("de A quedaron fuera los tres más antiguos (1, 2 y 3)").isEmpty();
        assertThat(cs.stream().filter(c -> c.ruc().equals("20100066603")).count()).isEqualTo(9);
    }

    @Test void losEventosSonLasAccionesDelAdministradorSobreEstaCuenta() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        UUID otra = cuenta("Otra", "otra@x.pe", T0);
        var auditoria = new JdbcAuditoriaAdminRepository(jdbc);
        var admin = pe.factura.domain.plataforma.ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
        auditoria.registrar(pe.factura.domain.plataforma.RegistroAuditoria.de(admin, pe.factura.domain.plataforma.AccionAdmin.CREAR_CUENTA, cuenta, null, "ruc=20100066603", T0));
        auditoria.registrar(pe.factura.domain.plataforma.RegistroAuditoria.de(pe.factura.domain.plataforma.ActorAdmin.clavePlataforma("10.0.0.1"),
                pe.factura.domain.plataforma.AccionAdmin.CREAR_TENANT, cuenta, null, "ruc=20100066611", T0.plusSeconds(60)));
        auditoria.registrar(pe.factura.domain.plataforma.RegistroAuditoria.de(admin, pe.factura.domain.plataforma.AccionAdmin.CREAR_CUENTA, otra, null, "ruc=otra", T0.plusSeconds(120)));
        for (int i = 0; i < 12; i++)
            auditoria.registrar(pe.factura.domain.plataforma.RegistroAuditoria.de(admin, pe.factura.domain.plataforma.AccionAdmin.CREAR_TENANT, cuenta, null, "n=" + i, T0.plusSeconds(1000 + i)));

        var eventos = repo.detalle(cuenta).orElseThrow().eventos();

        assertThat(eventos).as("tope de 10, de los más recientes a los más antiguos").hasSize(10);
        assertThat(eventos.get(0).detalle()).isEqualTo("n=11");
        assertThat(eventos.get(0).accion()).isEqualTo("CREAR_TENANT");
        assertThat(eventos.get(0).actor()).isEqualTo("ADMINISTRADOR");
        assertThat(eventos.get(0).ocurridoEn()).isEqualTo(T0.plusSeconds(1011));
        assertThat(eventos).extracting(e -> e.detalle()).doesNotContain("ruc=otra");

        var delTodo = repo.detalle(otra).orElseThrow().eventos();
        assertThat(delTodo).extracting(e -> e.detalle()).containsExactly("ruc=otra");
    }

    @Test void laClavePlataformaSeVeComoTal() {
        UUID cuenta = cuenta("Mi negocio", "ana@negocio.pe", T0);
        new JdbcAuditoriaAdminRepository(jdbc).registrar(pe.factura.domain.plataforma.RegistroAuditoria.de(pe.factura.domain.plataforma.ActorAdmin.clavePlataforma("10.0.0.1"),
                pe.factura.domain.plataforma.AccionAdmin.CREAR_TENANT, cuenta, null, "x", T0));

        assertThat(repo.detalle(cuenta).orElseThrow().eventos().get(0).actor()).isEqualTo("CLAVE_PLATAFORMA");
    }

    /** El detalle de una cuenta con mucha historia no recorre todos los comprobantes de sus empresas: cada página lee por índice. */
    @Test void losComprobantesRecientesSeLeenPorIndiceDeCadaEmpresa() {
        assertThat(plan("SELECT id FROM documento WHERE tenant_id = '" + UUID.randomUUID() + "' ORDER BY fecha_emision DESC, created_at DESC LIMIT 10"))
                .contains("ix_documento_tenant_fecha");
    }

    @Test void laBitacoraDeUnaCuentaSeLeePorIndice() {
        assertThat(plan("SELECT id FROM auditoria_admin WHERE cuenta_id = '" + UUID.randomUUID() + "' ORDER BY ocurrido_en DESC LIMIT 10")).contains("ix_auditoria_cuenta");
    }

    @Test void elOrdenDelListadoSeApoyaEnUnIndice() {
        // Sin él, cada página ordena todas las cuentas.
        assertThat(plan("SELECT id FROM cuenta ORDER BY created_at DESC, id LIMIT 10")).contains("ix_cuenta_alta");
    }

    // --- #182: estado de la cuenta ------------------------------------------------------------------------------------------------

    @Test void elListadoDiceDesdeCuandoEstaSuspendidaCadaCuentaYLasActivasNo() {
        UUID suspendida = cuenta("Suspendida", "sus@x.pe", T0);
        cuenta("Activa", "act@x.pe", T0.plusSeconds(60));
        jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(300)), suspendida);

        List<CuentaResumen> filas = repo.listar(Filtro.NINGUNO, 1, 20);

        assertThat(filas).extracting(CuentaResumen::email).containsExactly("act@x.pe", "sus@x.pe");
        assertThat(filas.get(0).suspendidaEn()).isNull();
        assertThat(filas.get(1).suspendidaEn()).isEqualTo(T0.plusSeconds(300));
    }

    @Test void elDetalleDiceDesdeCuandoEstaSuspendida() {
        UUID id = cuenta("Mi negocio", "ana@negocio.pe", T0);
        assertThat(repo.detalle(id).orElseThrow().suspendidaEn()).isNull();

        jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(300)), id);

        assertThat(repo.detalle(id).orElseThrow().suspendidaEn()).isEqualTo(T0.plusSeconds(300));
    }

    /** Suspender no esconde a la cuenta del listado: el administrador tiene que poder encontrarla para reactivarla. */
    @Test void unaCuentaSuspendidaSigueApareciendoEnElListadoYEnLaBusqueda() {
        UUID id = cuenta("Suspendida", "sus@x.pe", T0);
        jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ?", Timestamp.from(T0), id);

        assertThat(repo.contar(Filtro.NINGUNO)).isEqualTo(1);
        assertThat(repo.listar(new Filtro("sus@"), 1, 20)).hasSize(1);
    }

    // --- #201: baja lógica ----------------------------------------------------------------------------------------------------------

    UUID deBaja(String nombre, String email, Instant creada) {
        UUID id = cuenta(nombre, email, creada);
        jdbc.update("UPDATE cuenta SET baja_en = ? WHERE id = ?", Timestamp.from(creada.plusSeconds(500)), id);
        return id;
    }

    /** El listado operativo por defecto: la cuenta dada de baja desaparece, y el total (la cabecera de paginación) la descuenta también. */
    @Test void lasCuentasDeBajaNoSalenEnElListadoNiEnElTotalPorDefecto() {
        cuenta("Activa", "act@x.pe", T0);
        deBaja("Se fue", "baja@x.pe", T0.plusSeconds(60));

        assertThat(emails(repo.listar(Filtro.NINGUNO, 1, 20))).containsExactly("act@x.pe");
        assertThat(repo.contar(Filtro.NINGUNO)).isEqualTo(1);
    }

    @Test void conIncluidasSalenLasDosYSoloDevuelveUnicamenteLasDeBaja() {
        cuenta("Activa", "act@x.pe", T0);
        deBaja("Se fue", "baja@x.pe", T0.plusSeconds(60));

        assertThat(emails(repo.listar(new Filtro(null, VisibilidadDeBajas.INCLUIDAS), 1, 20))).containsExactly("baja@x.pe", "act@x.pe");
        assertThat(repo.contar(new Filtro(null, VisibilidadDeBajas.INCLUIDAS))).isEqualTo(2);
        assertThat(emails(repo.listar(new Filtro(null, VisibilidadDeBajas.SOLO), 1, 20))).containsExactly("baja@x.pe");
        assertThat(repo.contar(new Filtro(null, VisibilidadDeBajas.SOLO))).isEqualTo(1);
    }

    @Test void laFilaDiceDesdeCuandoEstaDeBaja() {
        deBaja("Se fue", "baja@x.pe", T0);
        cuenta("Activa", "act@x.pe", T0.plusSeconds(60));

        List<CuentaResumen> filas = repo.listar(new Filtro(null, VisibilidadDeBajas.INCLUIDAS), 1, 20);

        assertThat(filas.get(0).bajaEn()).isNull();
        assertThat(filas.get(1).bajaEn()).isEqualTo(T0.plusSeconds(500));
    }

    /** La visibilidad y la búsqueda se combinan con «y»: buscar a una cuenta de baja por su nombre no la encuentra a menos que se pida verla. */
    @Test void laBusquedaNoEncuentraUnaCuentaDeBajaSalvoQueSePidaVerla() {
        deBaja("Panadería Sol", "ana@sol.pe", T0);

        assertThat(repo.listar(new Filtro("sol"), 1, 20)).isEmpty();
        assertThat(repo.contar(new Filtro("sol"))).isZero();
        assertThat(emails(repo.listar(new Filtro("sol", VisibilidadDeBajas.INCLUIDAS), 1, 20))).containsExactly("ana@sol.pe");
        assertThat(emails(repo.listar(new Filtro("sol", VisibilidadDeBajas.SOLO), 1, 20))).containsExactly("ana@sol.pe");
    }

    /** Una cuenta de baja y suspendida: la baja manda en el listado (no sale) y la suspensión se conserva al verla. */
    @Test void laBajaNoPisaLaSuspension() {
        UUID id = deBaja("Doble", "doble@x.pe", T0);
        jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(10)), id);

        CuentaResumen fila = repo.listar(new Filtro(null, VisibilidadDeBajas.SOLO), 1, 20).get(0);

        assertThat(fila.suspendidaEn()).isEqualTo(T0.plusSeconds(10));
        assertThat(fila.bajaEn()).isEqualTo(T0.plusSeconds(500));
    }

    /** Se conserva todo lo que la ley obliga: el detalle de una cuenta de baja se abre igual, con sus empresas, y dice desde cuándo está de baja. */
    @Test void elDetalleDeUnaCuentaDeBajaSeAbreIgualYDiceDesdeCuando() {
        UUID id = deBaja("Se fue", "baja@x.pe", T0);
        empresa(id, "20100066603", "SE FUE SAC");

        var detalle = repo.detalle(id).orElseThrow();

        assertThat(detalle.bajaEn()).isEqualTo(T0.plusSeconds(500));
        assertThat(detalle.empresas()).hasSize(1);
        assertThat(repo.detalle(cuenta("Activa", "act@x.pe", T0)).orElseThrow().bajaEn()).isNull();
    }
}
