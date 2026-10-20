package pe.factura.application.port.out;

import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.util.Optional;
import java.util.UUID;

/** Las aceptaciones de los términos y la política de privacidad (#342): una por usuario y versión, con la fecha. */
public interface AceptacionDeTerminosRepository {
    /** Si el usuario ya había aceptado esa versión no cambia nada: vale la primera fecha. */
    void registrar(AceptacionDeTerminos aceptacion);

    Optional<AceptacionDeTerminos> buscar(UUID usuarioId, String version);
}
