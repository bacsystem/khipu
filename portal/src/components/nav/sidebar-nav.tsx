"use client";

import { EyeIcon, GaugeIcon, ListOrderedIcon, ReceiptTextIcon, ShieldCheckIcon, TerminalIcon, StoreIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

type Item = {
  href: string;
  label: string;
  icon: typeof ReceiptTextIcon;
};

type Grupo = {
  titulo: string;
  items: Item[];
};

/** Solo páginas que existen (C6): «Catálogo» se veía con «Pronto» y no llevaba a ningún lado. */
const GRUPOS: Grupo[] = [
  {
    titulo: "Emisión & SUNAT",
    items: [
      { href: "/comprobantes", label: "Comprobantes", icon: ReceiptTextIcon },
      { href: "/series", label: "Series correlativas", icon: ListOrderedIcon },
    ],
  },
  {
    titulo: "Configuración",
    items: [
      { href: "/empresa", label: "Fiscal & certificado", icon: ShieldCheckIcon },
      { href: "/establecimientos", label: "Establecimientos", icon: StoreIcon },
      { href: "/api-keys", label: "API keys", icon: TerminalIcon },
    ],
  },
  {
    // Lo de la cuenta y no de una empresa, como en la miga («Cuenta / …»).
    titulo: "Cuenta",
    items: [
      { href: "/cuenta/plan", label: "Plan y consumo", icon: GaugeIcon },
      { href: "/cuenta/accesos-de-soporte", label: "Accesos de soporte", icon: EyeIcon },
    ],
  },
];

const ITEM_BASE =
  "flex w-full min-w-0 items-center gap-2.5 overflow-hidden rounded-md border border-transparent px-2.5 py-1.5 text-[13px]";

export function SidebarNav() {
  const pathname = usePathname();

  return (
    <nav className="grid w-full grid-cols-1 gap-3 px-2 pt-2">
      {GRUPOS.map((grupo) => (
        <div key={grupo.titulo} className="grid w-full grid-cols-1 gap-0.5">
          <span className="truncate px-2.5 py-1 text-[10px] font-medium tracking-wider text-muted-foreground/80 uppercase">
            {grupo.titulo}
          </span>
          {grupo.items.map((item) => {
            const Icono = item.icon;
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  ITEM_BASE,
                  "transition-colors",
                  active
                    ? "border-primary/20 bg-accent/70 font-medium text-accent-foreground"
                    : "font-normal text-sidebar-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                <Icono className={cn("size-[18px] shrink-0", active ? "text-primary" : "text-muted-foreground/70")} />
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
                {active ? <span className="size-1.5 shrink-0 rounded-full bg-primary" /> : null}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
