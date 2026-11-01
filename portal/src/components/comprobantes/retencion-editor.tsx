"use client";

import { useLocale, useMessages } from "next-intl";
import { Campo } from "khipu-design-system/formularios/campo";
import { interpolar } from "@/i18n/mensajes";
import { errorDeRetencion, type RetencionForm } from "@/lib/comprobantes/retencion";
import { AYUDA_CAMPO, CAMPO } from "@/lib/estilos";
import { formatearMonto } from "@/lib/formato";
import { cn } from "@/lib/utils";

/** Retención del IGV (#313): el porcentaje (3 % por ley) y lo que el backend calculó; el cliente paga el total menos esa retención. */
export function RetencionEditor({ valor, onCambio, moneda, montoCalculado }: { valor: RetencionForm; onCambio: (r: RetencionForm) => void; moneda: string; montoCalculado: number | null }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.comprobantes.editores;
  const error = errorDeRetencion(valor, messages);
  return (
    <div className="grid gap-3 rounded-xl border border-border px-3 py-3" data-testid="retencion-form">
      <Campo id="nc-ret-porcentaje" etiqueta={t.retencion.porcentaje} ayuda={t.retencion.ayuda}>
        <input
          id="nc-ret-porcentaje"
          value={valor.porcentaje}
          onChange={(e) => onCambio({ ...valor, porcentaje: e.target.value })}
          inputMode="decimal"
          className={cn(CAMPO, "font-mono text-control tabular-nums sm:max-w-[160px]")}
        />
      </Campo>
      <p className={cn(AYUDA_CAMPO, error ? "text-destructive" : undefined)} role={error ? "alert" : "status"} data-testid="retencion-resumen">
        {error ?? (montoCalculado !== null ? interpolar(t.retencion.resumen, { monto: formatearMonto(moneda, montoCalculado, locale) }) : t.montoAlEmitir)}
      </p>
    </div>
  );
}
