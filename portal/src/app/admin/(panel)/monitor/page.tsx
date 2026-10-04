import { redirect } from "next/navigation";
import { PanelDelMonitor } from "@/components/admin/panel-del-monitor";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { obtenerMonitor, type MonitorDeEmision } from "@/lib/api/admin-monitor";
import { messages } from "@/lib/messages";

export const metadata = { title: "Monitor · Backoffice" };

/**
 * Monitor global de emisión (#195). Trae la primera lectura en el servidor para que la página no nazca vacía; de ahí en adelante el panel pide una nueva cada 30 segundos
 * por el BFF. Si la primera lectura falla la página igual se abre y el panel lo intenta de nuevo y muestra el error con su botón de reintentar. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminMonitorPage() {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const inicial: MonitorDeEmision | null = await obtenerMonitor(access).catch(() => null);

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-4">
      <div className="grid gap-1">
        <h1 className="font-heading text-2xl">{messages.admin.monitor.titulo}</h1>
        <p className="max-w-3xl text-sm text-muted-foreground">{messages.admin.monitor.descripcion}</p>
      </div>
      <PanelDelMonitor inicial={inicial} />
    </div>
  );
}
