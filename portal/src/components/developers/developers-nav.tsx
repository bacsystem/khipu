"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

export const SECCIONES = [
  { href: "/developers", label: "Referencia API" },
  { href: "/developers/guia", label: "Guía de emisión" },
  { href: "/developers/catalogos", label: "Catálogos SUNAT" },
  { href: "/developers/errores", label: "Errores y estados" },
] as const;

export function DevelopersNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Secciones del developer portal" className="flex items-center gap-1 overflow-x-auto">
      {SECCIONES.map((s) => {
        const activa = s.href === "/developers" ? pathname === "/developers" : pathname.startsWith(s.href);
        return (
          <Link
            key={s.href}
            href={s.href}
            aria-current={activa ? "page" : undefined}
            className={cn(
              "rounded-md px-2.5 py-1 text-[12px] font-medium whitespace-nowrap transition-colors",
              activa ? "bg-accent/70 text-accent-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {s.label}
          </Link>
        );
      })}
    </nav>
  );
}
