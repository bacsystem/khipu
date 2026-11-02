package pe.factura.application.port.out;

import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Los textos de correo que un administrador reemplazó (#199). Lo que no está acá sigue siendo el texto de fábrica ({@link PlantillaDeCorreo#defecto()}). */
public interface PlantillasRepository {
    /** {@code actualizadaPor}: el correo del administrador que la cambió (H11); nulo si fue la clave de la plataforma o ese administrador ya no existe. */
    record Guardada(Texto texto, Instant actualizadaEn, String actualizadaPor) {
        public Guardada(Texto texto, Instant actualizadaEn) { this(texto, actualizadaEn, null); }
    }

    Optional<Guardada> buscar(PlantillaDeCorreo tipo);

    Map<PlantillaDeCorreo, Guardada> todas();

    /** Crea o reemplaza el texto de un correo. {@code por} es el administrador que lo cambió. */
    void guardar(PlantillaDeCorreo tipo, Texto texto, Instant ahora, UUID por);

    /** Quita el texto propio: el correo vuelve al de fábrica. {@code true} si había uno. */
    boolean quitar(PlantillaDeCorreo tipo);
}
