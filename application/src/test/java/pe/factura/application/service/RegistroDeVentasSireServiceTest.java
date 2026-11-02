package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase.Conciliacion;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase.Exportacion;
import pe.factura.application.port.out.RegistroDeVentasRepository;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatSireGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.sire.FilaRvie;
import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El registro de ventas del SIRE (#322) en el servicio: qué valida antes de escribir, cómo recorre el periodo por tramos, el ZIP y la conciliación. */
class RegistroDeVentasSireServiceTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor CLIENTE = new Receptor("6", "20601234565", "COMERCIAL ANDINA S.A.C.", null);

    /** Un repositorio en memoria con el mismo contrato: solo los estados que entran, en orden, por posición, y cuántas veces se le pidió un tramo. */
    static class RegistroFalso implements RegistroDeVentasRepository {
        final List<Comprobante> comprobantes = new ArrayList<>();
        final Map<Referencia, LocalDate> fechas = new HashMap<>();
        int tramos, consultasDeFechas;
        Integer fallarEnElTramo;
        long pendientes;

        static final Comparator<Comprobante> ORDEN = Comparator.comparing(Comprobante::fechaEmision).thenComparing(c -> c.tipo().codigo()).thenComparing(Comprobante::serie)
                .thenComparing(Comprobante::numero);

        public Conteo contar(UUID tenantId, LocalDate desde, LocalDate hasta, int ejemplos) {
            List<Comprobante> sinTc = enRango(tenantId, desde, hasta).stream().filter(c -> !"PEN".equals(c.moneda()) && c.tipoCambio() == null).toList();
            return new Conteo(enRango(tenantId, desde, hasta).size(), pendientes, sinTc.size(), sinTc.stream().limit(ejemplos).map(c -> c.serie() + "-" + c.numero()).toList());
        }

        public List<Comprobante> siguientes(UUID tenantId, LocalDate desde, LocalDate hasta, Posicion despuesDe, int limite) {
            if (fallarEnElTramo != null && tramos == fallarEnElTramo) throw new IllegalStateException("la base se cayó");
            tramos++;
            return enRango(tenantId, desde, hasta).stream().filter(c -> despuesDe == null || ORDEN.compare(c, marcador(despuesDe)) > 0).limit(limite).toList();
        }

        public Map<Referencia, LocalDate> fechasDeEmision(UUID tenantId, Collection<Referencia> referencias) {
            consultasDeFechas++;
            Map<Referencia, LocalDate> r = new HashMap<>();
            for (Referencia ref : referencias) if (fechas.containsKey(ref)) r.put(ref, fechas.get(ref));
            return r;
        }

        private List<Comprobante> enRango(UUID tenantId, LocalDate desde, LocalDate hasta) {
            return comprobantes.stream().filter(c -> c.tenantId().equals(tenantId) && !c.fechaEmision().isBefore(desde) && !c.fechaEmision().isAfter(hasta)
                    && FilaRvie.INCLUIDOS.contains(c.estado())).sorted(ORDEN).toList();
        }

        private static Comprobante marcador(Posicion p) {
            return Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.porCodigo(p.tipo()), p.serie(), p.numero(), p.fechaEmision(), EstadoDocumento.ACEPTADO, CLIENTE,
                    List.of(item("1"))).rehidratar();
        }
    }

    static Item item(String precio) { return new Item("P", "Producto", "NIU", BigDecimal.ONE, new BigDecimal(precio), TipoAfectacionIgv.GRAVADO); }

    final Fakes.Tenants tenants = new Fakes.Tenants();
    final RegistroFalso registro = new RegistroFalso();
    final RegistroDeVentasSireService service = new RegistroDeVentasSireService(registro, tenants, new SireFalso(), CLOCK);
    final UUID tenant = UUID.randomUUID();

    { tenants.guardar(Fakes.tenantListo(tenant)); }

    Comprobante.Persistido persistido(TipoDocumento tipo, String serie, long numero, LocalDate fecha, EstadoDocumento estado) {
        return Comprobante.persistido(UUID.randomUUID(), tenant, tipo, serie, numero, fecha, estado, CLIENTE, List.of(item("118")));
    }

    Comprobante agregar(Comprobante c) { registro.comprobantes.add(c); return c; }

    Comprobante factura(long numero, LocalDate fecha) { return agregar(persistido(TipoDocumento.FACTURA, "F001", numero, fecha, EstadoDocumento.ACEPTADO).rehidratar()); }

    static List<String> lineas(byte[] zip, String nombre) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e = z.getNextEntry();
            assertThat(e.getName()).isEqualTo(nombre + ".txt");
            String texto = new String(z.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(z.getNextEntry()).isNull();
            return texto.isEmpty() ? List.of() : List.of(texto.split("\r\n", -1)).subList(0, texto.split("\r\n", -1).length - 1);
        }
    }

    byte[] exportar(String periodo) throws IOException {
        Exportacion e = service.preparar(tenant, periodo, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.escribir(e, out);
        return out.toByteArray();
    }

    // --- antes de escribir ------------------------------------------------------------------------------------------------------------------

    @Test void preparaElNombreYLaCuentaSinLeerNingunTramo() {
        factura(1, LocalDate.of(2026, 10, 1));
        registro.pendientes = 3;
        Exportacion e = service.preparar(tenant, "202610", false);
        assertThat(e.nombreArchivo()).isEqualTo("LE2010006660320261000140400021112");
        assertThat(e.registros()).isEqualTo(1);
        assertThat(e.pendientesDeSunat()).isEqualTo(3);
        assertThat(registro.tramos).isZero();
    }

    @Test void unPeriodoSinComprobantesEsNoEncontradoYDiceSiHayPendientes() {
        registro.pendientes = 2;
        assertThatThrownBy(() -> service.preparar(tenant, "202610", false)).isInstanceOf(DomainException.class).hasMessageContaining("2 siguen en camino")
                .extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void unPeriodoFuturoOMalEscritoSeRechazaSinConsultarNada() {
        assertThatThrownBy(() -> service.preparar(tenant, "202611", false)).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThatThrownBy(() -> service.preparar(tenant, "2026-10", false)).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThatThrownBy(() -> service.conciliar(tenant, "x", InputStream.nullInputStream())).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThat(registro.tramos).isZero();
    }

    @Test void masDelTopeEsRvieExcedeElTope() {
        RegistroFalso grande = new RegistroFalso() {
            @Override public Conteo contar(UUID t, LocalDate d, LocalDate h, int e) { return new Conteo(RegistroDeVentasSireService.MAXIMO_REGISTROS + 1L, 0, 0, List.of()); }
        };
        RegistroDeVentasSireService s = new RegistroDeVentasSireService(grande, tenants, new SireFalso(), CLOCK);
        assertThatThrownBy(() -> s.preparar(tenant, "202610", false)).hasMessageContaining("100000").extracting("codigo").isEqualTo("RVIE_EXCEDE_EL_TOPE");
    }

    @Test void sinTipoDeCambioFueraDeSolesSeRechazaSalvoQueSePermita() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        agregar(persistido(TipoDocumento.FACTURA, "F001", 2, LocalDate.of(2026, 10, 2), EstadoDocumento.ACEPTADO).moneda("USD").rehidratar());

        assertThatThrownBy(() -> service.preparar(tenant, "202610", false)).isInstanceOf(DomainException.class).hasMessageContainingAll("F001-2", "campo 28")
                .extracting("codigo").isEqualTo("RVIE_SIN_TIPO_CAMBIO");

        Exportacion e = service.preparar(tenant, "202610", true);
        assertThat(e.sinTipoCambio()).isEqualTo(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.escribir(e, out);
        assertThat(lineas(out.toByteArray(), e.nombreArchivo()).get(1).split("\\|", -1)[27]).isEmpty();
    }

    @Test void otraEmpresaNoExisteParaElServicio() {
        assertThatThrownBy(() -> service.preparar(UUID.randomUUID(), "202610", false)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    // --- el archivo -------------------------------------------------------------------------------------------------------------------------

    @Test void escribeUnZipConElTxtYUnaFilaPorComprobanteEnOrden() throws IOException {
        factura(3, LocalDate.of(2026, 10, 2));
        factura(1, LocalDate.of(2026, 10, 9));
        factura(2, LocalDate.of(2026, 10, 2));
        factura(9, LocalDate.of(2026, 9, 30));

        List<String> filas = lineas(exportar("202610"), "LE2010006660320261000140400021112");

        assertThat(filas).hasSize(3);
        assertThat(filas).extracting(l -> l.split("\\|")[8]).containsExactly("2", "3", "1");
        assertThat(filas).allSatisfy(l -> assertThat(l).startsWith("20100066603|EMPRESA SAC|202610|").endsWith("|"));
    }

    @Test void recorrePorTramosHastaElUltimoSinRepetirNiSaltar() throws IOException {
        int n = RegistroDeVentasSireService.TRAMO * 2 + 3;
        for (int k = 1; k <= n; k++) factura(k, LocalDate.of(2026, 10, 1 + k % 28));

        List<String> filas = lineas(exportar("202610"), "LE2010006660320261000140400021112");

        assertThat(filas).hasSize(n);
        assertThat(filas.stream().map(l -> l.split("\\|")[8]).distinct().count()).isEqualTo(n);
        assertThat(registro.tramos).isEqualTo(3);
    }

    @Test void unTramoJustoDelTamanoPideUnoMasYTermina() throws IOException {
        for (int k = 1; k <= RegistroDeVentasSireService.TRAMO; k++) factura(k, LocalDate.of(2026, 10, 5));
        assertThat(lineas(exportar("202610"), "LE2010006660320261000140400021112")).hasSize(RegistroDeVentasSireService.TRAMO);
        assertThat(registro.tramos).isEqualTo(2);
    }

    @Test void laNotaLlevaLaFechaDelComprobanteQueModificaConUnaConsultaPorTramo() throws IOException {
        factura(125, LocalDate.of(2026, 9, 30));
        agregar(persistido(TipoDocumento.NOTA_CREDITO, "F001", 1, LocalDate.of(2026, 10, 3), EstadoDocumento.ACEPTADO)
                .nota(new Nota(TipoDocumento.FACTURA, "F001", 125, "07", "Devolución")).rehidratar());
        agregar(persistido(TipoDocumento.NOTA_CREDITO, "F001", 2, LocalDate.of(2026, 10, 3), EstadoDocumento.ACEPTADO)
                .nota(new Nota(TipoDocumento.FACTURA, "F001", 777, "07", "Devolución")).rehidratar());
        registro.fechas.put(new RegistroDeVentasRepository.Referencia("01", "F001", 125), LocalDate.of(2026, 9, 30));

        List<String> filas = lineas(exportar("202610"), "LE2010006660320261000140400021112");

        assertThat(filas.get(0).split("\\|", -1)[28]).isEqualTo("30/09/2026");
        assertThat(filas.get(1).split("\\|", -1)[28]).as("khipu no tiene la F001-777: el campo queda vacío").isEmpty();
        assertThat(filas.get(0).split("\\|", -1)[25]).isEqualTo("-118.00");
        assertThat(registro.consultasDeFechas).isEqualTo(1);
    }

    /** Sin notas no hace falta buscar fechas: no se consulta por gusto. */
    @Test void sinNotasNoBuscaFechas() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        exportar("202610");
        assertThat(registro.consultasDeFechas).isZero();
    }

    /** Un fallo a mitad de camino no deja un ZIP que se abra como si estuviera completo: sin directorio central, ZipFile lo rechaza. */
    @Test void unFalloAMitadDeCaminoDejaUnZipInvalidoNoUnoCorto() throws IOException {
        for (int k = 1; k <= RegistroDeVentasSireService.TRAMO + 5; k++) factura(k, LocalDate.of(2026, 10, 5));
        Exportacion e = service.preparar(tenant, "202610", false);
        registro.fallarEnElTramo = 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> service.escribir(e, out)).hasMessageContaining("la base se cayó");

        Path archivo = Files.createTempFile("rvie", ".zip");
        try {
            Files.write(archivo, out.toByteArray());
            assertThatThrownBy(() -> new ZipFile(archivo.toFile()).close()).isInstanceOf(ZipException.class);
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    @Test void unZipCompletoSeAbreConZipFile() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        Path archivo = Files.createTempFile("rvie", ".zip");
        try {
            Files.write(archivo, exportar("202610"));
            try (ZipFile z = new ZipFile(archivo.toFile())) { assertThat(z.size()).isEqualTo(1); }
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    // --- la conciliación --------------------------------------------------------------------------------------------------------------------

    byte[] propuesta() throws IOException { return exportar("202610"); }

    static String texto(byte[] zip) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) { z.getNextEntry(); return new String(z.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test void elArchivoPropioConciliaSinDiferenciasEnZipYEnTxt() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        factura(2, LocalDate.of(2026, 10, 2));
        byte[] zip = propuesta();

        Conciliacion enZip = service.conciliar(tenant, "202610", new ByteArrayInputStream(zip));
        Conciliacion enTxt = service.conciliar(tenant, "202610", new ByteArrayInputStream(texto(zip).getBytes(StandardCharsets.UTF_8)));

        for (Conciliacion c : List.of(enZip, enTxt)) {
            assertThat(c.resultado().cuadra()).isTrue();
            assertThat(c.resultado().coinciden()).isEqualTo(2);
        }
    }

    @Test void laConciliacionSeparaFaltantesSobrantesYDiferencias() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        factura(2, LocalDate.of(2026, 10, 2));
        String sunat = "RUC|Razón social|Periodo|...\r\n" + texto(propuesta())
                .replace("|F001|2||", "|F001|5||")
                .replace("|100.00|0.00|18.00|", "|100.00|0.00|18.01|");
        factura(3, LocalDate.of(2026, 10, 3));

        Conciliacion c = service.conciliar(tenant, "202610", new ByteArrayInputStream(sunat.getBytes(StandardCharsets.UTF_8)));

        assertThat(c.resultado().soloEnSunat()).containsExactly("01-F001-5");
        assertThat(c.resultado().soloEnKhipu()).containsExactly("01-F001-2", "01-F001-3");
        assertThat(c.resultado().difieren()).extracting(d -> d.comprobante() + " " + d.campo()).containsExactly("01-F001-1 igv");
    }

    @Test void unZipSinTxtOUnArchivoQueNoSeLeeEsRvieArchivoInvalido() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) { z.putNextEntry(new ZipEntry("leeme.pdf")); z.write(1); z.closeEntry(); }
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream(zip.toByteArray()))).hasMessageContaining(".txt").extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream("hola\r\nmundo".getBytes())))
                .hasMessageContaining("línea 2").extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void unArchivoMasGrandeQueElTopeSeCortaAlLeerlo() {
        InputStream infinito = new InputStream() {
            @Override public int read() { return 10; }   // líneas en blanco: lo único que corta es el tope de bytes
        };
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", infinito)).extracting("codigo").isEqualTo("RVIE_PROPUESTA_DEMASIADO_GRANDE");
    }

    @Test void unZipQueSeInflaSinMedidaSeCortaEnElTopeDescomprimido() throws IOException {
        ByteArrayOutputStream bomba = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bomba)) {
            z.putNextEntry(new ZipEntry("propuesta.txt"));
            // Líneas en blanco: se saltan sin acumularse, así que lo único que corta la lectura es el tope.
            byte[] relleno = new byte[1024 * 1024];
            java.util.Arrays.fill(relleno, (byte) 10);
            for (int k = 0; k < RegistroDeVentasSireService.MAXIMO_DESCOMPRIMIDO / relleno.length + 2; k++) z.write(relleno);
            z.closeEntry();
        }
        assertThat((long) bomba.size()).isLessThan(RegistroDeVentasSireService.MAXIMO_ARCHIVO);
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream(bomba.toByteArray()))).extracting("codigo").isEqualTo("RVIE_PROPUESTA_DEMASIADO_GRANDE");
    }

    /** H5: una línea sin fin no se acumula hasta el tope de bytes: corta en 16 KB con la línea. */
    @Test void unaLineaSinSaltoDeLineaSeCortaEnSuTope() {
        factura(1, LocalDate.of(2026, 10, 1));
        byte[] unMega = new byte[1024 * 1024];
        java.util.Arrays.fill(unMega, (byte) 'x');
        byte[] archivo = ("RUC|titulos\n").getBytes(StandardCharsets.UTF_8);
        byte[] todo = new byte[archivo.length + unMega.length];
        System.arraycopy(archivo, 0, todo, 0, archivo.length);
        System.arraycopy(unMega, 0, todo, archivo.length, unMega.length);
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream(todo))).hasMessageContainingAll("línea 2", "16384")
                .extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
        assertThat(RegistroDeVentasSireService.MAXIMO_LINEA).isLessThan((int) RegistroDeVentasSireService.MAXIMO_ARCHIVO / 100);
    }

    /** H11: sin filas de datos la conciliación diría que SUNAT no tiene nada: es un archivo equivocado. */
    @Test void unaPropuestaSinFilasDeDatosEsArchivoInvalido() {
        factura(1, LocalDate.of(2026, 10, 1));
        for (String cuerpo : List.of("", "RUC|Razón social|Periodo\r\n", "\r\n\r\n"))
            assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream(cuerpo.getBytes(StandardCharsets.UTF_8))))
                    .as("«%s»", cuerpo).extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    /** H11: un ZIP con los datos comprimidos dañados falla al leerlos, y eso también es RVIE_ARCHIVO_INVALIDO, no un error interno. */
    @Test void unZipConDatosDanadosEsArchivoInvalido() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        byte[] zip = propuesta();
        assertThat(zip.length).isGreaterThan(260);
        for (int k = 80; k < zip.length - 130; k++) zip[k] = (byte) 0xFF;
        assertThatThrownBy(() -> service.conciliar(tenant, "202610", new ByteArrayInputStream(zip))).extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    /** H10: las filas consolidadas de la propuesta (campo 10) no se concilian y se cuentan. */
    @Test void lasFilasConsolidadasSeCuentanAparte() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        String propia = texto(propuesta());
        String consolidada = propia.replace("|01|F001|1||", "|03|B001|1|40|");
        Conciliacion c = service.conciliar(tenant, "202610", new ByteArrayInputStream((propia + consolidada).getBytes(StandardCharsets.UTF_8)));
        assertThat(c.filasConsolidadas()).isEqualTo(1);
        assertThat(c.resultado().coinciden()).isEqualTo(1);
    }

    @Test void laConciliacionInformaLosPendientes() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        registro.pendientes = 4;
        assertThat(service.conciliar(tenant, "202610", new ByteArrayInputStream(propuesta())).pendientesDeSunat()).isEqualTo(4);
    }

    // --- la propuesta que SUNAT prepara por la API del SIRE (fase 2) ------------------------------------------------------------------------

    /** El SIRE de SUNAT en memoria: el estado que se le diga, los archivos que se le den, y cuántas veces se le pidió cada cosa. */
    static class SireFalso implements SunatSireGateway {
        EstadoDelTicket estado;
        final Map<String, byte[]> archivos = new HashMap<>();
        RuntimeException fallo;
        String ticketDado = "202600000123";
        int solicitudes, consultas, descargas;

        public boolean disponible() { return true; }
        public String solicitarPropuesta(Tenant t, PeriodoSire periodo) { solicitudes++; falla(); return ticketDado; }
        public EstadoDelTicket consultar(Tenant t, PeriodoSire periodo, String ticket) { consultas++; falla(); return estado; }
        public byte[] descargar(Tenant t, ArchivoDelTicket a, long maximoBytes) { descargas++; falla(); return archivos.get(a.nombre()); }
        private void falla() { if (fallo != null) throw fallo; }

        SireFalso terminado(String... nombres) {
            estado = new EstadoDelTicket("202600000123", Fase.TERMINADO, "06", "Terminado", java.util.Arrays.stream(nombres).map(n -> new ArchivoDelTicket("202600000123", n, "01", "13", "202610")).toList());
            return this;
        }
    }

    final SireFalso sire = new SireFalso();
    final RegistroDeVentasSireService conSire = new RegistroDeVentasSireService(registro, tenants, sire, CLOCK);

    /** Las filas del propio registro de khipu como las bajaría SUNAT: un ZIP con el .txt. */
    byte[] propuestaDeSunat(String... lineas) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("20100066603-CPF-202610.txt"));
            z.write(String.join("\r\n", lineas).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @Test void pedirLaPropuestaDevuelveElTicketYValidaElPeriodoAntesDeLlamarASunat() {
        assertThat(conSire.solicitarPropuesta(tenant, "202610")).satisfies(p -> {
            assertThat(p.ticket()).isEqualTo("202600000123");
            assertThat(p.periodo().aaaamm()).isEqualTo("202610");
        });
        assertThatThrownBy(() -> conSire.solicitarPropuesta(tenant, "202611")).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThatThrownBy(() -> conSire.solicitarPropuesta(UUID.randomUUID(), "202610")).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(sire.solicitudes).as("solo el pedido válido llegó a SUNAT").isEqualTo(1);
    }

    @Test void elEstadoDelTicketDiceLaFaseYCuantosArchivosHay() {
        sire.terminado("a.zip", "b.zip");
        assertThat(conSire.estadoDePropuesta(tenant, "202610", "202600000123")).satisfies(e -> {
            assertThat(e.fase()).isEqualTo(SunatSireGateway.Fase.TERMINADO);
            assertThat(e.archivos()).isEqualTo(2);
            assertThat(e.codigoSunat()).isEqualTo("06");
        });
    }

    @Test void conciliarConLaPropuestaDeSunatBajaElArchivoYConcilia() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        factura(2, LocalDate.of(2026, 10, 2));
        sire.terminado("a.zip");
        sire.archivos.put("a.zip", propuesta());

        Conciliacion c = conSire.conciliarConSunat(tenant, "202610", "202600000123");

        assertThat(c.resultado().cuadra()).isTrue();
        assertThat(c.resultado().coinciden()).isEqualTo(2);
        assertThat(sire.descargas).isEqualTo(1);
    }

    @Test void unPeriodoPartidoEnVariosArchivosSeConciliaComoUnoSolo() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        factura(2, LocalDate.of(2026, 10, 2));
        String[] filas = texto(propuesta()).split("\r\n");
        sire.terminado("parte1.zip", "parte2.zip");
        sire.archivos.put("parte1.zip", propuestaDeSunat(filas[0]));
        sire.archivos.put("parte2.zip", propuestaDeSunat(filas[1]));

        Conciliacion c = conSire.conciliarConSunat(tenant, "202610", "202600000123");

        assertThat(c.resultado().cuadra()).isTrue();
        assertThat(c.resultado().registrosSunat()).isEqualTo(2);
    }

    @Test void unArchivoIlegibleDiceCualDeLosVariosEs() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        String[] filas = texto(propuesta()).split("\r\n");
        sire.terminado("parte1.zip", "parte2.zip");
        sire.archivos.put("parte1.zip", propuestaDeSunat(filas[0]));
        sire.archivos.put("parte2.zip", propuestaDeSunat("20100066603|x|202610"));

        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).hasMessageContainingAll("Archivo 2 de 2", "parte2.zip", "línea 1")
                .extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void conUnSoloArchivoElErrorNoHablaDeArchivos() {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.terminado("a.zip");
        sire.archivos.put("a.zip", propuestaDeSunat("20100066603|x|202610"));
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).hasMessageNotContaining("Archivo 1 de 1");
    }

    @Test void siElTicketNoTerminoNoSeBajaNada() {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.estado = new SunatSireGateway.EstadoDelTicket("202600000123", SunatSireGateway.Fase.EN_PROCESO, "05", "En proceso", List.of());
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).hasMessageContaining("En proceso").extracting("codigo").isEqualTo("PROPUESTA_NO_LISTA");
        sire.estado = new SunatSireGateway.EstadoDelTicket("202600000123", SunatSireGateway.Fase.CON_ERRORES, "03", "Procesado con errores", List.of());
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("PROPUESTA_CON_ERRORES");
        assertThat(sire.descargas).isZero();
    }

    @Test void unTicketTerminadoSinArchivosNoEsUnaConciliacionVacia() {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.terminado();
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void unArchivoSinFilasDeComprobantesNoEsUnaConciliacionVacia() {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.terminado("a.zip");
        sire.archivos.put("a.zip", propuestaDeSunat("RUC|Razón social|Periodo"));
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void losArchivosDeUnTicketTienenUnTopeEnSuma() throws IOException {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.terminado("a.zip", "b.zip");
        // Cada archivo es una propuesta válida con 11 MB de relleno que no se comprime: ninguno pasa solo del tope, los dos juntos sí.
        byte[] relleno = new byte[11 * 1024 * 1024];
        new java.util.Random(7).nextBytes(relleno);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            z.putNextEntry(new ZipEntry("p.txt"));
            z.write(texto(propuesta()).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("relleno.bin"));
            z.write(relleno);
            z.closeEntry();
        }
        sire.archivos.put("a.zip", zip.toByteArray());
        sire.archivos.put("b.zip", zip.toByteArray());
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("RVIE_PROPUESTA_DEMASIADO_GRANDE");
    }

    @Test void masArchivosQueElTopeNoSeBajan() {
        factura(1, LocalDate.of(2026, 10, 1));
        sire.terminado(java.util.stream.IntStream.rangeClosed(1, RegistroDeVentasSireService.MAXIMO_ARCHIVOS + 1).mapToObj(i -> "p" + i + ".zip").toArray(String[]::new));
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("RVIE_EXCEDE_EL_TOPE");
        assertThat(sire.descargas).isZero();
    }

    @Test void loQueSunatContestaSeConvierteEnErroresDelApi() {
        sire.fallo = new SunatCredencialesException("400", "SUNAT no acepta las credenciales API SUNAT (HTTP 400: unauthorized_client)");
        assertThatThrownBy(() -> conSire.solicitarPropuesta(tenant, "202610")).hasMessageContainingAll("MIGE RCE y RVIE - SIRE", "unauthorized_client")
                .extracting("codigo").isEqualTo("CREDENCIALES_SIRE_RECHAZADAS");

        sire.fallo = new SunatRechazoException("1070", "No se ha encontrado información de comprobantes de pago en Periodo Seleccionado");
        assertThatThrownBy(() -> conSire.solicitarPropuesta(tenant, "202610")).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        sire.fallo = new SunatRechazoException("1006", "Formato de perTributario no cumple con el formato yyyymm");
        assertThatThrownBy(() -> conSire.solicitarPropuesta(tenant, "202610")).hasMessageContaining("1006").extracting("codigo").isEqualTo("SIRE_RECHAZO");

        sire.fallo = new SunatTransientException("0109", "Tiempo de espera agotado");
        assertThatThrownBy(() -> conSire.estadoDePropuesta(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");
        assertThatThrownBy(() -> conSire.conciliarConSunat(tenant, "202610", "202600000123")).extracting("codigo").isEqualTo("SUNAT_NO_DISPONIBLE");
    }

    @Test void loQueElGatewayYaEsUnErrorDelApiPasaTalCual() {
        sire.fallo = new DomainException("SIRE_NO_DISPONIBLE", "apagado");
        assertThatThrownBy(() -> conSire.solicitarPropuesta(tenant, "202610")).extracting("codigo").isEqualTo("SIRE_NO_DISPONIBLE");
    }
}
