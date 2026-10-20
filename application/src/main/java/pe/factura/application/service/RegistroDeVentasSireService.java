package pe.factura.application.service;

import pe.factura.application.port.in.RegistroDeVentasSireUseCase;
import pe.factura.application.port.out.RegistroDeVentasRepository;
import pe.factura.application.port.out.RegistroDeVentasRepository.Posicion;
import pe.factura.application.port.out.RegistroDeVentasRepository.Referencia;
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
import java.io.BufferedReader;
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
    /** Comprobantes por archivo: acota el tiempo de una descarga. Un mes con más se pide a soporte (el SIRE no admite partir un periodo). */
    static final int MAXIMO_REGISTROS = 100_000;
    static final int TRAMO = 500;
    /** Cuántos comprobantes sin tipo de cambio se nombran en el error. */
    static final int EJEMPLOS = 10;
    /** Tope del archivo de la propuesta que se recibe, tal como llega (zip o txt), y de su texto ya descomprimido: una fila del RVIE ocupa unos 300 bytes. */
    static final long MAXIMO_ARCHIVO = 20L * 1024 * 1024;
    static final long MAXIMO_DESCOMPRIMIDO = 64L * 1024 * 1024;

    private final RegistroDeVentasRepository registro;
    private final TenantRepository tenants;
    private final Clock clock;

    public RegistroDeVentasSireService(RegistroDeVentasRepository registro, TenantRepository tenants, Clock clock) {
        this.registro = registro; this.tenants = tenants; this.clock = clock;
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
        long pendientes = registro.contar(tenantId, p.desde(), p.hasta(), 0).pendientesDeSunat();
        ConciliacionRvie conciliacion = new ConciliacionRvie();
        recorrer(tenantId, p, false, f -> conciliacion.deKhipu(f.importes()), t.ruc(), t.razonSocial());
        PropuestaRvie lector = new PropuestaRvie(t.ruc(), p);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(texto(propuesta), FormatoRvie.CODIFICACION))) {
            String linea;
            long n = 0, filas = 0;
            while ((linea = r.readLine()) != null) {
                ImportesRvie fila = lector.leer(linea, ++n);
                if (fila == null) continue;
                if (++filas > MAXIMO_REGISTROS)
                    throw new DomainException("RVIE_EXCEDE_EL_TOPE", "La propuesta tiene más de " + MAXIMO_REGISTROS + " comprobantes: pídale la conciliación a soporte");
                conciliacion.deSunat(fila);
            }
        }
        return new Conciliacion(p, pendientes, conciliacion.resultado());
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
     * El texto de la propuesta: el archivo tal cual si es un {@code .txt}, o el único {@code .txt} de un ZIP (el SIRE entrega la propuesta comprimida). Los dos topes
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

    /** Corta la lectura al pasar {@code maximo} bytes, con un {@code RVIE_ARCHIVO_DEMASIADO_GRANDE}. */
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
            if (leidos > maximo) throw new DomainException("RVIE_ARCHIVO_DEMASIADO_GRANDE", mensaje);
        }

        @Override public long skip(long n) throws IOException {
            long s = super.skip(n);
            if (s > 0) contar((int) Math.min(s, Integer.MAX_VALUE));
            return s;
        }

        @Override public boolean markSupported() { return false; }
    }
}
