"use client";

import { OPCIONES_POR_PAGINA } from "@/lib/paginacion";
import { cn } from "@/lib/utils";

export function SelectorPorPagina({ valor, onCambio }: { valor: number; onCambio: (n: number) => void }) {
  return (
    <div className="inline-flex items-center gap-2 text-[11px] text-muted-foreground" role="group" aria-label="Filas por página">
      <span>Filas</span>
      <div className="inline-flex h-9 items-center rounded-lg border border-border bg-card p-0.5 shadow-2xs">
        {OPCIONES_POR_PAGINA.map((n) => (
          <button
            key={n}
            type="button"
            aria-pressed={valor === n}
            onClick={() => onCambio(n)}
            className={cn(
              "flex h-7 min-w-8 items-center justify-center rounded-md px-1.5 font-mono text-[11px] font-medium transition-colors",
              valor === n ? "bg-foreground text-background shadow-2xs" : "text-foreground/70 hover:bg-secondary hover:text-foreground",
            )}
          >
            {n}
          </button>
        ))}
      </div>
    </div>
  );
}
