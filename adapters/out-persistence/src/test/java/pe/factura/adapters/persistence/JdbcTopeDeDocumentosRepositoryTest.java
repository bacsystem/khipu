package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Entorno;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Lo que ocupa el tope de documentos del plan (#18), con Postgres real: los documentos de todas las empresas de la cuenta que consumieron o pueden llegar a
 * consumir, en el mes de su fecha de emisión o en toda la vida de la cuenta (#613), por entorno (#580, #613); y el bloqueo que impide que dos emisiones simultáneas tomen el último lugar libre.
 */
class JdbcTopeDeDocumentosRepositoryTest extends PersistenciaTestBase {
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    static final LocalDate MITAD = LocalDate.of(2026, 10, 15);
    static final AtomicLong NUMERO = new AtomicLong(1);
    static final AtomicLong RUC = new AtomicLong(500);

    JdbcTopeDeDocumentosRepository repo = new JdbcTopeDeDocumentosRepository(jdbc);

    UUID cuenta() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, id + "@negocio.pe", Timestamp.from(Instant.parse("2026-09-01T10:00:00Z")));
        return id;
    }

    UUID empresa(UUID cuenta) { return empresa(cuenta, "PRODUCCION"); }

    UUID empresa(UUID cuenta, String entorno) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, 'EMPRESA SAC', ?, ?)", id, "20" + String.format("%09d", RUC.getAndIncrement()), entorno, cuenta);
        return id;
    }

    void documento(UUID empresa, EstadoDocumento estado, LocalDate fecha) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'archivo')",
                UUID.randomUUID(), empresa, NUMERO.getAndIncrement(), Date.valueOf(fecha), estado.name());
    }

    /** Lo de Producción en el mes. */
    long ocupados(UUID cuenta, YearMonth mes) { return ocupados(cuenta, Entorno.PRODUCCION, mes); }

    long ocupados(UUID cuenta, Entorno entorno, YearMonth mes) { return uow.ejecutar(() -> repo.ocupadosBloqueando(cuenta, entorno, mes)); }

    /** Un documento por cada estado: ocupan los que dice el dominio, ni uno más. Si aparece un estado nuevo, el test del dominio obliga a clasificarlo. */
    @Test void ocupanLosEstadosQueDiceElDominio() {
        UUID c = cuenta();
        UUID e = empresa(c);
        for (EstadoDocumento estado : EstadoDocumento.values()) documento(e, estado, MITAD);

        long esperados = java.util.Arrays.stream(EstadoDocumento.values()).filter(EstadoDocumento::ocupaElTope).count();
        assertThat(ocupados(c, OCTUBRE)).isEqualTo(esperados).isEqualTo(7);
    }

    @Test void sumaTodasLasEmpresasDeLaCuentaYNadaDeOtra() {
        UUID c = cuenta();
        documento(empresa(c), EstadoDocumento.ACEPTADO, MITAD);
        documento(empresa(c), EstadoDocumento.ENVIADO, MITAD);
        documento(empresa(cuenta()), EstadoDocumento.ACEPTADO, MITAD);
        documento(tenantDePrueba(), EstadoDocumento.ACEPTADO, MITAD);

        assertThat(ocupados(c, OCTUBRE)).isEqualTo(2);
    }

    @Test void cuentaPorElMesDeLaFechaDeEmision() {
        UUID c = cuenta();
        UUID e = empresa(c);
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 10, 1));
        documento(e, EstadoDocumento.FIRMADO, LocalDate.of(2026, 10, 31));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 11, 1));

        assertThat(ocupados(c, OCTUBRE)).isEqualTo(2);
        assertThat(ocupados(c, YearMonth.of(2026, 9))).isEqualTo(1);
    }

    /** #580: las pruebas en Beta no ocupan el tope. Una cuenta con una empresa en cada entorno solo cuenta la de Producción. */
    @Test void lasPruebasEnBetaNoOcupanElTope() {
        UUID c = cuenta();
        UUID beta = empresa(c, "BETA");
        documento(beta, EstadoDocumento.ACEPTADO, MITAD);
        documento(beta, EstadoDocumento.ENVIADO, MITAD);
        documento(empresa(c), EstadoDocumento.ACEPTADO, MITAD);

        assertThat(ocupados(c, OCTUBRE)).isEqualTo(1);
    }

    /**
     * #580: cuenta el entorno en que se emitió cada documento, no el que la empresa tiene hoy. La empresa prueba en Beta y pasa a Producción a mitad de mes:
     * sus pruebas siguen sin ocupar el tope, y lo que emite después sí.
     */
    @Test void alPasarAProduccionLasPruebasAnterioresSiguenFuera() {
        UUID c = cuenta();
        UUID e = empresa(c, "BETA");
        for (int i = 0; i < 8; i++) documento(e, EstadoDocumento.ACEPTADO, MITAD);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE id = ?", e);
        documento(e, EstadoDocumento.ACEPTADO, MITAD);

        assertThat(ocupados(c, OCTUBRE)).isEqualTo(1);
    }

    /** #613: las pruebas en Beta tienen su propia bolsa: se cuentan solo las de Beta, de todas las empresas de la cuenta, con los mismos estados y en su mes. */
    @Test void lasPruebasEnBetaSeCuentanAparte() {
        UUID c = cuenta();
        UUID beta = empresa(c, "BETA");
        UUID otraBeta = empresa(c, "BETA");
        documento(beta, EstadoDocumento.ACEPTADO, MITAD);
        documento(beta, EstadoDocumento.ENVIADO, MITAD);
        documento(otraBeta, EstadoDocumento.ACEPTADO, MITAD);
        documento(otraBeta, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30));
        documento(empresa(c), EstadoDocumento.ACEPTADO, MITAD);
        documento(empresa(cuenta(), "BETA"), EstadoDocumento.ACEPTADO, MITAD);

        assertThat(ocupados(c, Entorno.BETA, OCTUBRE)).as("las tres de Beta de octubre, de sus dos empresas").isEqualTo(3);
        assertThat(ocupados(c, Entorno.BETA, YearMonth.of(2026, 9))).isEqualTo(1);
        assertThat(ocupados(c, Entorno.PRODUCCION, OCTUBRE)).as("Producción no cuenta las pruebas").isEqualTo(1);
    }

    /** #613: un tope en total (sin mes) cuenta toda la vida de la cuenta en Producción, sin las pruebas. */
    @Test void sinMesCuentaTodaLaVidaDeLaCuenta() {
        UUID c = cuenta();
        UUID e = empresa(c);
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2025, 1, 10));
        documento(e, EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30));
        documento(e, EstadoDocumento.ENVIADO, MITAD);
        documento(e, EstadoDocumento.RECHAZADO, MITAD);
        documento(empresa(c, "BETA"), EstadoDocumento.ACEPTADO, MITAD);
        documento(empresa(cuenta()), EstadoDocumento.ACEPTADO, MITAD);

        assertThat(ocupados(c, Entorno.PRODUCCION, null)).isEqualTo(3);
    }

    @Test void unaCuentaSinDocumentosOQueNoExisteEsCero() {
        assertThat(ocupados(cuenta(), OCTUBRE)).isZero();
        assertThat(ocupados(UUID.randomUUID(), OCTUBRE)).isZero();
    }

    /** Sin transacción el bloqueo se soltaría al terminar la consulta y no protegería nada: se rechaza en vez de fallar en silencio. */
    @Test void exigeUnaTransaccion() {
        assertThatThrownBy(() -> repo.ocupadosBloqueando(cuenta(), Entorno.PRODUCCION, OCTUBRE)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Ocho emisiones a la vez con cinco lugares libres, cada una de una empresa distinta de la misma cuenta: el bloqueo hace que cada una cuente lo que dejó la anterior,
     * así que entran exactamente cinco. Sin él, varias verían el mismo conteo y pasarían el tope.
     */
    @Test void dosEmisionesSimultaneasNoTomanElMismoLugar() throws Exception {
        UUID c = cuenta();
        List<UUID> empresas = new ArrayList<>();
        for (int i = 0; i < 8; i++) empresas.add(empresa(c));
        int tope = 5;

        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> emitidas = new ArrayList<>();
        try {
            for (UUID e : empresas) emitidas.add(ex.submit(() -> uow.ejecutar(() -> {
                if (repo.ocupadosBloqueando(c, Entorno.PRODUCCION, OCTUBRE) >= tope) return false;
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                documento(e, EstadoDocumento.FIRMADO, MITAD);
                return true;
            })));
            long ok = 0;
            for (Future<Boolean> f : emitidas) if (f.get()) ok++;
            assertThat(ok).isEqualTo(tope);
        } finally {
            ex.shutdownNow();
        }
        assertThat(ocupados(c, OCTUBRE)).isEqualTo(tope);
    }
}
