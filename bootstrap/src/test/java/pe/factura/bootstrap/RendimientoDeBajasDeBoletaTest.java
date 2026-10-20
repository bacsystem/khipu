package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import pe.factura.adapters.signing.XmlDsigSigner;
import pe.factura.adapters.ubl.FreemarkerUblGenerator;
import pe.factura.adapters.ubl.JaxpXsdValidator;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.CertificadoDigital;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #287: cuánto cuesta preparar la baja de una boleta en el resumen diario —generar el SummaryDocuments, firmarlo con XML-DSig y validarlo contra el XSD—, que es todo lo que hace
 * khipu por cada baja antes de hablar con SUNAT. Una baja lleva su propio resumen de una línea (lo que se agrupa hasta 500 líneas, #301, son las altas: ver abajo), así que **10 000 bajas son 10 000
 * resúmenes**: este es el tiempo que el sistema necesita para producirlos, sin red. La meta acordada es p95 ≤ 100 ms por resumen y ≥ 50 resúmenes por segundo en un hilo, o sea
 * 10 000 bajas en menos de 3,5 minutos; en una laptop mide unos 6 ms de p95 y ~240 resúmenes/s (ver `k6/README.md`). La prueba falla si se rompe la meta, no si el equipo es rápido.
 */
class RendimientoDeBajasDeBoletaTest {
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));
    static final int RESUMENES = 300;
    static final double P95_MAXIMO_MS = 100;
    static final double RESUMENES_POR_SEGUNDO_MINIMO = 50;

    static Tenant tenant() throws Exception {
        byte[] p12 = RendimientoDeBajasDeBoletaTest.class.getResourceAsStream("/test-cert.p12").readAllBytes();
        return new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null, new CertificadoDigital(p12, "test1234", LocalDate.of(2036, 1, 1)));
    }

    static Comprobante boleta(long numero) {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("P001", "Menú ejecutivo", "NIU", new BigDecimal("2"), new BigDecimal("18.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P002", "Chicha morada 1 L", "NIU", BigDecimal.ONE, new BigDecimal("9.50"), TipoAfectacionIgv.GRAVADO))).crear(RELOJ);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k");
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
        return c;
    }

    @Test void prepararElResumenDeUnaBajaCuestaMuchoMenosQueLaMeta() throws Exception {
        FreemarkerUblGenerator ubl = new FreemarkerUblGenerator();
        XmlDsigSigner firma = new XmlDsigSigner();
        JaxpXsdValidator xsd = new JaxpXsdValidator();
        Tenant tenant = tenant();
        List<Comprobante> boletas = new ArrayList<>();
        for (int i = 1; i <= RESUMENES; i++) boletas.add(boleta(i));
        // Calentamiento: la primera pasada carga plantillas, esquemas y el PKCS#12.
        for (int i = 0; i < 10; i++) preparar(ubl, firma, xsd, tenant, boletas.get(i), i + 1);

        List<Double> milisegundos = new ArrayList<>();
        long inicio = System.nanoTime();
        for (int i = 0; i < RESUMENES; i++) {
            long t0 = System.nanoTime();
            preparar(ubl, firma, xsd, tenant, boletas.get(i), i + 1);
            milisegundos.add((System.nanoTime() - t0) / 1_000_000.0);
        }
        double segundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis() / 1000.0;
        Collections.sort(milisegundos);
        double p50 = milisegundos.get(RESUMENES / 2), p95 = milisegundos.get((int) (RESUMENES * 0.95)), maximo = milisegundos.get(RESUMENES - 1);
        double porSegundo = RESUMENES / segundos;

        System.out.printf("Resúmenes de baja de boleta (generar + firmar + XSD), %d en un hilo: p50 %.1f ms · p95 %.1f ms · máx %.1f ms · %.1f resúmenes/s%n", RESUMENES, p50, p95, maximo, porSegundo);
        assertThat(p95).as("p95 por resumen (ms)").isLessThanOrEqualTo(P95_MAXIMO_MS);
        assertThat(porSegundo).as("resúmenes por segundo").isGreaterThanOrEqualTo(RESUMENES_POR_SEGUNDO_MINIMO);
    }

    /** El 6.º día después del 13: las boletas firmadas sin enviar ya solo van en el resumen diario. */
    static final Clock DIA_SEIS = Clock.fixed(Instant.parse("2026-09-19T15:00:00Z"), ZoneId.of("America/Lima"));
    static final int BOLETAS_EN_UN_DIA_DE_CAIDA = 10_000;
    static final Duration TOPE_DE_LAS_DIEZ_MIL = Duration.ofMinutes(5);

    /**
     * #301: la fila «RC de 10 000 boletas < 5 min» de {@code k6/README.md}. Con el resumen por lotes, 10 000 boletas de una misma fecha son <b>20</b> resúmenes de 500 líneas, y esto
     * mide lo que khipu necesita para producirlos —generar el SummaryDocuments, firmarlo con XML-DSig y validarlo contra el XSD—, sin red: SUNAT es lo único que se suma, y su
     * latencia no la controla khipu. Falla si pasa de 5 minutos.
     */
    @Test void diezMilBoletasSonVeinteResumenesDeQuinientasLineasYSePreparanEnMenosDeCincoMinutos() throws Exception {
        FreemarkerUblGenerator ubl = new FreemarkerUblGenerator();
        XmlDsigSigner firma = new XmlDsigSigner();
        JaxpXsdValidator xsd = new JaxpXsdValidator();
        Tenant tenant = tenant();
        List<Comprobante> boletas = new ArrayList<>();
        for (int i = 1; i <= BOLETAS_EN_UN_DIA_DE_CAIDA; i++) {
            Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                    List.of(new Item("P001", "Menú ejecutivo", "NIU", new BigDecimal("2"), new BigDecimal("18.00"), TipoAfectacionIgv.GRAVADO),
                            new Item("P002", "Chicha morada 1 L", "NIU", BigDecimal.ONE, new BigDecimal("9.50"), TipoAfectacionIgv.GRAVADO))).crear(RELOJ);
            c.asignarNumero(i, "20100066603");
            c.firmar("H", "k");
            boletas.add(c);
        }
        int lote = ComunicacionBaja.MAX_LINEAS_RESUMEN;
        // Calentamiento: la primera pasada carga plantillas, esquemas y el PKCS#12.
        prepararResumen(ubl, firma, xsd, tenant, boletas.subList(0, lote), 1);

        List<Double> milisegundos = new ArrayList<>();
        long inicio = System.nanoTime();
        int resumenes = 0;
        for (int desde = 0; desde < boletas.size(); desde += lote) {
            long t0 = System.nanoTime();
            prepararResumen(ubl, firma, xsd, tenant, boletas.subList(desde, desde + lote), ++resumenes);
            milisegundos.add((System.nanoTime() - t0) / 1_000_000.0);
        }
        Duration total = Duration.ofNanos(System.nanoTime() - inicio);
        Collections.sort(milisegundos);

        System.out.printf("%d boletas = %d resúmenes de %d líneas (generar + firmar + XSD), en un hilo: total %.1f s · por resumen p50 %.0f ms · máx %.0f ms%n",
                BOLETAS_EN_UN_DIA_DE_CAIDA, resumenes, lote, total.toMillis() / 1000.0, milisegundos.get(milisegundos.size() / 2), milisegundos.get(milisegundos.size() - 1));
        assertThat(resumenes).isEqualTo(20);
        assertThat(total).as("10 000 boletas en resúmenes de 500 líneas").isLessThanOrEqualTo(TOPE_DE_LAS_DIEZ_MIL);
    }

    private static void prepararResumen(FreemarkerUblGenerator ubl, XmlDsigSigner firma, JaxpXsdValidator xsd, Tenant tenant, List<Comprobante> lote, int correlativo) {
        List<ComunicacionBaja> filas = ComunicacionBaja.altasEnResumen(lote, correlativo, DIA_SEIS);
        String firmado = firma.firmar(ubl.generarResumen(filas, lote, tenant), tenant.certificado()).xmlFirmado();
        xsd.validarBaja(firmado, filas.get(0));
    }

    private static void preparar(FreemarkerUblGenerator ubl, XmlDsigSigner firma, JaxpXsdValidator xsd, Tenant tenant, Comprobante boleta, int correlativo) {
        ComunicacionBaja baja = ComunicacionBaja.crear(boleta, correlativo, "Error en el monto", RELOJ);
        String sinFirma = ubl.generarBaja(baja, boleta, tenant);
        String firmado = firma.firmar(sinFirma, tenant.certificado()).xmlFirmado();
        xsd.validarBaja(firmado, baja);
    }
}
