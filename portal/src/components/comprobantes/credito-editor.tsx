"use client";

import { PlusIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { Campo } from "@/components/formularios/campo";
import { EntradaFecha } from "@/components/formularios/entrada-fecha";
import { type CreditoForm, repartirEnCuotas, sumaDeCuotas } from "@/lib/comprobantes/credito";
import { AYUDA_CAMPO, BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { formatearMonto, sumarDias } from "@/lib/formato";
import { cn } from "@/lib/utils";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/**
 * Factura al crédito (#309): el neto pendiente y el calendario de cuotas. «Repartir» arma N cuotas iguales cada 30 días desde la emisión; se pueden editar o agregar a
 * mano. Aquí solo se muestra la cuenta (cuánto suman contra el neto); el motivo por el que no vale lo calcula `errorDeCredito` y lo muestra el formulario.
 */
export function CreditoEditor({
  credito,
  onCambio,
  neto,
  moneda,
  emision,
  error,
}: {
  credito: CreditoForm;
  onCambio: (c: CreditoForm) => void;
  /** El total del comprobante: el neto pendiente por defecto. */
  neto: number;
  moneda: string;
  emision: string;
  error: string | null;
}) {
  const [n, setN] = useState(2);
  const pendiente = credito.montoPendiente.trim() === "" ? neto : Number(credito.montoPendiente);
  const suma = sumaDeCuotas(credito.cuotas);
  const poner = (i: number, cambio: Partial<CreditoForm["cuotas"][number]>) => onCambio({ ...credito, cuotas: credito.cuotas.map((q, j) => (j === i ? { ...q, ...cambio } : q)) });
  return (
    <div className="grid gap-3 rounded-xl border border-border px-3 py-3" data-testid="credito">
      <div className="grid items-start gap-3 sm:grid-cols-[200px_1fr]">
        <Campo id="nc-neto-pendiente" etiqueta="Neto pendiente" ayuda={`Por defecto el total (${formatearMonto(moneda, neto)}); con detracción o retención, lo que queda por cobrar`}>
          <input
            id="nc-neto-pendiente"
            value={credito.montoPendiente}
            onChange={(e) => onCambio({ ...credito, montoPendiente: e.target.value })}
            inputMode="decimal"
            placeholder={neto.toFixed(2)}
            className={cn(CAMPO_DENSO, "font-mono tabular-nums")}
          />
        </Campo>
        <div className="flex flex-wrap items-end gap-2">
          <Campo id="nc-cuotas-n" etiqueta="Repartir en">
            <select id="nc-cuotas-n" value={n} onChange={(e) => setN(Number(e.target.value))} className={CAMPO_DENSO}>
              {Array.from({ length: 11 }, (_, i) => i + 2).map((v) => (
                <option key={v} value={v}>{v} cuotas iguales</option>
              ))}
            </select>
          </Campo>
          <button type="button" className={BOTON_SECUNDARIO} onClick={() => onCambio({ ...credito, cuotas: repartirEnCuotas(pendiente, n, emision) })} disabled={!(pendiente > 0)}>
            Repartir
          </button>
        </div>
      </div>

      <div className="grid gap-2">
        {credito.cuotas.map((q, i) => (
          <div key={i} className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
            <Campo id={`nc-cuota-monto-${i}`} etiqueta={`Cuota ${i + 1}: monto`} className="[&>label]:sr-only">
              <input id={`nc-cuota-monto-${i}`} value={q.monto} onChange={(e) => poner(i, { monto: e.target.value })} inputMode="decimal" placeholder="0.00" className={cn(CAMPO_DENSO, "font-mono tabular-nums")} />
            </Campo>
            <Campo id={`nc-cuota-fecha-${i}`} etiqueta={`Cuota ${i + 1}: vencimiento`} className="[&>label]:sr-only">
              <EntradaFecha id={`nc-cuota-fecha-${i}`} valor={q.vencimiento} onCambio={(v) => poner(i, { vencimiento: v })} min={sumarDias(emision, 1)} variante="filtro" />
            </Campo>
            <button
              type="button"
              aria-label={`Quitar la cuota ${i + 1}`}
              onClick={() => onCambio({ ...credito, cuotas: credito.cuotas.filter((_, j) => j !== i) })}
              className="mb-1 rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-destructive"
            >
              <Trash2Icon className="size-4" />
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() => onCambio({ ...credito, cuotas: [...credito.cuotas, { monto: "", vencimiento: sumarDias(credito.cuotas.at(-1)?.vencimiento ?? emision, 30) }] })}
          className="inline-flex items-center gap-1 justify-self-start text-[12px] font-medium text-primary hover:underline"
        >
          <PlusIcon className="size-3.5" />
          Agregar cuota
        </button>
      </div>

      <p className={cn(AYUDA_CAMPO, error ? "text-destructive" : undefined)} role={error ? "alert" : "status"} data-testid="credito-resumen">
        {error ?? `Las ${credito.cuotas.length} cuotas suman ${formatearMonto(moneda, suma)} de ${formatearMonto(moneda, pendiente)} pendientes.`}
      </p>
    </div>
  );
}
