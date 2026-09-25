"use client";

import { ActivityIcon, BuildingIcon, CreditCardIcon, HomeIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { messages } from "@/lib/messages";
import { cn } from "@/lib/utils";

type Item = { href?: string; label: string; icon: typeof HomeIcon };

/** Solo "Inicio" tiene página propia: el resto del backoffice llega en los issues de la épica #11. */
const ITEMS: Item[] = [
  { href: "/admin", label: messages.admin.nav.inicio, icon: HomeIcon },
  { label: "Clientes", icon: BuildingIcon },
  { label: "Planes", icon: CreditCardIcon },
  { label: "Operación", icon: ActivityIcon },
];

const ITEM_BASE =
  "flex w-full min-w-0 items-center gap-2.5 overflow-hidden rounded-md border border-transparent px-2.5 py-1.5 text-[13px]";

export function AdminSidebarNav() {
  const pathname = usePathname();

  return (
    <nav className="grid w-full grid-cols-1 gap-0.5 px-2 pt-2">
      {ITEMS.map((item) => {
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
        const active = pathname === item.href;
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
    </nav>
  );
}
