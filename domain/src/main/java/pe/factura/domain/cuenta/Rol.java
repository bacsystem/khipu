package pe.factura.domain.cuenta;

import java.util.EnumSet;
import java.util.Set;

/**
 * El rol de un usuario dentro de su cuenta (#21): el administrador todo; el emisor emite y consulta, pero no toca la configuración ni el equipo; el de solo lectura mira.
 */
public enum Rol {
    ADMIN(EnumSet.allOf(Permiso.class)),
    EMISOR(EnumSet.of(Permiso.LEER, Permiso.EMITIR)),
    LECTURA(EnumSet.of(Permiso.LEER));

    private final Set<Permiso> permisos;

    Rol(Set<Permiso> permisos) { this.permisos = permisos; }

    public boolean puede(Permiso permiso) { return permisos.contains(permiso); }

    /** Cómo se le dice al usuario («Administrador», «Emisor», «Solo lectura»): el portal y los correos usan la misma etiqueta. */
    public String etiqueta() {
        return switch (this) {
            case ADMIN -> "Administrador";
            case EMISOR -> "Emisor";
            case LECTURA -> "Solo lectura";
        };
    }
}
