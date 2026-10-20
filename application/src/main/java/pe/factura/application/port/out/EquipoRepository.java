package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Equipo;

import java.util.UUID;

/** Los usuarios de una cuenta (#21). Cambiarlos se hace con {@link UsuarioRepository#guardar}. */
public interface EquipoRepository {
    /** Todos los usuarios de la cuenta, activos o no, por fecha de alta. */
    Equipo deLaCuenta(UUID cuentaId);

    /**
     * Lo mismo, bloqueando a los usuarios de la cuenta hasta el final de la transacción: dos administradores que se quitan el rol a la vez no dejan la cuenta sin
     * administrador, porque el segundo ve el cambio del primero. Solo dentro de una transacción.
     */
    Equipo deLaCuentaParaCambiar(UUID cuentaId);
}
