import { redirect } from "next/navigation";
import { AccesosDeSoporte } from "@/components/soporte/accesos-de-soporte";
import { listarAccesosDeSoporte } from "@/lib/api/accesos-de-soporte";
import { messages } from "@/lib/messages";
import { getServerSession } from "@/lib/session-server";

const t = messages.soporte.accesos;

/** El historial de accesos de soporte a la cuenta (#184): el cliente ve cuándo el equipo de khipu miró su portal, sin saber qué administrador fue. */
export default async function AccesosDeSoportePage() {
  const { access } = await getServerSession();
  if (!access) redirect("/login");

  const accesos = await listarAccesosDeSoporte(access);

  return (
    <div className="mx-auto grid w-full max-w-[1100px] min-w-0 grid-cols-1 gap-4">
      <div>
        <h1 className="font-heading text-xl font-semibold tracking-tight">{t.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t.descripcion}</p>
      </div>
      <AccesosDeSoporte accesos={accesos} />
    </div>
  );
}
