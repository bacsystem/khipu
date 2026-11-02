package pe.factura.adapters.rest;

import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Descarga masiva (#324): los archivos de los comprobantes que cumplen un filtro, en un ZIP que se escribe a medida que se leen (un comprobante a la vez en memoria). El tope
 * por petición ({@link #MAXIMO}) se comprueba antes de empezar, con el mismo conteo que el listado: así el cliente recibe un 422 que le dice cuántos hay, no un ZIP truncado.
 *
 * <p>Un comprobante todavía sin firmar no tiene archivos y se omite; los que fallan a mitad de camino (un archivo que falta en el storage) no tumban la descarga: quedan
 * anotados en {@code LEEME.txt} dentro del ZIP.
 */
final class DescargaEnZip {
    /** Cuántos comprobantes admite una descarga: acota el tiempo y la carga del servidor. Más que eso se pide por tramos de fechas. */
    static final int MAXIMO = 500;
    private static final int PAGINA = 50;

    /** Qué archivos de cada comprobante van en el ZIP. */
    record Contenido(boolean xml, boolean pdf, boolean cdr) {
        static Contenido de(String tipos) {
            boolean xml = false, pdf = false, cdr = false;
            for (String t : (tipos == null || tipos.isBlank() ? "xml,pdf,cdr" : tipos).split(",")) {
                switch (t.strip().toLowerCase(java.util.Locale.ROOT)) {
                    case "xml" -> xml = true;
                    case "pdf" -> pdf = true;
                    case "cdr" -> cdr = true;
                    default -> throw new DomainException("PARAMETRO_INVALIDO", "tipos admite xml, pdf y cdr separados por coma: " + t.strip());
                }
            }
            return new Contenido(xml, pdf, cdr);
        }
    }

    private DescargaEnZip() {}

    /** Valida el tope y la existencia de resultados; devuelve cuántos comprobantes entran. */
    static long verificar(ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro) {
        long total = consultar.contar(tenant, filtro);
        if (total == 0) throw new DomainException("NO_ENCONTRADO", "No hay comprobantes con esos filtros");
        if (total > MAXIMO)
            throw new DomainException("DESCARGA_EXCEDE_EL_TOPE", "Hay " + total + " comprobantes con esos filtros y una descarga admite hasta " + MAXIMO + ": acote las fechas y descargue por tramos");
        return total;
    }

    static void escribir(OutputStream salida, ConsultarComprobanteUseCase consultar, UUID tenant, Filtro filtro, Contenido contenido, long total) throws IOException {
        List<String> omitidos = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            int escritos = 0;
            for (int pagina = 1; escritos < total; pagina++) {
                List<Comprobante> lote = consultar.listar(tenant, filtro, pagina, PAGINA);
                if (lote.isEmpty()) break;
                for (Comprobante c : lote) {
                    if (escritos++ >= total) break;
                    agregar(zip, consultar, tenant, c, contenido, omitidos);
                }
            }
            if (!omitidos.isEmpty()) {
                zip.putNextEntry(new ZipEntry("LEEME.txt"));
                zip.write(("No se pudo incluir:\n" + String.join("\n", omitidos) + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static void agregar(ZipOutputStream zip, ConsultarComprobanteUseCase consultar, UUID tenant, Comprobante c, Contenido contenido, List<String> omitidos) throws IOException {
        // Sin firma no hay archivos que descargar: ni XML, ni PDF, ni CDR.
        if (c.xmlKey() == null) return;
        String nombre = c.nombreArchivo();
        if (contenido.xml()) copiar(zip, nombre + ".xml", () -> consultar.xml(tenant, c.id()), omitidos);
        if (contenido.pdf()) copiar(zip, nombre + ".pdf", () -> consultar.pdf(tenant, c.id()), omitidos);
        if (contenido.cdr() && c.cdrKey() != null) copiar(zip, "R-" + nombre + ".zip", () -> consultar.cdr(tenant, c.id()), omitidos);
    }

    private interface Lector { byte[] leer(); }

    private static void copiar(ZipOutputStream zip, String entrada, Lector lector, List<String> omitidos) throws IOException {
        byte[] datos;
        try {
            datos = lector.leer();
        } catch (RuntimeException e) {
            omitidos.add(entrada + " (" + e.getMessage() + ")");
            return;
        }
        zip.putNextEntry(new ZipEntry(entrada));
        zip.write(datos);
        zip.closeEntry();
    }
}
