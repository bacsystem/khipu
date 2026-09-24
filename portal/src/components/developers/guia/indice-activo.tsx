"use client";

import { useSeccionActiva } from "@/lib/seccion-activa";
import { cn } from "@/lib/utils";

/**
 * Índice lateral de una guía, que marca la sección que se está leyendo. Sin esto, en una guía de veinte secciones el
 * índice no dice dónde estás y hay que ubicarse a mano.
 */
export function IndiceActivo({ items }: { items: Array<{ id: string; label: string }> }) {
  const activo = useSeccionActiva(items.map((i) => i.id));

  return (
    <nav aria-label="Contenido" className="sticky top-16 hidden max-h-[calc(100vh-5rem)] overflow-y-auto lg:block">
      <span className="mb-2 block text-[11px] font-semibold tracking-wider text-muted-foreground/80 uppercase">En esta página</span>
      <ul className="space-y-1 border-l border-border/60">
        {items.map((i) => {
          const esActivo = i.id === activo;
          return (
            <li key={i.id}>
              <a
                href={`#${i.id}`}
                aria-current={esActivo ? "location" : undefined}
                className={cn(
                  "-ml-px block border-l py-1 pl-3 text-[12px] transition-colors",
                  esActivo
                    ? "border-primary font-medium text-foreground"
                    : "border-transparent text-muted-foreground hover:border-primary/40 hover:text-foreground",
                )}
              >
                {i.label}
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
