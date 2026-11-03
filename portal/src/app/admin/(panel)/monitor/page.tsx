import type { Metadata } from "next";
import { getMessages } from "next-intl/server";
import { CabeceraPagina } from "khipu-design-system/patrones/cabecera-pagina";
import { redirect } from "next/navigation";
import { PanelDelMonitor } from "@/components/admin/panel-del-monitor";
import { getAdminServerSession } from "@/lib/admin-session-server";
import { obtenerMonitor, type MonitorDeEmision } from "@/lib/api/admin-monitor";
import { CONTENEDOR_PAGINA } from "@/lib/estilos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: messages.admin.metadatos.monitor };
}

/**
 * Monitor global de emisión (#195). Trae la primera lectura en el servidor para que la página no nazca vacía; de ahí en adelante el panel pide una nueva cada 30 segundos
 * por el BFF. Si la primera lectura falla la página igual se abre y el panel lo intenta de nuevo y muestra el error con su botón de reintentar. No captura el 401 por
 * separado: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un token vencido ya redirigió a /admin/login antes de llegar aquí.
 */
export default async function AdminMonitorPage() {
  const messages = await getMessages();
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const inicial: MonitorDeEmision | null = await obtenerMonitor(access).catch(() => null);

  return (
    <div className={CONTENEDOR_PAGINA}>
      <CabeceraPagina titulo={messages.admin.monitor.titulo} descripcion={messages.admin.monitor.descripcion} />
      <PanelDelMonitor inicial={inicial} />
    </div>
  );
}
