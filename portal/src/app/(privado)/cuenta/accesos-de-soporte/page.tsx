import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getMessages } from "next-intl/server";
import { AccesosDeSoporte } from "@/components/soporte/accesos-de-soporte";
import { listarAccesosDeSoporte } from "@/lib/api/accesos-de-soporte";
import { getServerSession } from "@/lib/session-server";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.soporte.accesos.titulo} · ${messages.app.nombre}` };
}

/** El historial de accesos de soporte a la cuenta (#184): el cliente ve cuándo el equipo de khipu miró su portal, sin saber qué administrador fue. */
export default async function AccesosDeSoportePage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");

  const accesos = await listarAccesosDeSoporte(access);
  const t = (await getMessages()).soporte.accesos;

  return (
    // Como las demás páginas del panel (C9): el título va en la miga de la barra superior, que es el único h1, y el ancho es el mismo.
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <p className="text-sm text-muted-foreground">{t.descripcion}</p>
      <AccesosDeSoporte accesos={accesos} />
    </div>
  );
}
