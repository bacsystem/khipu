import type { ReactNode } from "react";
import { Shell } from "khipu-design-system/nav/shell";
import { redirect } from "next/navigation";
import { AdminSidebar } from "@/components/admin/admin-sidebar";
import { AdminTopBar } from "@/components/admin/admin-top-bar";
import { meAdministrador } from "@/lib/api/admin-auth";
import { getAdminServerSession } from "@/lib/admin-session-server";

/**
 * Guardia real del backoffice (#175/#176/#179): sin refresh token, así que si el JWT (30 min) venció o
 * es inválido, `meAdministrador` falla y se redirige a /admin/login — no hay middleware de por medio.
 * El esqueleto es el `Shell` del design system, el mismo del portal de clientes (#512).
 */
export default async function AdminPanelLayout({ children }: { children: ReactNode }) {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const administrador = await meAdministrador(access).catch(() => null);
  if (!administrador) redirect("/admin/login");

  return (
    <Shell sidebar={<AdminSidebar administrador={administrador} />} topBar={<AdminTopBar administrador={administrador} />}>
      {children}
    </Shell>
  );
}
