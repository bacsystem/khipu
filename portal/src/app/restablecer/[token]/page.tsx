import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/auth-shell";
import { RestablecerForm } from "@/components/auth/restablecer-form";
import { messages } from "@/lib/messages";

type BusquedaRestablecer = Promise<{ invitacion?: string }>;

/**
 * El enlace de la invitación del alta asistida (#188) es este mismo con `?invitacion=1`: el cliente crea su contraseña por primera
 * vez en vez de restablecer una. Cualquier otro valor del parámetro cuenta como un restablecer normal. La pestaña y la página usan
 * el mismo texto.
 */
async function textos(searchParams: BusquedaRestablecer) {
  const invitacion = (await searchParams).invitacion === "1";
  return { invitacion, t: invitacion ? messages.auth.invitacion : messages.auth.restablecer };
}

export async function generateMetadata({ searchParams }: { searchParams: BusquedaRestablecer }): Promise<Metadata> {
  const { t } = await textos(searchParams);
  return { title: `${t.titulo} · ${messages.app.nombre}` };
}

export default async function RestablecerPage({ params, searchParams }: { params: Promise<{ token: string }>; searchParams: BusquedaRestablecer }) {
  const { token } = await params;
  const { invitacion, t } = await textos(searchParams);
  return (
    <AuthShell title={t.titulo} subtitle={t.subtitulo}>
      <RestablecerForm token={token} invitacion={invitacion} />
    </AuthShell>
  );
}
