package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.out.ColaDeErroresRepository.Fila;
import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cola global de errores (#196), con Postgres real: qué comprobantes entran (los mismos que dice {@code ClaseDeError}), su empresa y su cuenta, el reintento del outbox, los
 * filtros por clase, empresa y texto, el orden por urgencia y la paginación.
 */
class JdbcColaDeErroresRepositoryTest extends PersistenciaTestBase {
    static final AtomicLong NUMERO = new AtomicLong(1);
    static final Filtro TODOS = new Filtro(null, null, null);

    JdbcColaDeErroresRepository repo = new JdbcColaDeErroresRepository(jdbc);

    UUID empresa(String razonSocial) {
        UUID t = tenantDePrueba();
        jdbc.update("UPDATE tenant SET razon_social = ? WHERE id = ?", razonSocial, t);
        return t;
    }

    UUID cuenta(UUID tenant, String nombre, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', now())", id, nombre, email);
        jdbc.update("UPDATE tenant SET cuenta_id = ? WHERE id = ?", id, tenant);
        return id;
    }

    UUID documento(UUID tenant, EstadoDocumento estado, String cdrCodigo, LocalDate emision) {
        UUID id = UUID.randomUUID();
        long numero = NUMERO.getAndIncrement();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, intentos, ultimo_error, cdr_codigo, cdr_descripcion, created_at)
                VALUES (?, ?, '01', 'F001', ?, ?, ?, ?, 3, ?, ?, ?, ?)
                """, id, tenant, numero, java.sql.Date.valueOf(emision), estado.name(), "20100066603-01-F001-" + numero, estado == EstadoDocumento.RECHAZADO ? null : "0109 - SUNAT no responde",
                cdrCodigo, cdrCodigo == null ? null : "descripción " + cdrCodigo, Timestamp.from(Instant.parse("2026-10-01T10:00:00Z").plusSeconds(numero)));
        return id;
    }

    UUID documento(UUID tenant, EstadoDocumento estado, String cdrCodigo) { return documento(tenant, estado, cdrCodigo, LocalDate.of(2026, 10, 12)); }

    void envio(UUID tenant, UUID doc, Instant siguiente) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, 'ENVIAR', ?)", tenant, doc, Timestamp.from(siguiente));
    }

    List<UUID> ids(Filtro f) { return repo.listar(f, 1, 100).stream().map(Fila::comprobanteId).toList(); }

    // --- qué entra en la cola ---------------------------------------------------------------------------------------------------------------

    @Test void entranLosTresTiposDeProblemaYNingunOtro() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID envio = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        UUID formato = documento(t, EstadoDocumento.RECHAZADO, "1033");
        UUID plazo = documento(t, EstadoDocumento.FUERA_DE_PLAZO, null);
        for (EstadoDocumento e : EstadoDocumento.values())
            if (e != EstadoDocumento.ERROR_ENVIO && e != EstadoDocumento.FUERA_DE_PLAZO && e != EstadoDocumento.RECHAZADO) documento(t, e, null);
        documento(t, EstadoDocumento.RECHAZADO, "2324");

        assertThat(ids(TODOS)).containsExactlyInAnyOrder(envio, formato, plazo);
        assertThat(repo.contar(TODOS)).isEqualTo(3);
    }

    /** El SQL y {@code ClaseDeError} deciden lo mismo: cada código de borde, uno por uno. */
    @Test void unRechazoEntraSoloConUnCodigoDeFormatoComoDiceElDominio() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        for (String codigo : new String[]{"0999", "1000", "1033", "1999", "2000", "2324", "3999", "100", "abcd", "1a33"}) {
            UUID doc = documento(t, EstadoDocumento.RECHAZADO, codigo);
            boolean entra = ids(TODOS).contains(doc);
            assertThat(entra).as(codigo).isEqualTo(ClaseDeError.de(EstadoDocumento.RECHAZADO, codigo).isPresent());
        }
    }

    @Test void unRechazoSinCodigoNoEntra() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        documento(t, EstadoDocumento.RECHAZADO, null);

        assertThat(repo.contar(TODOS)).isZero();
    }

    @Test void trae_losDatosDelComprobanteSuEmpresaYSuCuenta() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID cuenta = cuenta(t, "Ana Pérez", "ana@negocio.pe");
        UUID doc = documento(t, EstadoDocumento.ERROR_ENVIO, null, LocalDate.of(2026, 10, 12));

        Fila f = repo.listar(TODOS, 1, 10).get(0);

        assertThat(f.comprobanteId()).isEqualTo(doc);
        assertThat(f.tenantId()).isEqualTo(t);
        assertThat(f.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(f.ruc()).hasSize(11);
        assertThat(f.cuentaId()).isEqualTo(cuenta);
        assertThat(f.cuentaNombre()).isEqualTo("Ana Pérez");
        assertThat(f.tipo()).isEqualTo("01");
        assertThat(f.serie()).isEqualTo("F001");
        assertThat(f.nombreArchivo()).startsWith("20100066603-01-F001-");
        assertThat(f.fechaEmision()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(f.estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        assertThat(f.intentos()).isEqualTo(3);
        assertThat(f.ultimoError()).isEqualTo("0109 - SUNAT no responde");
        assertThat(f.actualizadoEn()).isNotNull();
    }

    @Test void unaEmpresaDeIntegracionSinCuentaTambienEntra() {
        UUID t = empresa("INTEGRADA SAC");
        documento(t, EstadoDocumento.ERROR_ENVIO, null);

        Fila f = repo.listar(TODOS, 1, 10).get(0);

        assertThat(f.cuentaId()).isNull();
        assertThat(f.cuentaNombre()).isNull();
    }

    @Test void unRechazoTraeElCdrYUnErrorDeEnvioNo() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID formato = documento(t, EstadoDocumento.RECHAZADO, "1033");
        UUID envio = documento(t, EstadoDocumento.ERROR_ENVIO, null);

        List<Fila> filas = repo.listar(TODOS, 1, 10);

        Fila r = filas.stream().filter(f -> f.comprobanteId().equals(formato)).findFirst().orElseThrow();
        Fila e = filas.stream().filter(f -> f.comprobanteId().equals(envio)).findFirst().orElseThrow();
        assertThat(r.cdrCodigo()).isEqualTo("1033");
        assertThat(r.cdrDescripcion()).isEqualTo("descripción 1033");
        assertThat(e.cdrCodigo()).isNull();
    }

    // --- el reintento ----------------------------------------------------------------------------------------------------------------------

    @Test void diceCuandoLoReintentaElOutboxYNadaSiNoHayTarea() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID conTarea = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        UUID sinTarea = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        Instant proximo = Instant.parse("2026-10-15T18:30:00Z");
        envio(t, conTarea, proximo);

        List<Fila> filas = repo.listar(TODOS, 1, 10);

        assertThat(filas.stream().filter(f -> f.comprobanteId().equals(conTarea)).findFirst().orElseThrow().siguienteIntento()).isEqualTo(proximo);
        assertThat(filas.stream().filter(f -> f.comprobanteId().equals(sinTarea)).findFirst().orElseThrow().siguienteIntento()).isNull();
    }

    @Test void unaTareaDeOtraAccionNoEsElReintentoDelEnvio() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID doc = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, 'BAJA', now())", t, doc);

        assertThat(repo.listar(TODOS, 1, 10)).singleElement().extracting(Fila::siguienteIntento).isNull();
        assertThat(repo.contar(TODOS)).isEqualTo(1);
    }

    // --- los filtros ------------------------------------------------------------------------------------------------------------------------

    @Test void filtraPorClase() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID envio = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        UUID formato = documento(t, EstadoDocumento.RECHAZADO, "1001");
        UUID plazo = documento(t, EstadoDocumento.FUERA_DE_PLAZO, null);

        assertThat(ids(new Filtro(ClaseDeError.ERROR_DE_ENVIO, null, null))).containsExactly(envio);
        assertThat(ids(new Filtro(ClaseDeError.ERROR_DE_FORMATO, null, null))).containsExactly(formato);
        assertThat(ids(new Filtro(ClaseDeError.FUERA_DE_PLAZO, null, null))).containsExactly(plazo);
        assertThat(repo.contar(new Filtro(ClaseDeError.ERROR_DE_FORMATO, null, null))).isEqualTo(1);
    }

    @Test void filtraPorEmpresa() {
        UUID a = empresa("EMPRESA A SAC");
        UUID b = empresa("EMPRESA B SAC");
        UUID deA = documento(a, EstadoDocumento.ERROR_ENVIO, null);
        documento(b, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(new Filtro(null, a, null))).containsExactly(deA);
        assertThat(repo.contar(new Filtro(null, a, null))).isEqualTo(1);
        assertThat(ids(new Filtro(null, UUID.randomUUID(), null))).isEmpty();
    }

    @Test void sinFiltroTraeLasDeTodasLasEmpresas() {
        documento(empresa("EMPRESA A SAC"), EstadoDocumento.ERROR_ENVIO, null);
        documento(empresa("EMPRESA B SAC"), EstadoDocumento.FUERA_DE_PLAZO, null);

        assertThat(repo.contar(TODOS)).isEqualTo(2);
    }

    @Test void buscaPorRazonSocialSinDistinguirMayusculasNiTildes() {
        UUID panaderia = empresa("PANADERÍA SOL SAC");
        UUID ferreteria = empresa("FERRETERÍA LUNA SAC");
        UUID a = documento(panaderia, EstadoDocumento.ERROR_ENVIO, null);
        documento(ferreteria, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(new Filtro(null, null, "panaderia"))).containsExactly(a);
        assertThat(ids(new Filtro(null, null, "PANADERÍA"))).containsExactly(a);
        assertThat(ids(new Filtro(null, null, "sol s"))).containsExactly(a);
        assertThat(repo.contar(new Filtro(null, null, "panaderia"))).isEqualTo(1);
    }

    @Test void buscaPorElPrefijoDelRucNoPorUnFragmentoInterno() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        String ruc = jdbc.queryForObject("SELECT ruc FROM tenant WHERE id = ?", String.class, t);
        UUID doc = documento(t, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(new Filtro(null, null, ruc.substring(0, 6)))).containsExactly(doc);
        assertThat(ids(new Filtro(null, null, ruc.substring(4, 9)))).as("un fragmento interno de un RUC no identifica a nadie").isEmpty();
    }

    @Test void buscaPorElNombreOElCorreoDeLaCuenta() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        cuenta(t, "Ana Pérez", "ana@negocio.pe");
        UUID otra = empresa("OTRA SAC");
        UUID doc = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        documento(otra, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(new Filtro(null, null, "perez"))).containsExactly(doc);
        assertThat(ids(new Filtro(null, null, "ana@negocio"))).containsExactly(doc);
    }

    @Test void losComodinesDelTextoSeTomanLiterales() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        documento(t, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(repo.contar(new Filtro(null, null, "%"))).isZero();
        assertThat(repo.contar(new Filtro(null, null, "_"))).isZero();
        assertThat(repo.contar(new Filtro(null, null, "COMERCIAL_ANDINA"))).isZero();
    }

    @Test void losFiltrosSeCombinanConY() {
        UUID a = empresa("PANADERÍA SOL SAC");
        UUID b = empresa("PANADERÍA LUNA SAC");
        UUID envioA = documento(a, EstadoDocumento.ERROR_ENVIO, null);
        documento(a, EstadoDocumento.FUERA_DE_PLAZO, null);
        documento(b, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(new Filtro(ClaseDeError.ERROR_DE_ENVIO, a, "panaderia"))).containsExactly(envioA);
        assertThat(ids(new Filtro(ClaseDeError.ERROR_DE_ENVIO, b, "sol"))).isEmpty();
    }

    // --- el orden y la página -----------------------------------------------------------------------------------------------------------------

    @Test void van_deLaEmisionMasAntiguaALaMasReciente() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID reciente = documento(t, EstadoDocumento.ERROR_ENVIO, null, LocalDate.of(2026, 10, 14));
        UUID antigua = documento(t, EstadoDocumento.ERROR_ENVIO, null, LocalDate.of(2026, 10, 10));
        UUID media = documento(t, EstadoDocumento.FUERA_DE_PLAZO, null, LocalDate.of(2026, 10, 12));

        assertThat(ids(TODOS)).containsExactly(antigua, media, reciente);
    }

    @Test void conLaMismaFechaDesempataPorCuandoSeCreo() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID primero = documento(t, EstadoDocumento.ERROR_ENVIO, null);
        UUID segundo = documento(t, EstadoDocumento.ERROR_ENVIO, null);

        assertThat(ids(TODOS)).containsExactly(primero, segundo);
    }

    @Test void paginaSinRepetirNiSaltarYElTotalNoDependeDeLaPagina() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        List<UUID> creados = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) creados.add(documento(t, EstadoDocumento.ERROR_ENVIO, null));

        assertThat(repo.listar(TODOS, 1, 2).stream().map(Fila::comprobanteId)).containsExactly(creados.get(0), creados.get(1));
        assertThat(repo.listar(TODOS, 2, 2).stream().map(Fila::comprobanteId)).containsExactly(creados.get(2), creados.get(3));
        assertThat(repo.listar(TODOS, 3, 2).stream().map(Fila::comprobanteId)).containsExactly(creados.get(4));
        assertThat(repo.listar(TODOS, 4, 2)).isEmpty();
        assertThat(repo.contar(TODOS)).isEqualTo(5);
    }

    // --- ubicar -----------------------------------------------------------------------------------------------------------------------------

    @Test void ubicaUnComprobanteDeCualquierEstadoConSuEmpresa() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        UUID aceptado = documento(t, EstadoDocumento.ACEPTADO, null);

        assertThat(repo.ubicar(aceptado)).hasValueSatisfying(u -> {
            assertThat(u.tenantId()).isEqualTo(t);
            assertThat(u.nombreArchivo()).startsWith("20100066603-01-F001-");
        });
    }

    @Test void unComprobanteQueNoExisteNoSeUbica() {
        assertThat(repo.ubicar(UUID.randomUUID())).isEmpty();
    }

    // --- el índice --------------------------------------------------------------------------------------------------------------------------

    @Test void hayUnIndiceParcialSoloParaLoQueEstaEnProblema() {
        String def = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_documento_en_error'", String.class);

        assertThat(def).contains("fecha_emision").contains("ERROR_ENVIO").contains("FUERA_DE_PLAZO").contains("RECHAZADO");
    }

    /** Con muchos comprobantes buenos y unos pocos con problema, la cola no recorre la tabla: usa el índice parcial. */
    @Test void conMuchosComprobantesLaColaUsaElIndice() {
        UUID t = empresa("COMERCIAL ANDINA SAC");
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                SELECT gen_random_uuid(), ?, '01', 'F002', g, date '2026-09-01' + (g %% 30), 'ACEPTADO', 'x'
                FROM generate_series(1, 20000) g
                """.formatted(), t);
        documento(t, EstadoDocumento.ERROR_ENVIO, null);
        jdbc.execute("ANALYZE documento");

        String plan = String.join("\n", jdbc.queryForList("""
                EXPLAIN SELECT d.id FROM documento d
                WHERE (d.estado = 'ERROR_ENVIO' OR d.estado = 'FUERA_DE_PLAZO' OR (d.estado = 'RECHAZADO' AND d.cdr_codigo ~ '^1[0-9]{3}$'))
                ORDER BY d.fecha_emision, d.created_at, d.id LIMIT 20
                """, String.class));

        assertThat(plan).contains("ix_documento_en_error");
    }
}
