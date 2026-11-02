"use client";

import { MobileNav } from "khipu-design-system/nav/mobile-nav";
import { useMessages } from "next-intl";
import type { Administrador } from "@/lib/api/admin-auth";
import { AdminSidebar } from "./admin-sidebar";

/**
 * El sidebar del backoffice solo se ve desde `md`; en móvil esta es la única navegación. Es el cajón del design system: se cierra al cambiar de página y se
 * desplaza, así que en una pantalla baja el menú del administrador queda al alcance.
 */
export function AdminMobileNav({ administrador }: { administrador: Administrador }) {
  const messages = useMessages();
  return (
    <MobileNav etiqueta={messages.admin.topbar.abrirMenu}>
      <AdminSidebar administrador={administrador} />
    </MobileNav>
  );
}
