"use client";

import { Campo } from "khipu-design-system/formularios/campo";
import { errorDeRetencion, type RetencionForm } from "@/lib/comprobantes/retencion";
import { AYUDA_CAMPO, CAMPO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** Retención del IGV (#313): el porcentaje (3 % por ley) y lo que el backend calculó; el cliente paga el total menos esa retención. */
export function RetencionEditor({ valor, onCambio, moneda, montoCalculado }: { valor: RetencionForm; onCambio: (r: RetencionForm) => void; moneda: string; montoCalculado: number | null }) {
  const error = errorDeRetencion(valor);
  return (
    <div className="grid gap-3 rounded-xl border border-border px-3 py-3" data-testid="retencion-form">
      <Campo id="nc-ret-porcentaje" etiqueta="Porcentaje de retención" ayuda="La tasa legal es 3 %">
        <input
          id="nc-ret-porcentaje"
          value={valor.porcentaje}
          onChange={(e) => onCambio({ ...valor, porcentaje: e.target.value })}
          inputMode="decimal"
          className={cn(CAMPO, "font-mono text-[13px] tabular-nums sm:max-w-[160px]")}
        />
      </Campo>
      <p className={cn(AYUDA_CAMPO, error ? "text-destructive" : undefined)} role={error ? "alert" : "status"} data-testid="retencion-resumen">
        {error ?? (montoCalculado !== null ? `El cliente retiene ${formatearMonto(moneda, montoCalculado)} y la entera a SUNAT; no cambia los importes de la factura.` : "El monto lo calcula khipu al emitir.")}
      </p>
    </div>
  );
}
