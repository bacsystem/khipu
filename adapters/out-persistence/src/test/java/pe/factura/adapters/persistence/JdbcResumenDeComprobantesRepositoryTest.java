package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Facturado;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Agregados;
import pe.factura.domain.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El resumen de los comprobantes de una empresa (#15), con Postgres real: cuántos hay por estado y cuánto suman los que cuentan como facturados, por moneda, con las
 * notas de crédito restando; solo de la empresa y solo del rango, con sus bordes inclusivos.
 */
class JdbcResumenDeComprobantesRepositoryTest extends PersistenciaTestBase {
    static final LocalDate DIA = LocalDate.of(2026, 9, 15);
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcResumenDeComprobantesRepository repo = new JdbcResumenDeComprobantesRepository(jdbc);

    /** Un comprobante con su fila de documento y la de sus totales. */
    void comprobante(UUID tenant, String tipo, EstadoDocumento estado, LocalDate fecha, String moneda, String total) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, ?, 'F001', ?, ?, ?, 'x')",
                id, tenant, tipo, NUMERO.getAndIncrement(), java.sql.Date.valueOf(fecha), estado.name());
        jdbc.update("INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total) "
                + "VALUES (?, '0101', ?, '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, ?)", id, moneda, new BigDecimal(total));
    }

    void factura(UUID t, EstadoDocumento e, String total) { comprobante(t, "01", e, DIA, "PEN", total); }

    Agregados resumir(UUID t) { return repo.resumir(t, null, null); }

    // --- por estado -------------------------------------------------------------------------------------------------------------------------

    @Test void cuentaLosComprobantesDeCadaEstadoYSoloLosQueHay() {
        UUID t = tenantDePrueba();
        factura(t, EstadoDocumento.ACEPTADO, "100");
        factura(t, EstadoDocumento.ACEPTADO, "100");
        factura(t, EstadoDocumento.RECHAZADO, "100");
        factura(t, EstadoDocumento.ERROR_ENVIO, "100");

        assertThat(resumir(t).porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 2L), java.util.Map.entry(EstadoDocumento.RECHAZADO, 1L), java.util.Map.entry(EstadoDocumento.ERROR_ENVIO, 1L));
    }

    @Test void unaEmpresaSinComprobantesTrae_todoVacio() {
        Agregados r = resumir(tenantDePrueba());

        assertThat(r.porEstado()).isEmpty();
        assertThat(r.facturado()).isEmpty();
    }

    @Test void soloCuentaLosDeLaEmpresaNoLosDeOtra() {
        UUID mia = tenantDePrueba();
        UUID otra = tenantDePrueba();
        factura(mia, EstadoDocumento.ACEPTADO, "100");
        factura(otra, EstadoDocumento.ACEPTADO, "999");
        factura(otra, EstadoDocumento.RECHAZADO, "999");

        Agregados r = resumir(mia);

        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 1L));
        assertThat(r.facturado()).containsExactly(new Facturado("PEN", new BigDecimal("100.00")));
    }

    // --- el rango ---------------------------------------------------------------------------------------------------------------------------

    @Test void elRangoEsInclusivoEnLosDosExtremosYNoTraeLosVecinos() {
        UUID t = tenantDePrueba();
        for (int dia : new int[]{9, 10, 15, 20, 21}) comprobante(t, "01", EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, dia), "PEN", String.valueOf(dia));

        Agregados r = repo.resumir(t, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));

        assertThat(r.porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(3);
        assertThat(r.facturado()).containsExactly(new Facturado("PEN", new BigDecimal("45.00")));
    }

    @Test void unLadoAbiertoNoAcotaEseExtremo() {
        UUID t = tenantDePrueba();
        for (int dia : new int[]{9, 15, 21}) comprobante(t, "01", EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, dia), "PEN", "1");

        assertThat(repo.resumir(t, LocalDate.of(2026, 9, 15), null).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(2);
        assertThat(repo.resumir(t, null, LocalDate.of(2026, 9, 15)).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(2);
        assertThat(repo.resumir(t, null, null).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(3);
    }

    @Test void unRangoSinComprobantesTraeTodoVacioAunqueLaEmpresaTengaOtros() {
        UUID t = tenantDePrueba();
        factura(t, EstadoDocumento.ACEPTADO, "100");

        Agregados r = repo.resumir(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(r.porEstado()).isEmpty();
        assertThat(r.facturado()).isEmpty();
    }

    // --- lo facturado -----------------------------------------------------------------------------------------------------------------------

    @Test void sumaLoAceptadoYLoQueEstaEnCaminoYNoLoRechazadoNiLoAnuladoNiLoQueSePasoDelPlazo() {
        UUID t = tenantDePrueba();
        factura(t, EstadoDocumento.ACEPTADO, "100");
        factura(t, EstadoDocumento.ACEPTADO_CON_OBS, "10");
        factura(t, EstadoDocumento.ENVIADO, "1");
        factura(t, EstadoDocumento.FIRMADO, "1000");
        factura(t, EstadoDocumento.ERROR_ENVIO, "10000");
        factura(t, EstadoDocumento.PENDIENTE_AGRUPACION, "100000");
        factura(t, EstadoDocumento.RECHAZADO, "7");
        factura(t, EstadoDocumento.ANULADO, "7");
        factura(t, EstadoDocumento.FUERA_DE_PLAZO, "7");

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("111111.00")));
    }

    /** Los estados que suman salen del enum: si se agrega uno, el test de `EstadoDocumentoTest` obliga a decidirlo y esto sigue la regla sin tocarse. */
    @Test void losEstadosQueSumanSonLosQueDiceElDominioUnoPorUno() {
        UUID t = tenantDePrueba();
        for (EstadoDocumento e : EstadoDocumento.values()) factura(t, e, "100");
        long esperados = Arrays.stream(EstadoDocumento.values()).filter(EstadoDocumento::cuentaComoFacturado).count();

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("100.00").multiply(BigDecimal.valueOf(esperados))));
        assertThat(resumir(t).porEstado()).hasSize(EstadoDocumento.values().length);
    }

    @Test void lasNotasDeCreditoRestanYLasDeDebitoSuman() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "1000");
        comprobante(t, "03", EstadoDocumento.ACEPTADO, DIA, "PEN", "200");
        comprobante(t, "07", EstadoDocumento.ACEPTADO, DIA, "PEN", "300");
        comprobante(t, "08", EstadoDocumento.ACEPTADO, DIA, "PEN", "50");

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("950.00")));
    }

    @Test void unaNotaDeCreditoRechazadaNoRestaNada() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "1000");
        comprobante(t, "07", EstadoDocumento.RECHAZADO, DIA, "PEN", "300");

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("1000.00")));
    }

    @Test void siLasNotasSuperanLoEmitidoElNetoSaleNegativo() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "100");
        comprobante(t, "07", EstadoDocumento.ACEPTADO, DIA, "PEN", "250");

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("-150.00")));
    }

    @Test void cadaMonedaSeSumaAparteYVaOrdenadaPorMoneda() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "USD", "500.50");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "100");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "23.45");
        comprobante(t, "07", EstadoDocumento.ACEPTADO, DIA, "USD", "0.50");

        assertThat(resumir(t).facturado()).containsExactly(new Facturado("PEN", new BigDecimal("123.45")), new Facturado("USD", new BigDecimal("500.00")));
    }

    @Test void unaMonedaSinNadaQueFacturarNoAparece() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.RECHAZADO, DIA, "USD", "500");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "100");

        assertThat(resumir(t).facturado()).extracting(Facturado::moneda).containsExactly("PEN");
    }

    @Test void conservaLosCentimos() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "0.10");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "0.20");

        assertThat(resumir(t).facturado().get(0).total()).isEqualByComparingTo("0.30");
    }

    // --- volumen ----------------------------------------------------------------------------------------------------------------------------

    /**
     * El issue pide responder en menos de 300 ms con 100 000 comprobantes. Se siembran de golpe con `generate_series` (miles de INSERT sueltos agotan los puertos) y se
     * mide la mejor de tres lecturas después de una de calentamiento, para que un tropiezo del recolector de basura o del contenedor no tire la prueba.
     */
    @Test void con100milComprobantesRespondeEnMenosDe300Milisegundos() {
        UUID t = tenantDePrueba();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                SELECT gen_random_uuid(), ?, CASE WHEN g %% 50 = 0 THEN '07' ELSE '01' END, 'F001', g,
                       DATE '2026-01-01' + (g %% 270)::int,
                       (ARRAY['ACEPTADO','ACEPTADO','ACEPTADO','ACEPTADO_CON_OBS','ENVIADO','ERROR_ENVIO','RECHAZADO','FUERA_DE_PLAZO','ANULADO','FIRMADO'])[1 + g %% 10], 'x'
                FROM generate_series(1, 100000) g""".formatted(), t);
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total)
                SELECT id, '0101', CASE WHEN numero %% 7 = 0 THEN 'USD' ELSE 'PEN' END, '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, 100 + (numero %% 900)
                FROM documento WHERE tenant_id = ?""".formatted(), t);
        jdbc.execute("ANALYZE documento");
        jdbc.execute("ANALYZE comprobante");

        repo.resumir(t, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30));
        long mejor = Long.MAX_VALUE;
        Agregados r = null;
        for (int i = 0; i < 3; i++) {
            long inicio = System.nanoTime();
            r = repo.resumir(t, null, null);
            mejor = Math.min(mejor, (System.nanoTime() - inicio) / 1_000_000);
        }

        System.out.println("RESUMEN_100K_MS=" + mejor);
        assertThat(r.porEstado().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(100_000);
        assertThat(r.facturado()).extracting(Facturado::moneda).containsExactly("PEN", "USD");
        assertThat(mejor).as("ms de la mejor lectura sobre 100 000 comprobantes").isLessThan(300);
    }
}
