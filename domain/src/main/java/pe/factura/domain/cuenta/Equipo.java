package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.util.List;
import java.util.UUID;

/**
 * Los usuarios de una cuenta (#21), con las reglas para cambiarles el rol o desactivarlos: lo hace un administrador activo de la misma cuenta, y la cuenta nunca se queda sin
 * un administrador activo (si no, nadie podría volver a invitar, cambiar la configuración ni reactivar a nadie). Cada operación devuelve el usuario como queda; guardarlo es
 * del servicio.
 */
public record Equipo(UUID cuentaId, List<Usuario> miembros) {
    public Equipo {
        miembros = List.copyOf(miembros);
        for (Usuario u : miembros)
            if (!u.cuentaId().equals(cuentaId)) throw new IllegalArgumentException("Un miembro de otra cuenta en el equipo de " + cuentaId);
    }

    public Usuario cambiarRol(UUID actorId, UUID usuarioId, Rol nuevo) {
        if (nuevo == null) throw new DomainException("ROL_REQUERIDO", "Rol requerido");
        exigirAdministrador(actorId);
        Usuario u = miembro(usuarioId);
        if (u.rol() == nuevo) return u;
        if (u.esAdminActivo() && nuevo != Rol.ADMIN) exigirOtroAdministrador(u, "No puedes quitarle el rol de administrador: es el único administrador activo de la cuenta");
        return u.conRol(nuevo);
    }

    public Usuario desactivar(UUID actorId, UUID usuarioId) {
        exigirAdministrador(actorId);
        if (actorId.equals(usuarioId)) throw new DomainException("NO_PUEDES_DESACTIVARTE", "No puedes desactivar tu propio usuario: pídeselo a otro administrador");
        Usuario u = miembro(usuarioId);
        if (!u.activo()) return u;
        if (u.esAdminActivo()) exigirOtroAdministrador(u, "No puedes desactivarlo: es el único administrador activo de la cuenta");
        return u.desactivar();
    }

    public Usuario reactivar(UUID actorId, UUID usuarioId) {
        exigirAdministrador(actorId);
        Usuario u = miembro(usuarioId);
        return u.activo() ? u : u.reactivar();
    }

    /** Quien invita, cambia roles o desactiva: un administrador activo de esta cuenta. Segunda barrera tras el filtro de la API, que ya lo exige por ruta. */
    public void exigirAdministrador(UUID actorId) {
        boolean esAdmin = miembros.stream().anyMatch(m -> m.id().equals(actorId) && m.esAdminActivo());
        if (!esAdmin) throw new DomainException("ROL_INSUFICIENTE", "Solo un administrador de la cuenta puede gestionar su equipo");
    }

    public Usuario miembro(UUID usuarioId) {
        return miembros.stream().filter(m -> m.id().equals(usuarioId)).findFirst()
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Usuario no encontrado en tu cuenta"));
    }

    public boolean tieneCorreo(String email) {
        String normalizado = Usuario.normalizarEmail(email);
        return miembros.stream().anyMatch(m -> m.email().equals(normalizado));
    }

    private void exigirOtroAdministrador(Usuario quien, String mensaje) {
        boolean hayOtro = miembros.stream().anyMatch(m -> !m.id().equals(quien.id()) && m.esAdminActivo());
        if (!hayOtro) throw new DomainException("ULTIMO_ADMINISTRADOR", mensaje);
    }
}
