package pe.factura.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/** De dónde sale el archivo del padrón reducido (#361): la descarga pública de SUNAT, o un archivo local. */
public interface FuentePadronRuc {
    /** El archivo ya abierto: el texto del padrón (ISO-8859-1), sin comprimir. Quien lo abre lo cierra. */
    Descarga abrir() throws IOException;

    interface Descarga extends AutoCloseable {
        InputStream contenido();

        /** Dónde se obtuvo (una URL o una ruta), para el registro de cargas. */
        String origen();

        @Override void close() throws IOException;
    }
}
