"use client";

import { NumberField } from "@base-ui/react/number-field";
import { MinusIcon, PlusIcon } from "lucide-react";
import { cn } from "@/lib/utils";

const TAMANOS = {
  filtro: { grupo: "h-8", boton: "size-8" },
  campo: { grupo: "h-10", boton: "size-10" },
} as const;

/**
 * Cantidad numérica con +/− (ítems de una línea, plazos en días, cupos). El valor se escribe directo o se pisa
 * con las flechas; `min`/`max` deshabilitan el botón correspondiente al llegar al límite (los pinta base-ui solo).
 * `variante="campo"` (por defecto, h-10, dentro de un `Formulario`) o `"filtro"` (h-8, barra de filtros/fila de tabla).
 */
export function StepperNumerico({
  id,
  valor,
  onCambio,
  min,
  max,
  paso = 1,
  disabled,
  variante = "campo",
  className,
}: {
  id?: string;
  valor: number | null;
  onCambio: (v: number | null) => void;
  min?: number;
  max?: number;
  paso?: number;
  disabled?: boolean;
  variante?: "filtro" | "campo";
  className?: string;
}) {
  const t = TAMANOS[variante];
  return (
    <NumberField.Root id={id} value={valor} onValueChange={onCambio} min={min} max={max} step={paso} disabled={disabled}>
      <NumberField.Group className={cn("inline-flex w-fit items-stretch overflow-hidden rounded-lg border border-border bg-card shadow-2xs", t.grupo, className)}>
        <NumberField.Decrement
          aria-label="Disminuir"
          className={cn(
            "flex shrink-0 items-center justify-center text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:bg-muted disabled:pointer-events-none disabled:opacity-40",
            t.boton,
          )}
        >
          <MinusIcon className="size-3.5" />
        </NumberField.Decrement>
        <NumberField.Input
          className="w-14 border-x border-border bg-transparent text-center font-mono text-[13px] text-foreground tabular-nums outline-none disabled:cursor-not-allowed disabled:opacity-60"
        />
        <NumberField.Increment
          aria-label="Aumentar"
          className={cn(
            "flex shrink-0 items-center justify-center text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:bg-muted disabled:pointer-events-none disabled:opacity-40",
            t.boton,
          )}
        >
          <PlusIcon className="size-3.5" />
        </NumberField.Increment>
      </NumberField.Group>
    </NumberField.Root>
  );
}
