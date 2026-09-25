import { LogoMarca } from "@/components/nav/logo";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminPerfil } from "./admin-perfil";
import { AdminSidebarNav } from "./admin-sidebar-nav";

export function AdminSidebar({ administrador }: { administrador: Administrador }) {
  return (
    <div className="flex h-full w-full min-w-0 flex-col justify-between overflow-hidden select-none">
      <div className="flex w-full min-w-0 flex-col">
        <div className="flex h-14 w-full min-w-0 shrink-0 items-center justify-between gap-2 overflow-hidden border-b border-border/60 px-4">
          <LogoMarca href="/admin" />
          <span className="inline-flex shrink-0 items-center rounded border border-border/60 bg-secondary px-1.5 py-0.5 font-mono text-[10px] font-medium whitespace-nowrap text-secondary-foreground/70">
            ADMIN
          </span>
        </div>
        <AdminSidebarNav />
      </div>

      <div className="flex w-full min-w-0 flex-col gap-2.5 border-t border-border/60 p-3">
        <AdminPerfil administrador={administrador} />
      </div>
    </div>
  );
}
