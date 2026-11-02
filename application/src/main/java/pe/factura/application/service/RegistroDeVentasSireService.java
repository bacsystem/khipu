package pe.factura.application.service;

import pe.factura.application.port.in.RegistroDeVentasSireUseCase;
import pe.factura.application.port.out.RegistroDeVentasRepository;
import pe.factura.application.port.out.RegistroDeVentasRepository.Posicion;
import pe.factura.application.port.out.RegistroDeVentasRepository.Referencia;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatSireGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.sire.ConciliacionRvie;
import pe.factura.domain.sire.FilaRvie;
import pe.factura.domain.sire.FormatoRvie;
import pe.factura.domain.sire.ImportesRvie;
import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.sire.PropuestaRvie;
import pe.factura.domain.tenant.Tenant;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * El registro de ventas del SIRE (#322). El archivo se escribe por tramos de {@value #TRAMO} comprobantes (uno en memoria por vez, con sus ítems), en el orden
 * fecha, tipo, serie y número; antes de escribir nada se valida el periodo y se cuenta lo que entra, para que cualquier error llegue como un {@code 4xx} con su
 * código y no como un archivo a medias.
 */
public class RegistroDeVentasSireService implements RegistroDeVentasSireUseCase {
    /** Comprobantes por archivo: acota el tiempo de una descarga. Un mes con más se pide a soporte. */
    static final int MAXIMO_REGISTROS = 100_000;
    static final int TRAMO = 500;
    /** Cuántos comprobantes sin tipo de cambio se nombran en el error. */
    static final int EJEMPLOS = 10;
    /** Tope del archivo de la propuesta que se recibe, tal como llega (zip o txt), y de su texto ya descomprimido: una fila del RVIE ocupa unos 300 bytes. */
    static final long MAXIMO_ARCHIVO = 20L * 1024 * 1024;
    static final long MAXIMO_DESCOMPRIMIDO = 64L * 1024 * 1024;

    /** Archivos de la propuesta que se concilian de un ticket: SUNAT parte un periodo grande en varios, pero más que esto es otra cosa. */
    static final int MAXIMO_ARCHIVOS = 50;

    private final RegistroDeVentasRepository registro;
    private final TenantRepository tenants;
    private final SunatSireGateway sire;
    private final Clock clock;

    public RegistroDeVentasSireService(RegistroDeVentasRepository registro, TenantRepository tenants, SunatSireGateway sire, Clock clock) {
        this.registro = registro; this.tenants = tenants; this.sire = sire; this.clock = clock;
    }

    @Override public Exportacion preparar(UUID tenantId, String periodo, boolean permitirSinTipoCambio) {
        PeriodoSire p = periodo(periodo);
        Tenant t = tenant(tenantId);
        RegistroDeVentasRepository.Conteo conteo = registro.contar(tenantId, p.desde(), p.hasta(), EJEMPLOS);
        if (conteo.incluidos() == 0)
            throw new DomainException("NO_ENCONTRADO", "No hay comprobantes aceptados ni anulados por SUNAT emitidos en el periodo " + p
                    + (conteo.pendientesDeSunat() > 0 ? " (" + conteo.pendientesDeSunat() + " siguen en camino a SUNAT y entrarán cuando los acepte)" : ""));
        if (conteo.incluidos() > MAXIMO_REGISTROS)
            throw new DomainException("RVIE_EXCEDE_EL_TOPE", "El periodo " + p + " tiene " + conteo.incluidos() + " comprobantes y un archivo del registro de ventas admite hasta "
                    + MAXIMO_REGISTROS + ": pídalo a soporte");
        if (conteo.sinTipoCambio() > 0 && !permitirSinTipoCambio)
            throw new DomainException("RVIE_SIN_TIPO_CAMBIO", conteo.sinTipoCambio() + " comprobante(s) del periodo " + p + " en otra moneda no tienen tipo de cambio ("
                    + String.join(", ", conteo.ejemplosSinTipoCambio()) + (conteo.sinTipoCambio() > conteo.ejemplosSinTipoCambio().size() ? "…" : "")
                    + "), y el campo 28 del registro de ventas lo pide: complételo en el archivo antes de subirlo al SIRE y vuelva a pedirlo con permitir_sin_tipo_cambio=true");
        return new Exportacion(tenantId, t.ruc(), t.razonSocial(), p, FormatoRvie.nombreArchivo(t.ruc(), p), conteo.incluidos(), conteo.pendientesDeSunat(), conteo.sinTipoCambio());
    }

    @Override public void escribir(Exportacion e, OutputStream salida) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(salida, FormatoRvie.CODIFICACION);
        zip.putNextEntry(new ZipEntry(e.nombreArchivo() + ".txt"));
        try {
            recorrer(e.tenantId(), e.periodo(), true, f -> {
                try {
                    zip.write(FormatoRvie.linea(f).getBytes(FormatoRvie.CODIFICACION));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }, e.ruc(), e.razonSocial());
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
        // Solo aquí, con todo escrito, el ZIP recibe su directorio central. Si algo falló antes, la excepción sale sin cerrarlo: el archivo queda inválido a propósito.
        zip.closeEntry();
        zip.close();
    }

    @Override public Conciliacion conciliar(UUID tenantId, String periodo, InputStream propuesta) throws IOException {
        PeriodoSire p = periodo(periodo);
        Tenant t = tenant(tenantId);
        long pendientes = pendientesDeSunat(tenantId, p);
        ConciliacionRvie conciliacion = conLoEmitido(tenantId, p, t);
        PropuestaRvie lector = new PropuestaRvie(t.ruc(), p);
        long filas = leerPropuesta(propuesta, lector, conciliacion, 0, "");
        // H11: sin ninguna fila de datos (cuerpo vacío, solo títulos) el reporte diría que SUNAT no tiene nada: es un archivo equivocado, no una conciliación.
        if (filas == 0 && lector.consolidadas() == 0)
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta no trae ninguna fila de comprobantes: envíe el archivo descargado del SIRE para el periodo " + p);
        return new Conciliacion(p, pendientes, lector.consolidadas(), conciliacion.resultado());
    }

    private long pendientesDeSunat(UUID tenantId, PeriodoSire p) { return registro.contar(tenantId, p.desde(), p.hasta(), 0).pendientesDeSunat(); }

    /** Una conciliación con el lado de khipu ya cargado: cada comprobante del periodo que SUNAT tiene, en el orden del registro. */
    private ConciliacionRvie conLoEmitido(UUID tenantId, PeriodoSire p, Tenant t) {
        ConciliacionRvie conciliacion = new ConciliacionRvie();
        recorrer(tenantId, p, false, f -> conciliacion.deKhipu(f.importes()), t.ruc(), t.razonSocial());
        return conciliacion;
    }

    /**
     * Lee un archivo de la propuesta ({@code .txt} o {@code .zip} con un {@code .txt}) hacia la conciliación y devuelve cuántas filas de comprobantes llevan leídas en
     * total ({@code yaLeidas} más las de este archivo: el tope es del periodo, no de cada archivo). {@code donde} se antepone al mensaje de un error de lectura, para decir
     * en cuál de los archivos de un ticket fue.
     */
    private long leerPropuesta(InputStream propuesta, PropuestaRvie lector, ConciliacionRvie conciliacion, long yaLeidas, String donde) throws IOException {
        long filas = yaLeidas;
        // H11: un ZIP cuyos datos comprimidos están dañados falla al leerlo, no al abrirlo: el recorrido entero responde 422, no 500.
        try (java.io.Reader r = new java.io.BufferedReader(new InputStreamReader(texto(propuesta), FormatoRvie.CODIFICACION))) {
            String linea;
            long n = 0;
            while ((linea = linea(r, n + 1)) != null) {
                ImportesRvie fila = lector.leer(linea, ++n);
                if (fila == null) continue;
                if (++filas > MAXIMO_REGISTROS)
                    throw new DomainException("RVIE_EXCEDE_EL_TOPE", "La propuesta tiene más de " + MAXIMO_REGISTROS + " comprobantes: pídale la conciliación a soporte");
                conciliacion.deSunat(fila);
            }
        } catch (ZipException | java.io.EOFException ex) {
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", donde + "El ZIP de la propuesta está dañado: " + ex.getMessage());
        } catch (DomainException ex) {
            if (donde.isEmpty() || !"RVIE_ARCHIVO_INVALIDO".equals(ex.codigo())) throw ex;
            throw new DomainException(ex.codigo(), donde + ex.getMessage());
        }
        return filas;
    }

    @Override public PropuestaSolicitada solicitarPropuesta(UUID tenantId, String periodo) {
        PeriodoSire p = periodo(periodo);
        Tenant t = tenant(tenantId);
        return new PropuestaSolicitada(p, delSire(() -> sire.solicitarPropuesta(t, p)));
    }

    @Override public EstadoDePropuesta estadoDePropuesta(UUID tenantId, String periodo, String ticket) {
        PeriodoSire p = periodo(periodo);
        Tenant t = tenant(tenantId);
        SunatSireGateway.EstadoDelTicket e = delSire(() -> sire.consultar(t, p, ticket));
        return new EstadoDePropuesta(p, e.ticket(), e.fase(), e.codigoSunat(), e.descripcionSunat(), e.archivos().size());
    }

    @Override public Conciliacion conciliarConSunat(UUID tenantId, String periodo, String ticket) throws IOException {
        PeriodoSire p = periodo(periodo);
        Tenant t = tenant(tenantId);
        SunatSireGateway.EstadoDelTicket e = delSire(() -> sire.consultar(t, p, ticket));
        if (e.fase() == SunatSireGateway.Fase.EN_PROCESO)
            throw new DomainException("PROPUESTA_NO_LISTA", "SUNAT todavía prepara la propuesta del ticket " + ticket + " (" + e.descripcionSunat() + "): consulte su estado y vuelva a pedir la conciliación al terminar");
        if (e.fase() == SunatSireGateway.Fase.CON_ERRORES)
            throw new DomainException("PROPUESTA_CON_ERRORES", "SUNAT terminó el ticket " + ticket + " con errores (" + e.descripcionSunat() + "): pida la propuesta de nuevo");
        if (e.archivos().isEmpty())
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "SUNAT terminó el ticket " + ticket + " sin entregar ningún archivo de la propuesta");
        if (e.archivos().size() > MAXIMO_ARCHIVOS)
            throw new DomainException("RVIE_EXCEDE_EL_TOPE", "SUNAT entregó " + e.archivos().size() + " archivos para el ticket " + ticket + " y khipu concilia hasta " + MAXIMO_ARCHIVOS + ": pídale la conciliación a soporte");
        long pendientes = pendientesDeSunat(tenantId, p);
        ConciliacionRvie conciliacion = conLoEmitido(tenantId, p, t);
        PropuestaRvie lector = new PropuestaRvie(t.ruc(), p);
        long filas = 0;
        // El tope de bytes es de todo el ticket: un periodo partido en archivos no puede, en suma, llevar la conciliación más allá de lo que admite el archivo subido.
        long restante = MAXIMO_ARCHIVO;
        int numero = 0;
        for (SunatSireGateway.ArchivoDelTicket a : e.archivos()) {
            numero++;
            byte[] bytes = delSire(() -> sire.descargar(t, a, MAXIMO_ARCHIVO));
            restante -= bytes.length;
            if (restante < 0)
                throw new DomainException("RVIE_PROPUESTA_DEMASIADO_GRANDE", "Los archivos de la propuesta de SUNAT pasan, en suma, de los " + MAXIMO_ARCHIVO / (1024 * 1024) + " MB que khipu concilia");
            filas = leerPropuesta(new java.io.ByteArrayInputStream(bytes), lector, conciliacion, filas,
                    e.archivos().size() > 1 ? "Archivo " + numero + " de " + e.archivos().size() + " de la propuesta de SUNAT (" + a.nombre() + "): " : "");
        }
        if (filas == 0 && lector.consolidadas() == 0)
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta que entregó SUNAT no trae ninguna fila de comprobantes para el periodo " + p);
        return new Conciliacion(p, pendientes, lector.consolidadas(), conciliacion.resultado());
    }

    /**
     * Lo que SUNAT contesta al SIRE, como errores del API: las credenciales que no acepta, el pedido que rechaza por sus datos (1070, que no tiene comprobantes en el
     * periodo, es {@code NO_ENCONTRADO}) y su caída o demora. Los mensajes ya vienen sin secretos.
     */
    private static <T> T delSire(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (SunatCredencialesException ex) {
            throw new DomainException("CREDENCIALES_SIRE_RECHAZADAS", "SUNAT no acepta las credenciales para el SIRE: revise las credenciales API SUNAT (la aplicación debe estar registrada en SOL como «MIGE RCE y RVIE - SIRE») "
                    + "y la clave SOL. " + ex.getMessage());
        } catch (SunatRechazoException ex) {
            if ("1070".equals(ex.codigo())) throw new DomainException("NO_ENCONTRADO", "SUNAT no tiene comprobantes en ese periodo: " + ex.descripcion());
            throw new DomainException("SIRE_RECHAZO", "SUNAT rechazó el pedido al SIRE (" + ex.codigo() + "): " + ex.descripcion());
        } catch (SunatTransientException ex) {
            throw new DomainException("SUNAT_NO_DISPONIBLE", "No se pudo hablar con el SIRE de SUNAT, reintente en unos minutos: " + ex.getMessage());
        }
    }

    /** Largo máximo de una línea de la propuesta: una fila del RVIE ocupa unos 300 caracteres y su campo más largo admite 1 500. */
    static final int MAXIMO_LINEA = 16 * 1024;

    /**
     * H5: la línea siguiente (sin el fin de línea), o nula al terminar. Corta en {@link #MAXIMO_LINEA}: un archivo sin saltos de línea no se acumula entero en
     * memoria hasta llegar al tope de bytes.
     */
    static String linea(java.io.Reader r, long numero) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        int c;
        while ((c = r.read()) != -1) {
            if (c == 10) return sb.toString();
            if (sb.length() >= MAXIMO_LINEA)
                throw new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta del RVIE no se puede leer (línea " + numero + "): pasa de " + MAXIMO_LINEA
                        + " caracteres sin un salto de línea; una fila del registro de ventas ocupa unos cientos");
            sb.append((char) c);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Cada fila del periodo, en orden. {@code conFechasModificadas}: busca la fecha de emisión del comprobante que modifica cada nota (campo 29), una consulta por
     * tramo; la conciliación no la compara y no la pide.
     */
    private void recorrer(UUID tenantId, PeriodoSire p, boolean conFechasModificadas, Consumer<FilaRvie> fila, String ruc, String razonSocial) {
        Posicion despues = null;
        while (true) {
            List<Comprobante> tramo = registro.siguientes(tenantId, p.desde(), p.hasta(), despues, TRAMO);
            if (tramo.isEmpty()) return;
            Map<Referencia, LocalDate> fechas = conFechasModificadas ? fechasModificadas(tenantId, tramo) : Map.of();
            for (Comprobante c : tramo) {
                LocalDate modificado = c.nota() == null ? null : fechas.get(new Referencia(c.nota().tipoAfectado().codigo(), c.nota().serieAfectada(), c.nota().numeroAfectado()));
                fila.accept(FilaRvie.de(ruc, razonSocial, p, c, modificado));
            }
            if (tramo.size() < TRAMO) return;
            despues = Posicion.de(tramo.get(tramo.size() - 1));
        }
    }

    private Map<Referencia, LocalDate> fechasModificadas(UUID tenantId, List<Comprobante> tramo) {
        Set<Referencia> refs = new LinkedHashSet<>();
        for (Comprobante c : tramo)
            if (c.nota() != null) refs.add(new Referencia(c.nota().tipoAfectado().codigo(), c.nota().serieAfectada(), c.nota().numeroAfectado()));
        return refs.isEmpty() ? Map.of() : registro.fechasDeEmision(tenantId, refs);
    }

    private PeriodoSire periodo(String texto) {
        PeriodoSire p = PeriodoSire.de(texto);
        p.exigirIniciado(LocalDate.now(clock));
        return p;
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Empresa no encontrada"));
    }

    /**
     * El texto de la propuesta: el archivo tal cual si es un {@code .txt}, o el primer {@code .txt} de un ZIP. Los dos topes
     * ({@link #MAXIMO_ARCHIVO}, {@link #MAXIMO_DESCOMPRIMIDO}) se cuentan mientras se lee: un ZIP que se infla sin medida corta en el tope, no agota la memoria.
     */
    static InputStream texto(InputStream propuesta) throws IOException {
        BufferedInputStream b = new BufferedInputStream(new Acotado(propuesta, MAXIMO_ARCHIVO, "El archivo de la propuesta supera los " + MAXIMO_ARCHIVO / (1024 * 1024) + " MB"));
        b.mark(4);
        byte[] cabecera = b.readNBytes(4);
        b.reset();
        boolean esZip = cabecera.length == 4 && cabecera[0] == 'P' && cabecera[1] == 'K' && cabecera[2] == 3 && cabecera[3] == 4;
        if (!esZip) return b;
        ZipInputStream zip = new ZipInputStream(b, FormatoRvie.CODIFICACION);
        ZipEntry entrada;
        try {
            while ((entrada = zip.getNextEntry()) != null) {
                if (!entrada.isDirectory() && entrada.getName().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    return new Acotado(zip, MAXIMO_DESCOMPRIMIDO, "El texto de la propuesta supera los " + MAXIMO_DESCOMPRIMIDO / (1024 * 1024) + " MB");
            }
        } catch (ZipException ex) {
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "El ZIP de la propuesta está dañado: " + ex.getMessage());
        }
        throw new DomainException("RVIE_ARCHIVO_INVALIDO", "El ZIP de la propuesta no trae ningún archivo .txt");
    }

    /** Corta la lectura al pasar {@code maximo} bytes, con un {@code RVIE_PROPUESTA_DEMASIADO_GRANDE}. */
    static final class Acotado extends FilterInputStream {
        private final long maximo;
        private final String mensaje;
        private long leidos;

        Acotado(InputStream in, long maximo, String mensaje) { super(in); this.maximo = maximo; this.mensaje = mensaje; }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) contar(1);
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) contar(n);
            return n;
        }

        private void contar(int n) {
            leidos += n;
            if (leidos > maximo) throw new DomainException("RVIE_PROPUESTA_DEMASIADO_GRANDE", mensaje);
        }

        @Override public long skip(long n) throws IOException {
            long s = super.skip(n);
            if (s > 0) contar((int) Math.min(s, Integer.MAX_VALUE));
            return s;
        }

        @Override public boolean markSupported() { return false; }
    }
}
