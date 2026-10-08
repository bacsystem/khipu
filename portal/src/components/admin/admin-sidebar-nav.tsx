"use client";

import { HomeIcon, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { SECCIONES_ADMIN } from "@/lib/admin-secciones";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

const ITEM_BASE =
  "flex w-full min-w-0 items-center gap-2.5 overflow-hidden rounded-md border border-transparent px-2.5 py-1.5 text-[13px]";

function ItemDelMenu({ href, etiqueta, icono: Icono, activo }: { href: string; etiqueta: string; icono: LucideIcon; activo: boolean }) {
  return (
    <Link
      href={href}
      aria-current={activo ? "page" : undefined}
      className={cn(
        ITEM_BASE,
        "transition-colors",
        activo
          ? "border-primary/20 bg-accent/70 font-medium text-accent-foreground"
          : "font-normal text-sidebar-foreground hover:bg-muted hover:text-foreground",
      )}
    >
      <Icono className={cn("size-[18px] shrink-0", activo ? "text-primary" : "text-muted-foreground/70")} />
      <span className="min-w-0 flex-1 truncate">{etiqueta}</span>
      {activo ? <span className="size-1.5 shrink-0 rounded-full bg-primary" /> : null}
    </Link>
  );
}

/**
 * Menú del backoffice agrupado por sección (Clientes · Comercial · Operación · Plataforma), como el del portal de clientes y como la miga de la cabecera:
 * todos salen de `SECCIONES_ADMIN`. El detalle de una cuenta o empresa deja marcada su lista.
 */
export function AdminSidebarNav() {
  const pathname = usePathname();
  const activo = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  return (
    <nav aria-label={messages.admin.nav.menu} className="grid w-full grid-cols-1 gap-3 px-2 pt-2">
      <ItemDelMenu href="/admin" etiqueta={messages.admin.nav.inicio} icono={HomeIcon} activo={pathname === "/admin"} />
      {SECCIONES_ADMIN.map((seccion) => (
        <div key={seccion.titulo} role="group" aria-label={seccion.titulo} className="grid w-full grid-cols-1 gap-0.5">
          <span className="truncate px-2.5 py-1 text-[10px] font-medium tracking-wider text-muted-foreground/80 uppercase">{seccion.titulo}</span>
          {seccion.items.map((item) => (
            <ItemDelMenu key={item.href} href={item.href} etiqueta={item.etiqueta} icono={item.icono} activo={activo(item.href)} />
          ))}
        </div>
      ))}
    </nav>
  );
}
