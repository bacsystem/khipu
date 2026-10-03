import { AuthShell } from "@/components/auth/auth-shell";
import { RestablecerForm } from "@/components/auth/restablecer-form";
import { messages } from "@/lib/messages";

export const metadata = { title: `${messages.auth.restablecer.titulo} · ${messages.app.nombre}` };

/**
 * El enlace de la invitación del alta asistida (#188) es este mismo con `?invitacion=1`: el cliente crea su contraseña por primera
 * vez en vez de restablecer una. Cualquier otro valor del parámetro cuenta como un restablecer normal.
 */
export default async function RestablecerPage({
  params,
  searchParams,
}: {
  params: Promise<{ token: string }>;
  searchParams: Promise<{ invitacion?: string }>;
}) {
  const { token } = await params;
  const invitacion = (await searchParams).invitacion === "1";
  const t = invitacion ? messages.auth.invitacion : messages.auth.restablecer;
  return (
    <AuthShell title={t.titulo} subtitle={t.subtitulo}>
      <RestablecerForm token={token} invitacion={invitacion} />
    </AuthShell>
  );
}
