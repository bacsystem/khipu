"use client";

import { HouseIcon } from "@phosphor-icons/react";
import { PESO_ICONO, PESO_ICONO_ACTIVO, type IconoComponente } from "khipu-design-system/lib/iconos";
import Link from "next/link";
import { useMessages } from "next-intl";
import { usePathname } from "next/navigation";
import { seccionesAdmin } from "@/lib/admin-secciones";
import { ROTULO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

// El ítem del `SidebarNav` del design system: píldora, y la activa con el fondo del acento (`bg-sidebar-primary`) y su ícono relleno.
const ITEM_BASE = "relative isolate flex w-full min-w-0 items-center gap-2.5 rounded-full px-3 py-[7px] text-control";

function ItemDelMenu({ href, etiqueta, icono: Icono, activo }: { href: string; etiqueta: string; icono: IconoComponente; activo: boolean }) {
  return (
    <Link
      href={href}
      aria-current={activo ? "page" : undefined}
      className={cn(
        ITEM_BASE,
        "transition-colors duration-(--duracion-rapida) ease-salida",
        activo ? "font-semibold text-sidebar-primary-foreground" : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
      )}
    >
      {activo ? <span aria-hidden className="absolute inset-0 -z-10 rounded-full border border-acento-borde bg-sidebar-primary" /> : null}
      <Icono weight={activo ? PESO_ICONO_ACTIVO : PESO_ICONO} className={cn("size-[18px] shrink-0", activo ? "text-current" : "text-muted-foreground")} />
      <span className="min-w-0 flex-1 truncate">{etiqueta}</span>
    </Link>
  );
}

/**
 * Menú del backoffice agrupado por sección (Clientes · Comercial · Operación · Plataforma), como el del portal de clientes y como la miga de la cabecera:
 * todos salen de `seccionesAdmin`. El detalle de una cuenta o empresa deja marcada su lista. Es propio (y no el `SidebarNav` del design system) porque «Inicio» es
 * `/admin`, que el del kit marcaría activo en todas las páginas por comparar por prefijo, y porque el menú lleva nombre accesible.
 */
export function AdminSidebarNav() {
  const messages = useMessages();
  const pathname = usePathname();
  const activo = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  return (
    <nav aria-label={messages.admin.nav.menu} className="grid w-full grid-cols-1 gap-4 px-2.5 pt-2">
      <ItemDelMenu href="/admin" etiqueta={messages.admin.nav.inicio} icono={HouseIcon} activo={pathname === "/admin"} />
      {seccionesAdmin(messages).map((seccion) => (
        <div key={seccion.titulo} role="group" aria-label={seccion.titulo} className="grid w-full grid-cols-1 gap-0.5">
          <span className={cn("truncate px-3 pt-1 pb-1.5", ROTULO)}>{seccion.titulo}</span>
          {seccion.items.map((item) => (
            <ItemDelMenu key={item.href} href={item.href} etiqueta={item.etiqueta} icono={item.icono} activo={activo(item.href)} />
          ))}
        </div>
      ))}
    </nav>
  );
}
