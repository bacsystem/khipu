"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMessages } from "next-intl";
import { SEGMENTADO, SEGMENTO } from "@/lib/estilos";
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
    <nav aria-label={t.aria} className={cn(SEGMENTADO, "max-w-full flex-nowrap overflow-x-auto")}>
      {SECCIONES.map((s) => {
        const activa = s.href === "/developers" ? pathname === "/developers" : pathname.startsWith(s.href);
        return (
          <Link
            key={s.href}
            href={s.href}
            aria-current={activa ? "page" : undefined}
            className={SEGMENTO}
          >
            {t[s.label]}
          </Link>
        );
      })}
    </nav>
  );
}
