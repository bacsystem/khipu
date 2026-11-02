"use client";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "khipu-design-system/ui/select";
import { CONTROL_FILTRO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

/** Lo que representa «sin filtro» dentro del combo: base-ui no trata bien una opción de valor vacío, y el que llama sigue hablando con `""`. */
const SIN_FILTRO = "__sin_filtro__";

/**
 * La píldora de filtro de un listado (§12 del design system), la misma de comprobantes y guías: un `Select` sobre `CONTROL_FILTRO`, sin rótulo a la vista. `etiqueta` es su
 * nombre accesible (`aria-label`), así que la opción que se ve tiene que decir por sí sola qué filtra («Todos los entornos», no «Todos»). Una opción de valor `""` es «sin filtro».
 */
export function SelectDeFiltro({
  id,
  etiqueta,
  opciones,
  valor,
  onCambio,
  className,
}: {
  /** El del disparador (el botón del combo). */
  id?: string;
  etiqueta: string;
  /** En el orden en que se muestran: una lista, no un objeto. */
  opciones: { value: string; label: string }[];
  valor: string;
  onCambio: (valor: string) => void;
  className?: string;
}) {
  const items = opciones.map((o) => ({ value: o.value === "" ? SIN_FILTRO : o.value, label: o.label }));
  return (
    <Select items={items} value={valor === "" ? SIN_FILTRO : valor} onValueChange={(v) => onCambio(!v || v === SIN_FILTRO ? "" : String(v))}>
      <SelectTrigger id={id} className={cn(CONTROL_FILTRO, "w-auto min-w-40 pl-3", className)} aria-label={etiqueta}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {items.map(({ value, label }) => (
          <SelectItem key={value} value={value}>
            {label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
