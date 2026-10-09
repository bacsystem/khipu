import { redirect } from "next/navigation";
import { AccesosDeSoporte } from "@/components/soporte/accesos-de-soporte";
import { listarAccesosDeSoporte } from "@/lib/api/accesos-de-soporte";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

export const metadata = { title: `Accesos de soporte · ${messages.app.nombre}` };

const t = messages.soporte.accesos;

/** El historial de accesos de soporte a la cuenta (#184): el cliente ve cuándo el equipo de khipu miró su portal, sin saber qué administrador fue. */
export default async function AccesosDeSoportePage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");

  const accesos = await listarAccesosDeSoporte(access);

  return (
    // Como las demás páginas del panel (C9): el título va en la miga de la barra superior, que es el único h1, y el ancho es el mismo.
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <p className="text-sm text-muted-foreground">{t.descripcion}</p>
      <AccesosDeSoporte accesos={accesos} />
    </div>
  );
}
