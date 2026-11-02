package pe.factura.application.port.out;

import pe.factura.domain.plataforma.BannerDeMantenimiento;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** El aviso de mantenimiento publicado (#199). Hay uno solo: publicar otro reemplaza al anterior, vigente o no. */
public interface BannerRepository {
    record Guardado(BannerDeMantenimiento banner, Instant actualizadoEn) {}

    Optional<Guardado> buscar();

    /** Crea o reemplaza el aviso. {@code por} es el administrador que lo publicó. */
    void guardar(BannerDeMantenimiento banner, Instant ahora, UUID por);

    /** Retira el aviso. {@code true} si había uno. */
    boolean retirar();
}
