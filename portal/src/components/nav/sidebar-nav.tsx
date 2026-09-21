"use client";

import { ListOrderedIcon, PackageIcon, PlusCircleIcon, ReceiptTextIcon, ShieldCheckIcon, TerminalIcon, StoreIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

type Item = {
  href?: string;
  label: string;
  icon: typeof ReceiptTextIcon;
};

type Grupo = {
  titulo: string;
  items: Item[];
};

const GRUPOS: Grupo[] = [
  {
    titulo: "Emisión & SUNAT",
    items: [
      { href: "/comprobantes", label: "Comprobantes", icon: ReceiptTextIcon },
      { label: "Emitir comprobante", icon: PlusCircleIcon },
      { href: "/series", label: "Series correlativas", icon: ListOrderedIcon },
      { label: "Catálogo", icon: PackageIcon },
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
            if (!item.href) {
              return (
                <span
                  key={item.label}
                  title="Próximamente"
                  aria-disabled="true"
                  className={cn(ITEM_BASE, "cursor-not-allowed text-muted-foreground/50")}
                >
                  <Icono className="size-[18px] shrink-0 text-muted-foreground/40" />
                  <span className="min-w-0 flex-1 truncate">{item.label}</span>
                  <span className="shrink-0 rounded bg-secondary px-1 py-0.5 text-[9px] font-medium tracking-wide whitespace-nowrap uppercase">
                    Pronto
                  </span>
                </span>
              );
            }
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
