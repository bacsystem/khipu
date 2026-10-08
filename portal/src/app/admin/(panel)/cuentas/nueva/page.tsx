import { redirect } from "next/navigation";
import { getAdminServerSession } from "@/lib/admin-session-server";

/**
 * El alta asistida (#188) es ahora un modal de tres pasos que se abre desde la cabecera de «Cuentas». Esta ruta queda para que un enlace
 * o marcador viejo no dé 404: lleva a la lista, donde está el botón. Sin sesión de administrador, al login como el resto del panel.
 */
export default async function AdminNuevaCuentaPage() {
  const { access } = await getAdminServerSession();
  redirect(access ? "/admin/cuentas" : "/admin/login");
}
