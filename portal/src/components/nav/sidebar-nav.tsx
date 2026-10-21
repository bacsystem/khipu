"use client";

import { EyeIcon, GaugeIcon, LayoutDashboardIcon, ListOrderedIcon, ReceiptTextIcon, ShieldCheckIcon, TerminalIcon, StoreIcon, UsersIcon, PackageIcon, UserCogIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMessages } from "next-intl";
import type { Mensajes } from "@/i18n/mensajes";
import { cn } from "@/lib/utils";

type Item = {
  href: string;
  /** Clave en `nav.items` de los mensajes. */
  label: keyof Mensajes["nav"]["items"];
  icon: typeof ReceiptTextIcon;
  /** #21: solo para administradores. Los demás roles reciben 403 del backend al leerla, así que ni se muestra. */
  soloAdmin?: boolean;
};

type Grupo = {
  /** Clave en `nav.grupos` de los mensajes. */
  titulo: keyof Mensajes["nav"]["grupos"];
  items: Item[];
};

/** Solo páginas que existen (C6): «Catálogo» se veía con «Pronto» y no llevaba a ningún lado. */
const GRUPOS: Grupo[] = [
  {
    // #24: el panel de inicio, adonde se llega al entrar.
    titulo: "panel",
    items: [{ href: "/inicio", label: "inicio", icon: LayoutDashboardIcon }],
  },
  {
    titulo: "emision",
    items: [
      { href: "/comprobantes", label: "comprobantes", icon: ReceiptTextIcon },
      { href: "/series", label: "series", icon: ListOrderedIcon },
      { href: "/clientes", label: "clientes", icon: UsersIcon },
      { href: "/productos", label: "productos", icon: PackageIcon },
    ],
  },
  {
    titulo: "configuracion",
    items: [
      { href: "/empresa", label: "empresa", icon: ShieldCheckIcon },
      { href: "/establecimientos", label: "establecimientos", icon: StoreIcon },
      { href: "/api-keys", label: "apiKeys", icon: TerminalIcon, soloAdmin: true },
    ],
  },
  {
    // Lo de la cuenta y no de una empresa, como en la miga («Cuenta / …»).
    titulo: "cuenta",
    items: [
      { href: "/cuenta/plan", label: "plan", icon: GaugeIcon },
      { href: "/cuenta/equipo", label: "equipo", icon: UserCogIcon, soloAdmin: true },
      { href: "/cuenta/accesos-de-soporte", label: "soporte", icon: EyeIcon },
    ],
  },
];

const ITEM_BASE =
  "flex w-full min-w-0 items-center gap-2.5 overflow-hidden rounded-md border border-transparent px-2.5 py-1.5 text-[13px]";

/** `rol`: el del usuario de la sesión (#21); sin él (pruebas) se muestra todo. */
export function SidebarNav({ rol = "ADMIN" }: { rol?: string }) {
  const pathname = usePathname();
  const t = useMessages().nav;

  return (
    <nav aria-label={t.principal} className="grid w-full grid-cols-1 gap-3 px-2 pt-2">
      {GRUPOS.map((grupo) => (
        <div key={grupo.titulo} className="grid w-full grid-cols-1 gap-0.5">
          <span className="truncate px-2.5 py-1 text-[10px] font-medium tracking-wider text-muted-foreground/80 uppercase">
            {t.grupos[grupo.titulo]}
          </span>
          {grupo.items.filter((item) => !item.soloAdmin || rol === "ADMIN").map((item) => {
            const Icono = item.icon;
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  ITEM_BASE,
                  "transition-colors",
                  active
                    ? "border-primary/20 bg-accent/70 font-medium text-accent-foreground"
                    : "font-normal text-sidebar-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                <Icono className={cn("size-[18px] shrink-0", active ? "text-primary" : "text-muted-foreground/70")} />
                <span className="min-w-0 flex-1 truncate">{t.items[item.label]}</span>
                {active ? <span className="size-1.5 shrink-0 rounded-full bg-primary" /> : null}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
