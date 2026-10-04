package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.FilaCertificado;
import pe.factura.application.port.out.AvisosRepository.FilaSol;
import pe.factura.application.port.out.AvisosRepository.Situacion;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.tenant.FalloDeAutenticacionSol;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los avisos a los clientes (#197), con Postgres real: qué empresas tienen el certificado en riesgo (con los bordes de los 30 días), cuáles tienen la SOL rechazada (la misma regla
 * que {@code FalloDeAutenticacionSol}), a quién se les escribe, y que reservar un aviso sea atómico: dos administradores a la vez dejan uno solo.
 */
class JdbcAvisosRepositoryTest extends PersistenciaTestBase {
    static final LocalDate HOY = LocalDate.of(2026, 10, 15);
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcAvisosRepository repo = new JdbcAvisosRepository(jdbc);

    /** Una empresa con el certificado que vence {@code dias} días después de hoy (negativo: ya venció); {@code null}: sin certificado. */
    UUID empresa(String razon, Integer dias) {
        UUID t = tenantDePrueba();
        jdbc.update("UPDATE tenant SET razon_social = ? WHERE id = ?", razon, t);
        if (dias != null) jdbc.update("UPDATE tenant SET cert_pkcs12_enc = '\\x01'::bytea, cert_vigencia_hasta = ? WHERE id = ?", Date.valueOf(HOY.plusDays(dias)), t);
        return t;
    }

    UUID cuenta(UUID tenant, String nombre, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', now())", id, nombre, email);
        jdbc.update("UPDATE tenant SET cuenta_id = ? WHERE id = ?", id, tenant);
        return id;
    }

    void documentoConError(UUID tenant, String estado, String ultimoError, Instant actualizado) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, ultimo_error, updated_at) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'x', ?, ?)",
                UUID.randomUUID(), tenant, NUMERO.getAndIncrement(), Date.valueOf(HOY), estado, ultimoError, Timestamp.from(actualizado));
    }

    AvisoRegistrado aviso(UUID empresa, UUID cuenta, MotivoDeAviso motivo, Instant cuando) {
        return new AvisoRegistrado(UUID.randomUUID(), empresa, cuenta, motivo, "ana@negocio.pe", cuando, null);
    }

    /** Inserta un aviso sin pasar por la reserva (que bloquea lo repetido): para sembrar un historial. */
    void sembrar(AvisoRegistrado a) {
        jdbc.update("INSERT INTO aviso_a_cliente (id, tenant_id, cuenta_id, motivo, destinatario, enviado_en, enviado_por) VALUES (?, ?, ?, ?, ?, ?, ?)",
                a.id(), a.empresaId(), a.cuentaId(), a.motivo().name(), a.destinatario(), Timestamp.from(a.enviadoEn()), a.enviadoPor());
    }

    List<UUID> ids(List<FilaCertificado> filas) { return filas.stream().map(FilaCertificado::empresaId).toList(); }

    // --- certificados ------------------------------------------------------------------------------------------------------------------

    @Test void entranLosVencidosYLosQueVencenEnMenosDeTreintaDiasYNingunOtro() {
        UUID vencido = empresa("VENCIDA SAC", -5);
        UUID ultimoDia = empresa("ULTIMO DIA SAC", 0);
        UUID enDiecinueve = empresa("DIECINUEVE SAC", 29);
        empresa("TREINTA SAC", 30);
        empresa("VIGENTE SAC", 200);
        empresa("SIN CERTIFICADO SAC", null);
        UUID sinFecha = empresa("SIN FECHA SAC", null);
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = '\\x01'::bytea WHERE id = ?", sinFecha);

        List<FilaCertificado> filas = repo.certificados(HOY, 1, 50);

        assertThat(ids(filas)).containsExactly(vencido, ultimoDia, enDiecinueve);
        assertThat(repo.contarCertificados(HOY)).isEqualTo(3);
    }

    @Test void dicenSuEstadoSuVigenciaYLosDiasQueLeQuedan() {
        empresa("VENCIDA SAC", -5);
        empresa("ULTIMO DIA SAC", 0);
        empresa("POR VENCER SAC", 12);

        List<FilaCertificado> filas = repo.certificados(HOY, 1, 50);

        assertThat(filas).extracting(FilaCertificado::estado).containsExactly(EstadoCertificado.VENCIDO, EstadoCertificado.POR_VENCER, EstadoCertificado.POR_VENCER);
        assertThat(filas).extracting(FilaCertificado::diasRestantes).containsExactly(-5, 0, 12);
        assertThat(filas).extracting(FilaCertificado::vigenteHasta).containsExactly(HOY.minusDays(5), HOY, HOY.plusDays(12));
        assertThat(filas).extracting(FilaCertificado::razonSocial).containsExactly("VENCIDA SAC", "ULTIMO DIA SAC", "POR VENCER SAC");
    }

    @Test void van_deLaMasUrgenteALaMenos() {
        UUID tres = empresa("A", 3);
        UUID vencidaHaceMucho = empresa("B", -40);
        UUID veinte = empresa("C", 20);
        UUID vencidaAyer = empresa("D", -1);

        assertThat(ids(repo.certificados(HOY, 1, 50))).containsExactly(vencidaHaceMucho, vencidaAyer, tres, veinte);
    }

    @Test void dosConLaMismaFechaSeDesempatanPorSuId() {
        UUID a = empresa("A", 5);
        UUID b = empresa("B", 5);

        assertThat(ids(repo.certificados(HOY, 1, 50))).containsExactlyElementsOf(java.util.stream.Stream.of(a, b).sorted(java.util.Comparator.comparing(UUID::toString)).toList());
    }

    @Test void traeLaCuentaDeLaEmpresaOCamposNulosSiNoTiene() {
        UUID conCuenta = empresa("CON CUENTA SAC", 5);
        UUID cuenta = cuenta(conCuenta, "Ana Pérez", "ana@negocio.pe");
        empresa("INTEGRADA SAC", 6);

        List<FilaCertificado> filas = repo.certificados(HOY, 1, 50);

        assertThat(filas.get(0).cuenta().cuentaId()).isEqualTo(cuenta);
        assertThat(filas.get(0).cuenta().nombre()).isEqualTo("Ana Pérez");
        assertThat(filas.get(0).cuenta().email()).isEqualTo("ana@negocio.pe");
        assertThat(filas.get(1).cuenta().cuentaId()).isNull();
        assertThat(filas.get(1).cuenta().email()).isNull();
    }

    @Test void unaCuentaDadaDeBajaYaNoSeAvisa() {
        UUID deBaja = empresa("DE BAJA SAC", -3);
        cuenta(deBaja, "Se fue", "se.fue@negocio.pe");
        jdbc.update("UPDATE cuenta SET baja_en = now() WHERE id = (SELECT cuenta_id FROM tenant WHERE id = ?)", deBaja);
        UUID activa = empresa("ACTIVA SAC", -3);
        cuenta(activa, "Sigue", "sigue@negocio.pe");

        assertThat(ids(repo.certificados(HOY, 1, 50))).containsExactly(activa);
        assertThat(repo.contarCertificados(HOY)).isEqualTo(1);
    }

    @Test void paginaSinRepetirNiSaltarYElTotalNoDependeDeLaPagina() {
        List<UUID> creadas = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) creadas.add(empresa("E" + i, i));

        assertThat(ids(repo.certificados(HOY, 1, 2))).containsExactly(creadas.get(0), creadas.get(1));
        assertThat(ids(repo.certificados(HOY, 2, 2))).containsExactly(creadas.get(2), creadas.get(3));
        assertThat(ids(repo.certificados(HOY, 3, 2))).containsExactly(creadas.get(4));
        assertThat(ids(repo.certificados(HOY, 4, 2))).isEmpty();
        assertThat(repo.contarCertificados(HOY)).isEqualTo(5);
    }

    @Test void conOtroDiaDeHoyCambiaLoQueEstaEnRiesgo() {
        UUID e = empresa("E", 40);

        assertThat(ids(repo.certificados(HOY, 1, 50))).isEmpty();
        assertThat(ids(repo.certificados(HOY.plusDays(15), 1, 50))).containsExactly(e);
        assertThat(repo.certificados(HOY.plusDays(15), 1, 50).get(0).diasRestantes()).isEqualTo(25);
    }

    // --- credenciales SOL --------------------------------------------------------------------------------------------------------------

    @Test void entranLasEmpresasConEnviosAtascadosPorLasCredencialesYNingunaOtra() {
        UUID sol = empresa("SOL SAC", 200);
        documentoConError(sol, "ERROR_ENVIO", "0102 - Usuario o contraseña incorrectos", AHORA);
        UUID http401 = empresa("HTTP 401 SAC", 200);
        documentoConError(http401, "ERROR_ENVIO", "0000 - SUNAT respondió HTTP 401 en 2 intentos (revisar credenciales SOL/URL)", AHORA);
        UUID caida = empresa("CAIDA SAC", 200);
        documentoConError(caida, "ERROR_ENVIO", "0109 - El sistema no puede responder su solicitud", AHORA);
        UUID resuelta = empresa("RESUELTA SAC", 200);
        documentoConError(resuelta, "ACEPTADO", "0102 - Usuario o contraseña incorrectos", AHORA);
        empresa("SIN ERRORES SAC", 200);

        List<FilaSol> filas = repo.credencialesSol(1, 50);

        assertThat(filas).extracting(FilaSol::empresaId).containsExactlyInAnyOrder(sol, http401);
        assertThat(repo.contarCredencialesSol()).isEqualTo(2);
    }

    /** El SQL y {@code FalloDeAutenticacionSol} deciden lo mismo: una prueba por cada texto. */
    @Test void elSqlYElDominioDicenLoMismoDeCadaError() {
        List<String> errores = List.of("0102 - x", "0103 - x", "0104 - x", "0105 - x", "0106 - x", "0111 - x", "0107 - x", "0101 - x", "0109 - x", "0100 - x", "0000 - SUNAT respondió HTTP 401",
                "0000 - SUNAT respondió HTTP 503", "0000 - SUNAT respondió HTTP 40", "INFRA - x", "01020 - x", "0102x - x", "x 0102 - x", "texto con HTTP 401 en el medio");
        for (String error : errores) {
            UUID e = empresa("E " + error, 200);
            documentoConError(e, "ERROR_ENVIO", error, AHORA);
            boolean entra = repo.credencialesSol(1, 500).stream().anyMatch(f -> f.empresaId().equals(e));
            assertThat(entra).as(error).isEqualTo(FalloDeAutenticacionSol.esUno(error));
        }
    }

    @Test void cuentaLosComprobantesAtascadosYDiceCuandoFalloElUltimoYQueDijoSunat() {
        UUID e = empresa("SOL SAC", 200);
        documentoConError(e, "ERROR_ENVIO", "0102 - viejo", AHORA.minus(Duration.ofHours(5)));
        documentoConError(e, "ERROR_ENVIO", "0104 - La clave ingresada es incorrecta", AHORA.minus(Duration.ofMinutes(10)));
        documentoConError(e, "ERROR_ENVIO", "0109 - servicio caído", AHORA);
        documentoConError(e, "ACEPTADO", "0102 - de un comprobante ya aceptado", AHORA);

        FilaSol f = repo.credencialesSol(1, 50).get(0);

        assertThat(f.comprobantesAfectados()).isEqualTo(2);
        assertThat(f.ultimoFallo()).isEqualTo(AHORA.minus(Duration.ofMinutes(10)));
        assertThat(f.ultimoError()).isEqualTo("0104 - La clave ingresada es incorrecta");
        assertThat(f.ruc()).hasSize(11);
        assertThat(f.razonSocial()).isEqualTo("SOL SAC");
    }

    @Test void vanDeLaQueTieneMasComprobantesAtascadosALaQueTieneMenos() {
        UUID uno = empresa("UNO", 200);
        UUID tres = empresa("TRES", 200);
        UUID dos = empresa("DOS", 200);
        documentoConError(uno, "ERROR_ENVIO", "0102 - x", AHORA);
        for (int i = 0; i < 3; i++) documentoConError(tres, "ERROR_ENVIO", "0102 - x", AHORA);
        for (int i = 0; i < 2; i++) documentoConError(dos, "ERROR_ENVIO", "0102 - x", AHORA);

        assertThat(repo.credencialesSol(1, 50)).extracting(FilaSol::empresaId).containsExactly(tres, dos, uno);
    }

    @Test void traeLaCuentaYExcluyeLasCuentasDeBaja() {
        UUID conCuenta = empresa("CON CUENTA", 200);
        UUID cuenta = cuenta(conCuenta, "Ana", "ana@negocio.pe");
        documentoConError(conCuenta, "ERROR_ENVIO", "0102 - x", AHORA);
        UUID deBaja = empresa("DE BAJA", 200);
        cuenta(deBaja, "Se fue", "se.fue@negocio.pe");
        jdbc.update("UPDATE cuenta SET baja_en = now() WHERE id = (SELECT cuenta_id FROM tenant WHERE id = ?)", deBaja);
        documentoConError(deBaja, "ERROR_ENVIO", "0102 - x", AHORA);
        UUID integrada = empresa("INTEGRADA", 200);
        documentoConError(integrada, "ERROR_ENVIO", "0102 - x", AHORA);

        List<FilaSol> filas = repo.credencialesSol(1, 50);

        assertThat(filas).extracting(FilaSol::empresaId).containsExactlyInAnyOrder(conCuenta, integrada);
        assertThat(filas.stream().filter(f -> f.empresaId().equals(conCuenta)).findFirst().orElseThrow().cuenta().cuentaId()).isEqualTo(cuenta);
        assertThat(filas.stream().filter(f -> f.empresaId().equals(integrada)).findFirst().orElseThrow().cuenta().email()).isNull();
    }

    @Test void paginaLasCredenciales() {
        for (int i = 0; i < 3; i++) documentoConError(empresa("E" + i, 200), "ERROR_ENVIO", "0102 - x", AHORA);

        assertThat(repo.credencialesSol(1, 2)).hasSize(2);
        assertThat(repo.credencialesSol(2, 2)).hasSize(1);
        assertThat(repo.credencialesSol(3, 2)).isEmpty();
        assertThat(repo.contarCredencialesSol()).isEqualTo(3);
    }

    // --- la situación de una empresa ---------------------------------------------------------------------------------------------------

    @Test void laSituacionDiceElCertificadoLaCuentaYCuantosEnviosFallanPorLaSol() {
        UUID e = empresa("MI EMPRESA SAC", 12);
        UUID cuenta = cuenta(e, "Ana", "ana@negocio.pe");
        documentoConError(e, "ERROR_ENVIO", "0102 - x", AHORA);
        documentoConError(e, "ERROR_ENVIO", "0102 - y", AHORA);
        documentoConError(e, "ERROR_ENVIO", "0109 - caído", AHORA);

        Situacion s = repo.situacionDe(e, HOY).orElseThrow();

        assertThat(s.empresaId()).isEqualTo(e);
        assertThat(s.razonSocial()).isEqualTo("MI EMPRESA SAC");
        assertThat(s.ruc()).hasSize(11);
        assertThat(s.certificado()).isEqualTo(EstadoCertificado.POR_VENCER);
        assertThat(s.vigenteHasta()).isEqualTo(HOY.plusDays(12));
        assertThat(s.diasRestantes()).isEqualTo(12);
        assertThat(s.fallosDeSol()).isEqualTo(2);
        assertThat(s.cuenta().cuentaId()).isEqualTo(cuenta);
        assertThat(s.cuenta().email()).isEqualTo("ana@negocio.pe");
    }

    @Test void unaEmpresaSinCertificadoNoTieneVigenciaNiDias() {
        UUID e = empresa("SIN CERT", null);

        Situacion s = repo.situacionDe(e, HOY).orElseThrow();

        assertThat(s.certificado()).isEqualTo(EstadoCertificado.SIN_CERTIFICADO);
        assertThat(s.vigenteHasta()).isNull();
        assertThat(s.diasRestantes()).isNull();
        assertThat(s.fallosDeSol()).isZero();
        assertThat(s.cuenta().cuentaId()).isNull();
    }

    @Test void unaEmpresaVigenteTieneSuVigenciaYNoEstaEnRiesgo() {
        UUID e = empresa("VIGENTE", 200);

        Situacion s = repo.situacionDe(e, HOY).orElseThrow();

        assertThat(s.certificado()).isEqualTo(EstadoCertificado.VIGENTE);
        assertThat(s.diasRestantes()).isEqualTo(200);
    }

    @Test void unaEmpresaQueNoExisteNoTieneSituacion() {
        assertThat(repo.situacionDe(UUID.randomUUID(), HOY)).isEmpty();
    }

    // --- los avisos registrados --------------------------------------------------------------------------------------------------------

    @Test void reservarRegistraElAvisoSiNoHayOtroReciente() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        AvisoRegistrado a = aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA);

        Optional<AvisoRegistrado> previo = uow.ejecutar(() -> repo.reservar(a, AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(previo).isEmpty();
        Map<String, Object> fila = jdbc.queryForMap("SELECT tenant_id, cuenta_id, motivo, destinatario, enviado_en, enviado_por FROM aviso_a_cliente WHERE id = ?", a.id());
        assertThat(fila.get("tenant_id")).isEqualTo(e);
        assertThat(fila.get("cuenta_id")).isEqualTo(c);
        assertThat(fila.get("motivo")).isEqualTo("CERTIFICADO_POR_VENCER");
        assertThat(fila.get("destinatario")).isEqualTo("ana@negocio.pe");
        assertThat(((Timestamp) fila.get("enviado_en")).toInstant()).isEqualTo(AHORA);
        assertThat(fila.get("enviado_por")).isNull();
    }

    @Test void guardaQuienLoMando() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        UUID admin = UUID.randomUUID();
        AvisoRegistrado a = new AvisoRegistrado(UUID.randomUUID(), e, c, MotivoDeAviso.CERTIFICADO_VENCIDO, "ana@negocio.pe", AHORA, admin);

        uow.ejecutar(() -> repo.reservar(a, AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(jdbc.queryForObject("SELECT enviado_por FROM aviso_a_cliente WHERE id = ?", UUID.class, a.id())).isEqualTo(admin);
    }

    @Test void siHayUnoRecienteDelMismoMotivoDevuelveEseYNoRegistraNada() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        AvisoRegistrado primero = aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(Duration.ofDays(2)));
        uow.ejecutar(() -> repo.reservar(primero, primero.enviadoEn().minus(MotivoDeAviso.ENFRIAMIENTO)));

        Optional<AvisoRegistrado> previo = uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(previo).hasValueSatisfying(p -> {
            assertThat(p.id()).isEqualTo(primero.id());
            assertThat(p.enviadoEn()).isEqualTo(primero.enviadoEn());
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Integer.class)).isEqualTo(1);
    }

    @Test void elBordeDeLaSemanaEsInclusivoParaAvisarDeNuevo() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        AvisoRegistrado primero = aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(MotivoDeAviso.ENFRIAMIENTO));
        uow.ejecutar(() -> repo.reservar(primero, primero.enviadoEn().minus(MotivoDeAviso.ENFRIAMIENTO)));

        Optional<AvisoRegistrado> exacto = uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(exacto).as("enviado justo hace una semana: ya se puede repetir").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Integer.class)).isEqualTo(2);
    }

    @Test void unSegundoMenosDeUnaSemanaTodaviaBloquea() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        AvisoRegistrado primero = aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(MotivoDeAviso.ENFRIAMIENTO).plusSeconds(1));
        uow.ejecutar(() -> repo.reservar(primero, primero.enviadoEn().minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)))).isPresent();
    }

    @Test void otroMotivoOOtraEmpresaNoBloquean() {
        UUID e = empresa("E", 5);
        UUID otra = empresa("OTRA", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        UUID c2 = cuenta(otra, "Beto", "beto@negocio.pe");
        uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minusSeconds(60)), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        assertThat(uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_VENCIDO, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)))).isEmpty();
        assertThat(uow.ejecutar(() -> repo.reservar(aviso(otra, c2, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Integer.class)).isEqualTo(3);
    }

    /** Dos administradores que avisan a la vez lo mismo a la misma empresa: uno reserva, el otro ve la reserva del primero. */
    @Test void dosReservasAlMismoTiempoDejanUnaSola() throws Exception {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Optional<AvisoRegistrado>>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));
                }));
            }
            salida.countDown();
            long reservaron = 0;
            for (Future<Optional<AvisoRegistrado>> f : futuros) if (f.get().isEmpty()) reservaron++;

            assertThat(reservaron).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void anularQuitaLaReservaYSePuedeVolverAReservar() {
        UUID e = empresa("E", 5);
        UUID c = cuenta(e, "Ana", "ana@negocio.pe");
        AvisoRegistrado a = aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA);
        uow.ejecutar(() -> repo.reservar(a, AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)));

        uow.ejecutar(() -> repo.anular(a.id()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Integer.class)).isZero();
        assertThat(uow.ejecutar(() -> repo.reservar(aviso(e, c, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA), AHORA.minus(MotivoDeAviso.ENFRIAMIENTO)))).isEmpty();
    }

    @Test void anularUnaReservaQueNoExisteNoHaceNada() {
        uow.ejecutar(() -> repo.anular(UUID.randomUUID()));
    }

    @Test void losUltimosAvisosSonElMasRecienteDeCadaEmpresaParaEseMotivo() {
        UUID a = empresa("A", 5);
        UUID b = empresa("B", 5);
        UUID sinAviso = empresa("C", 5);
        UUID ca = cuenta(a, "Ana", "ana@negocio.pe");
        UUID cb = cuenta(b, "Beto", "beto@negocio.pe");
        AvisoRegistrado viejoA = aviso(a, ca, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(Duration.ofDays(30)));
        AvisoRegistrado nuevoA = aviso(a, ca, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(Duration.ofDays(1)));
        AvisoRegistrado deB = aviso(b, cb, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA.minus(Duration.ofDays(3)));
        AvisoRegistrado otroMotivo = aviso(a, ca, MotivoDeAviso.CERTIFICADO_VENCIDO, AHORA);
        for (AvisoRegistrado x : List.of(viejoA, nuevoA, deB, otroMotivo)) sembrar(x);

        Map<UUID, AvisoRegistrado> r = repo.ultimosAvisos(List.of(a, b, sinAviso), MotivoDeAviso.CERTIFICADO_POR_VENCER);

        assertThat(r).containsOnlyKeys(a, b);
        assertThat(r.get(a).id()).isEqualTo(nuevoA.id());
        assertThat(r.get(a).enviadoEn()).isEqualTo(nuevoA.enviadoEn());
        assertThat(r.get(a).destinatario()).isEqualTo("ana@negocio.pe");
        assertThat(r.get(b).id()).isEqualTo(deB.id());
    }

    @Test void losUltimosAvisosSoloMiranLasEmpresasPedidas() {
        UUID a = empresa("A", 5);
        UUID b = empresa("B", 5);
        UUID ca = cuenta(a, "Ana", "ana@negocio.pe");
        UUID cb = cuenta(b, "Beto", "beto@negocio.pe");
        sembrar(aviso(a, ca, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA));
        sembrar(aviso(b, cb, MotivoDeAviso.CERTIFICADO_POR_VENCER, AHORA));

        assertThat(repo.ultimosAvisos(List.of(a), MotivoDeAviso.CERTIFICADO_POR_VENCER)).containsOnlyKeys(a);
    }

    @Test void sinEmpresasNoHayNadaQueBuscar() {
        assertThat(repo.ultimosAvisos(List.of(), MotivoDeAviso.CERTIFICADO_POR_VENCER)).isEmpty();
    }
}
