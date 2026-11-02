"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMessages } from "next-intl";
import { cn } from "@/lib/utils";

/** Las secciones y la clave de su nombre en `messages.developers.nav`. */
export const SECCIONES = [
  { href: "/developers", label: "referencia" },
  { href: "/developers/guia", label: "guia" },
  { href: "/developers/catalogos", label: "catalogos" },
  { href: "/developers/errores", label: "errores" },
  { href: "/developers/entornos", label: "entornos" },
] as const;

export function DevelopersNav() {
  const pathname = usePathname();
  const t = useMessages().developers.nav;
  return (
    <nav aria-label={t.aria} className="flex items-center gap-1 overflow-x-auto">
      {SECCIONES.map((s) => {
        const activa = s.href === "/developers" ? pathname === "/developers" : pathname.startsWith(s.href);
        return (
          <Link
            key={s.href}
            href={s.href}
            aria-current={activa ? "page" : undefined}
            className={cn(
              "rounded-md px-2.5 py-1 text-control font-medium whitespace-nowrap transition-colors",
              activa ? "bg-accent/70 text-accent-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {t[s.label]}
          </Link>
        );
      })}
    </nav>
  );
}
