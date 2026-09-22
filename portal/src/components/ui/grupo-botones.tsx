"use client";

import { Toggle } from "@base-ui/react/toggle";
import { ToggleGroup as ToggleGroupPrimitive } from "@base-ui/react/toggle-group";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type OpcionSegmentada<T extends string> = { valor: T; etiqueta: ReactNode; icon?: ReactNode; disabled?: boolean };

const TAMANOS = {
  sm: { grupo: "h-7 p-0.5", boton: "h-6 px-2 text-[11px]" },
  default: { grupo: "h-8 p-1", boton: "h-6 px-3 text-[12px]" },
} as const;

/**
 * Control segmentado de una sola opción activa (sobre `base-ui/toggle-group`, con navegación de flechas entre
 * opciones): la versión genérica y con teclado del patrón que ya se armaba a mano en `SelectorPorPagina`,
 * `ThemeToggle` y `Tabs estilo="segmentado"` — para cualquier enum corto (vista, unidad, modo).
 * Úsalo controlado (`valor` + `onCambio`): al ser el value del grupo, evita que se pueda "apagar" la única
 * opción activa haciendo clic dos veces (que es el comportamiento por defecto de un toggle suelto).
 */
export function GrupoBotones<T extends string>({
  opciones,
  valor,
  defaultValor,
  onCambio,
  /** Nombre accesible del grupo (base-ui ya le da `role="group"`, pero sin etiqueta un lector de pantalla no
   * sabe qué controla). Pásala salvo que un `<label>` visible ya describa el control (p. ej. dentro de `Campo`). */
  etiqueta,
  tamano = "default",
  className,
}: {
  opciones: OpcionSegmentada<T>[];
  valor?: T;
  defaultValor?: T;
  onCambio?: (v: T) => void;
  etiqueta?: string;
  tamano?: keyof typeof TAMANOS;
  className?: string;
}) {
  const t = TAMANOS[tamano];
  return (
    <ToggleGroupPrimitive
      aria-label={etiqueta}
      value={valor != null ? [valor] : undefined}
      defaultValue={defaultValor != null ? [defaultValor] : undefined}
      onValueChange={(v) => {
        if (v[0] != null) onCambio?.(v[0] as T);
      }}
      className={cn("inline-flex w-fit items-center gap-1 rounded-lg border border-border/60 bg-secondary/80", t.grupo, className)}
    >
      {opciones.map((o) => (
        <Toggle
          key={o.valor}
          value={o.valor}
          disabled={o.disabled}
          className={cn(
            "inline-flex items-center gap-1.5 rounded-md font-medium whitespace-nowrap text-muted-foreground transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring/40 hover:text-foreground data-pressed:bg-card data-pressed:text-foreground data-pressed:shadow-2xs disabled:pointer-events-none disabled:opacity-50",
            t.boton,
          )}
        >
          {o.icon}
          {o.etiqueta}
        </Toggle>
      ))}
    </ToggleGroupPrimitive>
  );
}
