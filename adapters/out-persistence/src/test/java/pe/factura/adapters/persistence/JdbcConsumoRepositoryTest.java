package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.ConsumoRepository.ConsumoDeEmpresa;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contador de consumo (#192) con Postgres real. Es código de dinero y el error está en lo que **no** cuenta, así que los tests recorren cada caso que no cuenta:
 * un documento por cada estado, el borde de los meses, los reintentos, las bajas, los tipos y las empresas ajenas. Solo cuentan los comprobantes aceptados
 * (con o sin observaciones), por su fecha de emisión, dentro del mes calendario.
 */
class JdbcConsumoRepositoryTest extends PersistenciaTestBase {
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    static final LocalDate MITAD = LocalDate.of(2026, 10, 15);
    static final AtomicLong NUMERO = new AtomicLong(1);
    static final AtomicLong RUC = new AtomicLong(100);

    JdbcConsumoRepository repo = new JdbcConsumoRepository(jdbc);

    UUID cuenta(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(Instant.parse("2026-09-01T10:00:00Z")));
        return id;
    }

    UUID empresa(UUID cuenta, String razon) {
        UUID id = UUID.randomUUID();
        String ruc = "20" + String.format("%09d", RUC.getAndIncrement());
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, ?, 'BETA', ?)", id, ruc, razon, cuenta);
        return id;
    }

    void documento(UUID empresa, String tipo, EstadoDocumento estado, LocalDate fecha, int intentos) {
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, intentos)
                VALUES (?, ?, ?, 'F001', ?, ?, ?, 'archivo', ?)""", UUID.randomUUID(), empresa, tipo, NUMERO.getAndIncrement(), java.sql.Date.valueOf(fecha), estado.name(), intentos);
    }

    void documento(UUID empresa, EstadoDocumento estado, LocalDate fecha) { documento(empresa, "01", estado, fecha, 1); }

    // --- lo que cuenta ----------------------------------------------------------------------------------------------------------------------

    @Test void cuentaLosAceptadosYLosAceptadosConObservaciones() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, MITAD);
        documento(e, EstadoDocumento.ACEPTADO_CON_OBS, MITAD);

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(2);
    }

    @Test void losCuatroTiposDeDocumentoCuentan() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        for (String tipo : List.of("01", "03", "07", "08")) documento(e, tipo, EstadoDocumento.ACEPTADO, MITAD, 1);

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(4);
    }

    // --- lo que NO cuenta -------------------------------------------------------------------------------------------------------------------

    /** Un documento en cada uno de los demás estados: ninguno consume. Si aparece un estado nuevo, el test del dominio obliga a clasificarlo. */
    @Test void ningunOtroEstadoCuenta() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        for (EstadoDocumento estado : EstadoDocumento.values()) {
            if (estado.cuentaParaElConsumo()) continue;
            documento(e, estado, MITAD);
        }

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isZero();
    }

    @Test void losRechazadosYLosQueNoLlegaronNoCuentanAunqueSeHayanReintentado() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, "01", EstadoDocumento.RECHAZADO, MITAD, 3);
        documento(e, "01", EstadoDocumento.ERROR_ENVIO, MITAD, 5);
        documento(e, "01", EstadoDocumento.FUERA_DE_PLAZO, MITAD, 8);

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isZero();
    }

    /** Un documento reintentado varias veces hasta que SUNAT lo acepta es **un** documento, no uno por intento. */
    @Test void unComprobanteAceptadoTrasVariosReintentosCuentaUnaSolaVez() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, "01", EstadoDocumento.ACEPTADO, MITAD, 6);

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(1);
    }

    /** Las bajas no cuentan: la comunicación de baja es otra tabla, y el comprobante dado de baja ya no está aceptado. */
    @Test void laBajaNoCuentaNiComoComunicacionNiComoComprobanteAnulado() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, MITAD);
        UUID anulado = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', ?, ?, 'ANULADO', 'x')",
                anulado, e, NUMERO.getAndIncrement(), java.sql.Date.valueOf(MITAD));

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(1);
    }

    // --- el mes -----------------------------------------------------------------------------------------------------------------------------

    @Test void elMesVaDelPrimeroAlUltimoDiaInclusive() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 10, 1));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 10, 31));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 11, 1));

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(2);
        assertThat(repo.documentosDeEmpresa(e, YearMonth.of(2026, 9))).isEqualTo(1);
        assertThat(repo.documentosDeEmpresa(e, YearMonth.of(2026, 11))).isEqualTo(1);
    }

    @Test void diciembreYEneroSonMesesDistintos() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 12, 31));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2027, 1, 1));

        assertThat(repo.documentosDeEmpresa(e, YearMonth.of(2026, 12))).isEqualTo(1);
        assertThat(repo.documentosDeEmpresa(e, YearMonth.of(2027, 1))).isEqualTo(1);
    }

    @Test void elMismoMesDeOtroAnioNoSeMezcla() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2025, 10, 15));

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isZero();
    }

    @Test void unMesSinDocumentosEsCero() {
        UUID e = empresa(cuenta("a@negocio.pe"), "UNO SAC");

        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isZero();
    }

    @Test void unaEmpresaQueNoExisteTieneCero() {
        assertThat(repo.documentosDeEmpresa(UUID.randomUUID(), OCTUBRE)).isZero();
    }

    // --- por empresa y por cuenta -----------------------------------------------------------------------------------------------------------

    @Test void cadaEmpresaCuentaLoSuyo() {
        UUID c = cuenta("a@negocio.pe");
        UUID e1 = empresa(c, "UNO SAC");
        UUID e2 = empresa(c, "DOS SAC");
        documento(e1, EstadoDocumento.ACEPTADO, MITAD);
        documento(e2, EstadoDocumento.ACEPTADO, MITAD);
        documento(e2, EstadoDocumento.ACEPTADO, MITAD);

        assertThat(repo.documentosDeEmpresa(e1, OCTUBRE)).isEqualTo(1);
        assertThat(repo.documentosDeEmpresa(e2, OCTUBRE)).isEqualTo(2);
    }

    @Test void laCuentaListaCadaEmpresaConSuConsumoPorRucAunqueSea0() {
        UUID c = cuenta("a@negocio.pe");
        UUID e1 = empresa(c, "UNO SAC");
        UUID e2 = empresa(c, "DOS SAC");
        UUID sinNada = empresa(c, "TRES SAC");
        documento(e1, EstadoDocumento.ACEPTADO, MITAD);
        documento(e2, EstadoDocumento.ACEPTADO_CON_OBS, MITAD);
        documento(e2, EstadoDocumento.RECHAZADO, MITAD);

        List<ConsumoDeEmpresa> lista = repo.documentosPorEmpresaDeCuenta(c, OCTUBRE);

        assertThat(lista).extracting(ConsumoDeEmpresa::tenantId).containsExactly(e1, e2, sinNada);
        assertThat(lista).extracting(ConsumoDeEmpresa::documentos).containsExactly(1L, 1L, 0L);
        assertThat(lista).extracting(ConsumoDeEmpresa::razonSocial).containsExactly("UNO SAC", "DOS SAC", "TRES SAC");
        assertThat(lista.get(0).ruc()).startsWith("20");
    }

    /** Las empresas de otra cuenta no se mezclan ni en el total ni en la lista. */
    @Test void laCuentaNoCuentaLasEmpresasDeOtra() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");
        UUID ea = empresa(a, "A SAC");
        UUID eb = empresa(b, "B SAC");
        documento(ea, EstadoDocumento.ACEPTADO, MITAD);
        documento(eb, EstadoDocumento.ACEPTADO, MITAD);
        documento(eb, EstadoDocumento.ACEPTADO, MITAD);

        assertThat(repo.documentosPorEmpresaDeCuenta(a, OCTUBRE)).extracting(ConsumoDeEmpresa::tenantId).containsExactly(ea);
        assertThat(repo.documentosPorEmpresaDeCuenta(b, OCTUBRE)).extracting(ConsumoDeEmpresa::documentos).containsExactly(2L);
    }

    @Test void unaEmpresaSinCuentaNoApareceEnNingunaCuenta() {
        UUID c = cuenta("a@negocio.pe");
        UUID huerfana = tenantDePrueba();
        documento(huerfana, EstadoDocumento.ACEPTADO, MITAD);

        assertThat(repo.documentosPorEmpresaDeCuenta(c, OCTUBRE)).isEmpty();
    }

    @Test void unaCuentaSinEmpresasOQueNoExisteDaUnaListaVacia() {
        assertThat(repo.documentosPorEmpresaDeCuenta(cuenta("a@negocio.pe"), OCTUBRE)).isEmpty();
        assertThat(repo.documentosPorEmpresaDeCuenta(UUID.randomUUID(), OCTUBRE)).isEmpty();
    }

    @Test void laListaDeLaCuentaAplicaLasMismasReglasQueLaDeLaEmpresa() {
        UUID c = cuenta("a@negocio.pe");
        UUID e = empresa(c, "UNO SAC");
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 11, 1));
        documento(e, EstadoDocumento.ENVIADO, MITAD);
        documento(e, "03", EstadoDocumento.ACEPTADO, MITAD, 4);

        assertThat(repo.documentosPorEmpresaDeCuenta(c, OCTUBRE).get(0).documentos()).isEqualTo(1);
        assertThat(repo.documentosDeEmpresa(e, OCTUBRE)).isEqualTo(1);
    }
}
