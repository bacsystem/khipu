import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { AdminSidebar } from "@/components/admin/admin-sidebar";
import { meAdministrador } from "@/lib/api/admin-auth";
import { getAdminServerSession } from "@/lib/admin-session-server";

/**
 * Guardia real del backoffice (#175/#176/#179): sin refresh token, así que si el JWT (30 min) venció o
 * es inválido, `meAdministrador` falla y se redirige a /admin/login — no hay middleware de por medio.
 */
export default async function AdminPanelLayout({ children }: { children: ReactNode }) {
  const { access } = await getAdminServerSession();
  if (!access) redirect("/admin/login");

  const administrador = await meAdministrador(access).catch(() => null);
  if (!administrador) redirect("/admin/login");

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 overflow-x-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:sticky md:top-0 md:block md:h-screen md:overflow-y-auto">
        <AdminSidebar administrador={administrador} />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <main className="min-w-0 flex-1 overflow-x-auto p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
