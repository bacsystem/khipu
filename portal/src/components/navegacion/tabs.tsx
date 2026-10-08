"use client";

import { Tabs as TabsPrimitive } from "@base-ui/react/tabs";
import type { ReactNode } from "react";
import { SEGMENTADO, SEGMENTO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

export type TabItem<T extends string> = { id: T; etiqueta: ReactNode; contador?: number; disabled?: boolean };

/**
 * Pestañas de página. `estilo="segmentado"` es el control en caja de las barras de filtro; `"linea"` es la pestaña subrayada para secciones de detalle.
 * Los paneles se pasan como `render(id)` (`children`) o, desde un Server Component —que no puede pasarle una función a un componente de cliente—, ya
 * armados en `paneles`, uno por id. En los dos casos solo se monta el panel activo.
 */
export function Tabs<T extends string>({
  items,
  valor,
  defaultValor,
  onCambio,
  estilo = "linea",
  children,
  paneles,
  className,
}: {
  items: TabItem<T>[];
  valor?: T;
  defaultValor?: T;
  onCambio?: (v: T) => void;
  estilo?: "linea" | "segmentado";
  children?: (id: T) => ReactNode;
  paneles?: Partial<Record<T, ReactNode>>;
  className?: string;
}) {
  return (
    <TabsPrimitive.Root value={valor} defaultValue={defaultValor ?? items[0]?.id} onValueChange={(v) => onCambio?.(v as T)} className={className}>
      <TabsPrimitive.List
        className={cn("relative", estilo === "linea" ? "flex items-center gap-4 border-b border-border/80" : SEGMENTADO)}
      >
        {items.map((t) => (
          <TabsPrimitive.Tab
            key={t.id}
            value={t.id}
            disabled={t.disabled}
            className={
              // h-10/h-6 fijos (no padding vertical) para que el contador no altere la altura de la fila.
              estilo === "linea"
                ? "-mb-px inline-flex h-10 items-center gap-1.5 border-b-2 border-transparent px-1 text-[12px] font-medium whitespace-nowrap text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/40 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 data-active:border-primary data-active:text-foreground"
                : SEGMENTO
            }
          >
            {t.etiqueta}
            {t.contador != null ? <span className="rounded bg-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">{t.contador}</span> : null}
          </TabsPrimitive.Tab>
        ))}
      </TabsPrimitive.List>
      {items.map((t) => (
        <TabsPrimitive.Panel key={t.id} value={t.id} className="pt-4 outline-none">
          {children ? children(t.id) : paneles?.[t.id]}
        </TabsPrimitive.Panel>
      ))}
    </TabsPrimitive.Root>
  );
}
