import { AuthShell } from "@/components/auth/auth-shell";
import { AceptarInvitacionForm } from "@/components/auth/aceptar-invitacion-form";
import { consultarInvitacion, type InvitacionRecibida } from "@/lib/api/equipo";
import { ApiError } from "@/lib/api/types";
import { messages } from "@/lib/messages";

const t = messages.auth.aceptarInvitacion;

export const metadata = { title: `${t.titulo} · ${messages.app.nombre}` };

/**
 * El enlace de la invitación al equipo de una cuenta (#21). Pública: el invitado todavía no tiene usuario. Mirar la invitación no la consume (los antivirus y los
 * filtros de correo abren los enlaces); se acepta recién al enviar la contraseña.
 */
export default async function AceptarInvitacionPage({ params }: { params: Promise<{ token: string }> }) {
  // El segmento llega codificado tal como vino en el enlace; un cliente de correo puede haberlo reescrito así.
  const token = decodeURIComponent((await params).token);
  let invitacion: InvitacionRecibida | null = null;
  try {
    invitacion = await consultarInvitacion(token);
  } catch (err) {
    if (!(err instanceof ApiError) || err.codigo !== "TOKEN_INVALIDO") throw err;
  }

  if (!invitacion) {
    return (
      <AuthShell title={t.titulo} subtitle={t.subtitulo}>
        <p role="alert" className="text-sm text-destructive">{t.enlaceInvalido}</p>
      </AuthShell>
    );
  }

  const rol = messages.roles.etiquetas[invitacion.rol] ?? invitacion.rol;
  return (
    <AuthShell title={t.titulo} subtitle={t.subtitulo}>
      <div className="grid gap-4">
        <div className="grid gap-1 text-sm">
          <p data-testid="invitacion-detalle">{t.invitado.replace("{cuenta}", invitacion.cuenta).replace("{rol}", rol)}</p>
          <p className="text-muted-foreground">{t.email.replace("{email}", invitacion.email)}</p>
        </div>
        <AceptarInvitacionForm token={token} email={invitacion.email} />
      </div>
    </AuthShell>
  );
}
