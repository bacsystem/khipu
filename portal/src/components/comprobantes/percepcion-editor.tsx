"use client";

import { Campo } from "@/components/formularios/campo";
import { errorDePercepcion, REGIMENES, type PercepcionForm } from "@/lib/comprobantes/percepcion";
import { AYUDA_CAMPO, CAMPO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/**
 * Percepción del IGV (#297): el régimen (catálogo 53) y, si no es el importe total, la base. La tasa la fija el régimen y el monto lo calcula el backend; se muestra en el
 * pie como «Percepción S/ X · Total a cobrar S/ Y».
 */
export function PercepcionEditor({
  valor,
  onCambio,
  moneda,
  alCredito,
  montoCalculado,
}: {
  valor: PercepcionForm;
  onCambio: (p: PercepcionForm) => void;
  moneda: string;
  alCredito: boolean;
  /** El monto de la percepción que calculó el backend, si ya respondió. */
  montoCalculado: number | null;
}) {
  const error = errorDePercepcion(valor, { moneda, alCredito });
  const set = (cambio: Partial<PercepcionForm>) => onCambio({ ...valor, ...cambio });
  return (
    <div className="grid gap-3 rounded-xl border border-border px-3 py-3" data-testid="percepcion-form">
      <div className="grid items-start gap-4 sm:grid-cols-[1fr_180px]">
        <Campo id="nc-per-regimen" etiqueta="Régimen de percepción" ayuda="Catálogo 53; la tasa la fija el régimen (catálogo 22)">
          <select id="nc-per-regimen" value={valor.regimen} onChange={(e) => set({ regimen: e.target.value })} className={CAMPO_DENSO}>
            {REGIMENES.map((r) => (
              <option key={r.codigo} value={r.codigo}>
                {r.codigo} · {r.etiqueta} ({r.tasa} %)
              </option>
            ))}
          </select>
        </Campo>
        <Campo id="nc-per-base" etiqueta="Base de la percepción" opcional ayuda="Por defecto, el importe total">
          <input id="nc-per-base" value={valor.base} onChange={(e) => set({ base: e.target.value })} inputMode="decimal" placeholder="0.00" className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
        </Campo>
      </div>
      <p className={cn(AYUDA_CAMPO, error ? "text-destructive" : undefined)} role={error ? "alert" : "status"} data-testid="percepcion-resumen">
        {error ?? (montoCalculado !== null ? `Se cobra ${formatearMonto("PEN", montoCalculado)} de percepción además del total.` : "El monto lo calcula khipu al emitir.")}
      </p>
    </div>
  );
}
