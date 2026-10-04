package pe.factura.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El historial de accesos de soporte a una cuenta (#184): cada vez que alguien del equipo de khipu miró el portal como uno de sus usuarios. Lo ve el propio
 * cliente. Dice cuándo, a qué usuario y por cuánto tiempo; **no** dice qué administrador fue (es «el equipo de soporte»: su identidad es de la bitácora
 * interna), y no hay nada que pedir al soporte para ocultar un acceso.
 */
public interface AccesosDeSoporteUseCase {
    /** Los más recientes primero, hasta {@link #MAXIMO}. */
    List<AccesoDeSoporte> deLaCuenta(UUID cuentaId);

    /** Lo más que se devuelve: es un historial para leer, no un volcado. */
    int MAXIMO = 100;

    /** {@code usuario} y {@code duracionSegundos} son nulos si el registro no tiene el formato que se entiende (p. ej. de una versión futura). */
    record AccesoDeSoporte(Instant ocurridoEn, String usuario, Long duracionSegundos) {}
}
