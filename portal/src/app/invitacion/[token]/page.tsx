import { AuthShell } from "@/components/auth/auth-shell";
import { AceptarInvitacionForm } from "@/components/auth/aceptar-invitacion-form";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { consultarInvitacion, type InvitacionRecibida } from "@/lib/api/equipo";
import { ApiError } from "@/lib/api/types";
import { getMessages } from "next-intl/server";
import type { Metadata } from "next";
import { interpolar } from "@/i18n/mensajes";
import { tokenDeEnlace } from "@/lib/token-de-enlace";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.auth.aceptarInvitacion.titulo} · ${messages.app.nombre}` };
}

/**
 * El enlace de la invitación al equipo de una cuenta (#21). Pública: el invitado todavía no tiene usuario. Mirar la invitación no la consume (los antivirus y los
 * filtros de correo abren los enlaces); se acepta recién al enviar la contraseña.
 */
export default async function AceptarInvitacionPage({ params }: { params: Promise<{ token: string }> }) {
  // El segmento llega codificado tal como vino en el enlace; un cliente de correo puede haberlo reescrito así. Mal codificado, es un enlace inválido.
  const token = tokenDeEnlace((await params).token);
  const messages = await getMessages();
  const t = messages.auth.aceptarInvitacion;
  let invitacion: InvitacionRecibida | null = null;
  if (token !== null) {
    try {
      invitacion = await consultarInvitacion(token);
    } catch (err) {
      if (!(err instanceof ApiError) || err.codigo !== "TOKEN_INVALIDO") throw err;
    }
  }

  if (!invitacion || token === null) {
    return (
      <AuthShell title={t.titulo} subtitle={t.subtitulo}>
        <Alerta tono="error">{t.enlaceInvalido}</Alerta>
      </AuthShell>
    );
  }

  const rol = (messages.roles.etiquetas as Record<string, string>)[invitacion.rol] ?? invitacion.rol;
  return (
    <AuthShell title={t.titulo} subtitle={t.subtitulo}>
      <div className="grid gap-4">
        <div className="grid gap-1 text-[13px]">
          <p data-testid="invitacion-detalle">{interpolar(t.invitado, { cuenta: invitacion.cuenta, rol })}</p>
          <p className="text-muted-foreground">{interpolar(t.email, { email: invitacion.email })}</p>
        </div>
        <AceptarInvitacionForm token={token} email={invitacion.email} />
      </div>
    </AuthShell>
  );
}
