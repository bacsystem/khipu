import { AuthShell } from "@/components/auth/auth-shell";
import { AceptarTerminos } from "@/components/legal/aceptar-terminos";
import { messages } from "@/lib/messages";
import type { Usuario } from "@/lib/api/auth";

/**
 * #342: quien no aceptó la versión vigente de los términos no usa el panel hasta aceptarla. Una sesión de soporte (#184) no se detiene: el
 * administrador no acepta en nombre del cliente (el backend se lo niega) y tiene que poder mirar. Sin el campo (un backend anterior) no se
 * pide nada.
 */
export function debeAceptarTerminos(usuario: Pick<Usuario, "terminos_pendientes" | "soporte_hasta">): boolean {
  return usuario.terminos_pendientes === true && !usuario.soporte_hasta;
}

/** Ocupa toda la página en lugar del panel: no hay menú ni nada detrás a lo que llegar sin aceptar. */
export function PantallaDeTerminos() {
  return (
    <AuthShell title={messages.aceptarTerminos.titulo} subtitle={messages.app.nombre}>
      <AceptarTerminos />
    </AuthShell>
  );
}
