"use client";

import { useMessages } from "next-intl";
import { Campo } from "khipu-design-system/formularios/campo";
import { interpolar } from "@/i18n/mensajes";
import { errorDeAjuste, type AjusteForm } from "@/lib/comprobantes/ajustes";
import { CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";


/**
 * Descuento o cargo (#310): modo (% o monto), valor y si afecta la base del IGV. Sirve de línea o global: en un cargo global ofrece además el recargo al consumo
 * (código 46), que nunca lleva IGV. Vacío es «sin ajuste». El importe que resulta lo calcula el backend; aquí solo se valida el formato, como el dominio.
 */
export function AjusteEditor({
  id,
  etiqueta,
  clase,
  valor,
  onCambio,
  ofreceRecargoConsumo = false,
  sinOpcionDeBase = false,
}: {
  /** Prefijo único de los controles (cada ítem tiene los suyos). */
  id: string;
  etiqueta: string;
  clase: "descuento" | "cargo";
  valor: AjusteForm;
  onCambio: (a: AjusteForm) => void;
  ofreceRecargoConsumo?: boolean;
  /** Oculta «reduce la base / paga IGV»: en una nota el ajuste siempre afecta la base (SUNAT no admite los otros, 3271/3277). */
  sinOpcionDeBase?: boolean;
}) {
  const messages = useMessages();
  const t = messages.comprobantes.editores.ajuste;
  const error = errorDeAjuste(valor, clase, messages);
  const set = (cambio: Partial<AjusteForm>) => onCambio({ ...valor, ...cambio });
  const textoAfecta = clase === "descuento" ? t.reduceBase : t.pagaIgv;
  return (
    <fieldset className="grid content-start gap-1.5">
      <legend className="mb-1 text-control font-medium text-foreground">{etiqueta}</legend>
      <div className="grid grid-cols-[110px_1fr] gap-2">
        <Campo id={`${id}-modo`} etiqueta={interpolar(t.tipo, { etiqueta })} className="[&>label]:sr-only">
          <select id={`${id}-modo`} value={valor.modo} onChange={(e) => set({ modo: e.target.value as AjusteForm["modo"] })} className={CAMPO}>
            <option value="porcentaje">{t.porcentaje}</option>
            <option value="monto">{t.monto}</option>
          </select>
        </Campo>
        <Campo id={`${id}-valor`} etiqueta={interpolar(t.valor, { etiqueta })} className="[&>label]:sr-only" error={error ?? undefined}>
          <input
            id={`${id}-valor`}
            value={valor.valor}
            onChange={(e) => set({ valor: e.target.value })}
            inputMode="decimal"
            placeholder={valor.modo === "porcentaje" ? "10" : "0.00"}
            aria-invalid={error !== null}
            className={cn(CAMPO, "font-mono tabular-nums")}
          />
        </Campo>
      </div>
      {valor.valor.trim() !== "" && !valor.recargoConsumo && !sinOpcionDeBase ? (
        <label className="flex items-center gap-2 text-control text-muted-foreground">
          <input type="checkbox" checked={valor.afectaBase} onChange={(e) => set({ afectaBase: e.target.checked })} className="size-3.5 accent-primary" />
          {textoAfecta}
        </label>
      ) : null}
      {ofreceRecargoConsumo ? (
        <label className="flex items-center gap-2 text-control text-muted-foreground">
          <input type="checkbox" checked={valor.recargoConsumo === true} onChange={(e) => set({ recargoConsumo: e.target.checked })} className="size-3.5 accent-primary" />
          {t.recargoConsumo}
        </label>
      ) : null}
    </fieldset>
  );
}
