import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getMessages } from "next-intl/server";
import { EquipoView } from "@/components/equipo/equipo-view";
import { esRolInsuficiente, SinPermisoDePagina } from "@/components/sin-permiso";
import { listarInvitaciones, listarMiembros } from "@/lib/api/equipo";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.nav.items.equipo} · ${messages.app.nombre}` };
}

/** #21: quiénes entran a la cuenta y con qué rol, y las invitaciones pendientes. Solo administradores: el backend responde 403 a los demás. */
export default async function EquipoPage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");

  const cargado = await Promise.all([listarMiembros(access), listarInvitaciones(access)]).then(
    ([miembros, invitaciones]) => ({ miembros, invitaciones }),
    (error: unknown) => {
      if (esRolInsuficiente(error)) return null;
      throw error;
    },
  );
  if (cargado === null) return <SinPermisoDePagina />;
  const messages = await getMessages();

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <p className="text-sm text-muted-foreground">{messages.equipo.descripcion}</p>
      <EquipoView miembros={cargado.miembros} invitaciones={cargado.invitaciones} />
    </div>
  );
}
