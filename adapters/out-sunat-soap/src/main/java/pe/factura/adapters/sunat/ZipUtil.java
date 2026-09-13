package pe.factura.adapters.sunat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtil {
    private ZipUtil() {}

    public static byte[] comprimir(String nombreEntrada, byte[] contenido) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry(nombreEntrada));
            zip.write(contenido);
            zip.closeEntry();
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("No se pudo comprimir " + nombreEntrada, e); }
    }

    public static byte[] extraerPrimero(byte[] zip, String sufijo) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith(sufijo.toLowerCase())) return in.readAllBytes();
            }
        } catch (IOException e) { throw new IllegalStateException("ZIP corrupto", e); }
        throw new IllegalStateException("El ZIP no contiene un archivo " + sufijo);
    }
}
