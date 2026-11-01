import { ChipSidebar } from "khipu-design-system/nav/sidebar";
import { useMessages } from "next-intl";
import { LogoMarca } from "@/components/nav/logo";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminPerfil } from "./admin-perfil";
import { AdminSidebarNav } from "./admin-sidebar-nav";

/**
 * El contenido del menú del backoffice, con el reparto del `SidebarContent` del design system (marca y chip arriba, el menú, y el usuario abajo). No usa el del kit porque su
 * menú no tiene nombre accesible ni distingue «Inicio» (`/admin`) de las páginas que cuelgan de él: ver `AdminSidebarNav`.
 */
export function AdminSidebar({ administrador }: { administrador: Administrador }) {
  const messages = useMessages();
  return (
    <div className="flex h-full w-full min-w-0 flex-col justify-between overflow-hidden select-none">
      <div className="flex w-full min-w-0 flex-col">
        <div className="flex h-14 w-full min-w-0 shrink-0 items-center justify-between gap-2 overflow-hidden px-4">
          <LogoMarca href="/admin" />
          <ChipSidebar>{messages.admin.nav.insignia}</ChipSidebar>
        </div>
        <AdminSidebarNav />
      </div>

      <div className="flex w-full min-w-0 flex-col gap-2.5 p-3">
        <AdminPerfil administrador={administrador} />
      </div>
    </div>
  );
}
