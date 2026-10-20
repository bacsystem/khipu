package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Facturado;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Agregados;
import pe.factura.application.port.out.ResumenDeComprobantesRepository.Celda;
import pe.factura.domain.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El resumen de los comprobantes de una empresa (#15), con Postgres real: cuántos hay por estado y cuánto suman los que cuentan como facturados, por moneda, con las
 * notas de crédito restando; solo de la empresa y solo del rango, con sus bordes inclusivos.
 */
class JdbcResumenDeComprobantesRepositoryTest extends PersistenciaTestBase {
    static final LocalDate DIA = LocalDate.of(2026, 9, 15);
    /** Hoy, para decidir qué boleta sin enviar ya pide atención (#356). */
    static final LocalDate HOY = LocalDate.of(2026, 10, 15);
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcResumenDeComprobantesRepository repo = new JdbcResumenDeComprobantesRepository(jdbc);

    /** Un comprobante con su fila de documento y la de sus totales. */
    UUID comprobante(UUID tenant, String tipo, EstadoDocumento estado, LocalDate fecha, String moneda, String total) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, ?, 'F001', ?, ?, ?, 'x')",
                id, tenant, tipo, NUMERO.getAndIncrement(), java.sql.Date.valueOf(fecha), estado.name());
        jdbc.update("INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total) "
                + "VALUES (?, '0101', ?, '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, ?)", id, moneda, new BigDecimal(total));
        return id;
    }

    void factura(UUID t, EstadoDocumento e, String total) { comprobante(t, "01", e, DIA, "PEN", total); }

    Agregados resumir(UUID t) { return repo.resumir(t, null, null, HOY); }

    // --- por estado -------------------------------------------------------------------------------------------------------------------------

    @Test void cuentaLosComprobantesDeCadaEstadoYSoloLosQueHay() {
        UUID t = tenantDePrueba();
        factura(t, EstadoDocumento.ACEPTADO, "100");
        factura(t, EstadoDocumento.ACEPTADO, "100");
        factura(t, EstadoDocumento.RECHAZADO, "100");
        factura(t, EstadoDocumento.ERROR_ENVIO, "100");

        Agregados r = resumir(t);
        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 2L), java.util.Map.entry(EstadoDocumento.RECHAZADO, 1L), java.util.Map.entry(EstadoDocumento.ERROR_ENVIO, 1L));
        assertThat(r.ventasPorEstado()).as("todas son facturas: las mismas cuentas").isEqualTo(r.porEstado());
    }

    @Test void unaEmpresaSinComprobantesTrae_todoVacio() {
        Agregados r = resumir(tenantDePrueba());

        assertThat(r.porEstado()).isEmpty();
        assertThat(r.ventasPorEstado()).isEmpty();
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

        Agregados r = repo.resumir(t, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20), HOY);

        assertThat(r.porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(3);
        assertThat(r.facturado()).containsExactly(new Facturado("PEN", new BigDecimal("45.00")));
    }

    @Test void unLadoAbiertoNoAcotaEseExtremo() {
        UUID t = tenantDePrueba();
        for (int dia : new int[]{9, 15, 21}) comprobante(t, "01", EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, dia), "PEN", "1");

        assertThat(repo.resumir(t, LocalDate.of(2026, 9, 15), null, HOY).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(2);
        assertThat(repo.resumir(t, null, LocalDate.of(2026, 9, 15), HOY).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(2);
        assertThat(repo.resumir(t, null, null, HOY).porEstado().get(EstadoDocumento.ACEPTADO)).isEqualTo(3);
    }

    @Test void unRangoSinComprobantesTraeTodoVacioAunqueLaEmpresaTengaOtros() {
        UUID t = tenantDePrueba();
        factura(t, EstadoDocumento.ACEPTADO, "100");

        Agregados r = repo.resumir(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), HOY);

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

    /**
     * #327: la liquidación de compra es una compra de la empresa, no una venta (TipoDocumento#esVenta): cuenta en los estados, pero no entre las ventas ni suma a lo facturado
     * (ni en su moneda aparece).
     */
    @Test void laLiquidacionDeCompraCuentaEnLosEstadosPeroNoComoVentaNiFacturado() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "1000");
        comprobante(t, "04", EstadoDocumento.ACEPTADO, DIA, "PEN", "985");
        comprobante(t, "04", EstadoDocumento.ACEPTADO, DIA, "USD", "197");

        Agregados r = resumir(t);

        assertThat(r.facturado()).containsExactly(new Facturado("PEN", new BigDecimal("1000.00")));
        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 3L));
        assertThat(r.ventasPorEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 1L));
    }

    /**
     * #40: lo que se retiene o percibe es un impuesto, no una venta (TipoDocumento#esVenta): la retención y la percepción cuentan en los estados, pero no entre las ventas ni en
     * lo facturado.
     */
    @Test void laRetencionYLaPercepcionCuentanEnLosEstadosPeroNoComoVentaNiFacturado() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, DIA, "PEN", "1000");
        comprobante(t, "20", EstadoDocumento.ACEPTADO, DIA, "PEN", "30");
        comprobante(t, "40", EstadoDocumento.ACEPTADO, DIA, "PEN", "20");

        Agregados r = resumir(t);

        assertThat(r.facturado()).containsExactly(new Facturado("PEN", new BigDecimal("1000.00")));
        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 3L));
        assertThat(r.ventasPorEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 1L));
    }

    /**
     * Un 04 rechazado o un 20/40 con error de envío o fuera de plazo pide atención como cualquier otro (sus plazos legales son de 1 y 7 días): cuenta por estado, aunque no
     * esté entre las ventas, y así el aviso cuadra con el listado filtrado por ese estado, que trae todos los tipos.
     */
    @Test void loQueNoEsVentaYPideAtencionCuentaPorEstado() {
        UUID t = tenantDePrueba();
        comprobante(t, "04", EstadoDocumento.RECHAZADO, DIA, "PEN", "985");
        comprobante(t, "20", EstadoDocumento.ERROR_ENVIO, DIA, "PEN", "30");
        comprobante(t, "40", EstadoDocumento.FUERA_DE_PLAZO, DIA, "PEN", "20");
        comprobante(t, "01", EstadoDocumento.RECHAZADO, DIA, "PEN", "100");

        Agregados r = resumir(t);

        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.RECHAZADO, 2L), java.util.Map.entry(EstadoDocumento.ERROR_ENVIO, 1L),
                java.util.Map.entry(EstadoDocumento.FUERA_DE_PLAZO, 1L));
        assertThat(r.ventasPorEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.RECHAZADO, 1L));
        assertThat(r.facturado()).isEmpty();
    }

    /** Una empresa que solo emitió retenciones y percepciones no tiene ventas, pero sí estados: el resumen no la muestra como si no hubiera emitido nada. */
    @Test void unaEmpresaQueSoloEmitioRetencionesYPercepcionesTieneEstadosPeroNoVentas() {
        UUID t = tenantDePrueba();
        comprobante(t, "20", EstadoDocumento.ACEPTADO, DIA, "PEN", "30");
        comprobante(t, "40", EstadoDocumento.ENVIADO, DIA, "PEN", "20");

        Agregados r = resumir(t);

        assertThat(r.porEstado()).containsOnly(java.util.Map.entry(EstadoDocumento.ACEPTADO, 1L), java.util.Map.entry(EstadoDocumento.ENVIADO, 1L));
        assertThat(r.ventasPorEstado()).isEmpty();
        assertThat(r.facturado()).isEmpty();
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

        repo.resumir(t, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30), HOY);
        long mejor = Long.MAX_VALUE;
        Agregados r = null;
        for (int i = 0; i < 3; i++) {
            long inicio = System.nanoTime();
            r = repo.resumir(t, null, null, HOY);
            mejor = Math.min(mejor, (System.nanoTime() - inicio) / 1_000_000);
        }

        // #24: sobre las mismas 100 000 filas (sembrar otras 100 000 en una prueba aparte tiraba la medición de esta), la emisión por día del mes del gráfico y de dos
        // meses, lo más que admite.
        LocalDate setiembre = LocalDate.of(2026, 9, 1);
        repo.porDia(t, setiembre, LocalDate.of(2026, 9, 30));
        long mejorMes = Long.MAX_VALUE;
        long mejorDosMeses = Long.MAX_VALUE;
        List<Celda> mes = null;
        for (int i = 0; i < 3; i++) {
            long inicio = System.nanoTime();
            mes = repo.porDia(t, setiembre, LocalDate.of(2026, 9, 30));
            mejorMes = Math.min(mejorMes, (System.nanoTime() - inicio) / 1_000_000);
            inicio = System.nanoTime();
            repo.porDia(t, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
            mejorDosMeses = Math.min(mejorDosMeses, (System.nanoTime() - inicio) / 1_000_000);
        }

        System.out.println("RESUMEN_100K_MS=" + mejor + " POR_DIA_100K_MES_MS=" + mejorMes + " POR_DIA_100K_DOS_MESES_MS=" + mejorDosMeses);
        assertThat(r.porEstado().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(100_000);
        assertThat(r.facturado()).extracting(Facturado::moneda).containsExactly("PEN", "USD");
        assertThat(mejor).as("ms de la mejor lectura sobre 100 000 comprobantes").isLessThan(300);
        assertThat(mes.stream().map(Celda::fecha).distinct().count()).as("del 1 al 27 de setiembre: la siembra llega hasta el día 269 del año").isEqualTo(27);
        assertThat(mes.stream().mapToLong(Celda::cuantos).sum()).as("370 por día: los restos 1 a 100, que llevan uno más, caen en enero–abril").isEqualTo(27 * 370);
        assertThat(mejorMes).as("ms de la mejor lectura por día de un mes sobre 100 000 comprobantes").isLessThan(300);
        assertThat(mejorDosMeses).as("ms de la mejor lectura por día de dos meses sobre 100 000 comprobantes").isLessThan(300);
    }

    // --- la boleta FIRMADA sin enviar (#356) ------------------------------------------------------------------------------------------------

    UUID boleta(UUID t, EstadoDocumento e, int diasAtras) { return comprobante(t, "03", e, HOY.minusDays(diasAtras), "PEN", "10"); }

    /** Una nota que modifica una boleta: tipo 07 o 08 y su motivo. */
    UUID notaDeBoleta(UUID t, String tipo, String motivo, int diasAtras) {
        UUID id = comprobante(t, tipo, EstadoDocumento.FIRMADO, HOY.minusDays(diasAtras), "PEN", "10");
        jdbc.update("UPDATE comprobante SET nota_tipo_afectado = '03', nota_serie_afectada = 'B001', nota_numero_afectado = 1, nota_motivo = ? WHERE documento_id = ?", motivo, id);
        return id;
    }

    /** Desde el 5.º día una boleta que nadie envió es «atención requerida»; antes no. Solo cuenta la FIRMADA: un envío que falla ya es un error de envío. */
    @Test void cuentaLasBoletasFirmadasSinEnviarDesdeElQuintoDia() {
        UUID t = tenantDePrueba();
        boleta(t, EstadoDocumento.FIRMADO, 4);
        boleta(t, EstadoDocumento.FIRMADO, 5);
        boleta(t, EstadoDocumento.FIRMADO, 7);
        boleta(t, EstadoDocumento.ERROR_ENVIO, 6);
        boleta(t, EstadoDocumento.ENVIADO, 6);
        boleta(t, EstadoDocumento.ACEPTADO, 6);

        Agregados r = resumir(t);

        assertThat(r.boletasSinEnviar()).isEqualTo(2);
        assertThat(r.porEstado().get(EstadoDocumento.FIRMADO)).as("las FIRMADAS siguen contándose por estado: son las 3").isEqualTo(3);
    }

    @Test void unaFacturaFirmadaSinEnviarNoSeCuentaComoBoleta() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.FIRMADO, HOY.minusDays(30), "PEN", "10");

        assertThat(resumir(t).boletasSinEnviar()).isZero();
    }

    /** La nota que modifica una boleta sigue a la boleta (va en el resumen diario); la NC 10 y la ND 03 no, y vencidas pasan a FUERA_DE_PLAZO. */
    @Test void cuentaLaNotaDeBoletaQueVaEnElResumenYNoLaNC10NiLaND03() {
        UUID t = tenantDePrueba();
        notaDeBoleta(t, "07", "07", 6);
        notaDeBoleta(t, "08", "01", 6);
        notaDeBoleta(t, "07", "10", 6);
        notaDeBoleta(t, "08", "03", 6);

        assertThat(resumir(t).boletasSinEnviar()).isEqualTo(2);
    }

    /** El rango y la empresa acotan igual que a lo demás, y la fecha de hoy es la que se pasa. */
    @Test void laCuentaRespetaLaEmpresaElRangoYLaFechaDeHoy() {
        UUID mia = tenantDePrueba();
        UUID otra = tenantDePrueba();
        boleta(mia, EstadoDocumento.FIRMADO, 5);   // emitida el 2026-10-10
        boleta(otra, EstadoDocumento.FIRMADO, 5);

        assertThat(repo.resumir(mia, null, null, HOY).boletasSinEnviar()).isEqualTo(1);
        assertThat(repo.resumir(mia, LocalDate.of(2026, 10, 11), null, HOY).boletasSinEnviar()).as("fuera del rango").isZero();
        assertThat(repo.resumir(mia, null, null, HOY.minusDays(1)).boletasSinEnviar()).as("ayer todavía no cumplía los 5 días").isZero();
    }

    // --- la emisión por día (#24) -----------------------------------------------------------------------------------------------------------

    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    LocalDate oct(int dia) { return LocalDate.of(2026, 10, dia); }

    List<Celda> porDia(UUID t) { return repo.porDia(t, OCT_1, OCT_31); }

    @Test void agrupaPorDiaTipoEstadoYMonedaConLasNotasDeCreditoRestando() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, oct(3), "PEN", "1000");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, oct(3), "PEN", "1");
        comprobante(t, "03", EstadoDocumento.ACEPTADO, oct(3), "PEN", "200");
        comprobante(t, "07", EstadoDocumento.ACEPTADO, oct(3), "PEN", "300");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, oct(3), "USD", "50.50");
        comprobante(t, "01", EstadoDocumento.RECHAZADO, oct(3), "PEN", "7");
        comprobante(t, "01", EstadoDocumento.ENVIADO, oct(9), "PEN", "10");

        assertThat(porDia(t)).containsExactly(
                new Celda(oct(3), "01", EstadoDocumento.ACEPTADO, "PEN", 2, new BigDecimal("1001.00")),
                new Celda(oct(3), "01", EstadoDocumento.ACEPTADO, "USD", 1, new BigDecimal("50.50")),
                new Celda(oct(3), "01", EstadoDocumento.RECHAZADO, "PEN", 1, new BigDecimal("7.00")),
                new Celda(oct(3), "03", EstadoDocumento.ACEPTADO, "PEN", 1, new BigDecimal("200.00")),
                new Celda(oct(3), "07", EstadoDocumento.ACEPTADO, "PEN", 1, new BigDecimal("-300.00")),
                new Celda(oct(9), "01", EstadoDocumento.ENVIADO, "PEN", 1, new BigDecimal("10.00")));
    }

    @Test void elRangoEsInclusivoYSoloDeLaEmpresa() {
        UUID mia = tenantDePrueba();
        UUID otra = tenantDePrueba();
        comprobante(mia, "01", EstadoDocumento.ACEPTADO, LocalDate.of(2026, 9, 30), "PEN", "1");
        comprobante(mia, "01", EstadoDocumento.ACEPTADO, OCT_1, "PEN", "2");
        comprobante(mia, "01", EstadoDocumento.ACEPTADO, OCT_31, "PEN", "3");
        comprobante(mia, "01", EstadoDocumento.ACEPTADO, LocalDate.of(2026, 11, 1), "PEN", "4");
        comprobante(otra, "01", EstadoDocumento.ACEPTADO, oct(15), "PEN", "999");

        assertThat(porDia(mia)).extracting(Celda::fecha).containsExactly(OCT_1, OCT_31);
        assertThat(porDia(tenantDePrueba())).isEmpty();
    }

    /** Un documento sin fila de totales (nunca se armó) igual cuenta su estado, con moneda y neto nulos: el dominio decide que no es emitido ni facturado. */
    @Test void unDocumentoSinTotalesVieneConMonedaYNetoNulos() {
        UUID t = tenantDePrueba();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', ?, ?, 'RECIBIDO', 'x')",
                UUID.randomUUID(), t, NUMERO.getAndIncrement(), java.sql.Date.valueOf(oct(4)));

        assertThat(porDia(t)).containsExactly(new Celda(oct(4), "01", EstadoDocumento.RECIBIDO, null, 1, null));
    }

    /**
     * La fecha de emisión es un DATE (el día de Lima en que se emitió): el día de cada celda no se corre con el huso de la JVM, ni en Lima (UTC−5) ni del otro lado de
     * la línea de cambio de fecha.
     */
    @Test void elDiaNoSeCorreConElHusoHorarioDeLaJvm() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, OCT_1, "PEN", "1");
        comprobante(t, "01", EstadoDocumento.ACEPTADO, OCT_31, "PEN", "1");
        java.util.TimeZone antes = java.util.TimeZone.getDefault();
        try {
            for (String huso : new String[]{"America/Lima", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC"}) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(huso));
                assertThat(porDia(t)).as(huso).extracting(Celda::fecha).containsExactly(OCT_1, OCT_31);
            }
        } finally {
            java.util.TimeZone.setDefault(antes);
        }
    }
}
