"use client";

import { HouseIcon } from "@phosphor-icons/react";
import type { GrupoNav } from "khipu-design-system/nav/sidebar-nav";
import { ChipSidebar, SidebarContent } from "khipu-design-system/nav/sidebar";
import { useMessages } from "next-intl";
import { LogoMarca } from "@/components/nav/logo";
import type { Mensajes } from "@/i18n/mensajes";
import type { Administrador } from "@/lib/api/admin-auth";
import { seccionesAdmin } from "@/lib/admin-secciones";
import { AdminPerfil } from "./admin-perfil";

/**
 * Los grupos del menú del backoffice (Panel · Clientes · Comercial · Operación · Plataforma), los mismos de la miga de la cabecera: salen de `seccionesAdmin`.
 * «Inicio» es `/admin`, del que cuelgan todas las demás, así que es `exacto`: por prefijo quedaría marcado en todas las páginas. El detalle de una cuenta o
 * empresa deja marcada su lista (por prefijo).
 */
export function gruposAdmin(messages: Mensajes): GrupoNav[] {
  const t = messages.admin.nav;
  return [
    { titulo: t.panel, items: [{ href: "/admin", label: t.inicio, icon: HouseIcon, exacto: true }] },
    ...seccionesAdmin(messages).map((s) => ({ titulo: s.titulo, items: s.items.map((i) => ({ href: i.href, label: i.etiqueta, icon: i.icono })) })),
  ];
}

/**
 * El menú del backoffice (la tarjeta lateral en escritorio, el cajón en el teléfono): el `SidebarContent` del design system con la marca, la insignia ADMIN, los
 * grupos y el menú del administrador. Es de cliente porque los íconos de los grupos son funciones y no cruzan desde el layout del servidor.
 */
export function AdminSidebar({ administrador }: { administrador: Administrador }) {
  const messages = useMessages();
  return (
    <SidebarContent
      marca={<LogoMarca href="/admin" />}
      chip={<ChipSidebar>{messages.admin.nav.insignia}</ChipSidebar>}
      grupos={gruposAdmin(messages)}
      etiquetaMenu={messages.admin.nav.menu}
      usuario={<AdminPerfil administrador={administrador} />}
    />
  );
}
