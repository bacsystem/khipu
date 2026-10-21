"use client";

import { PlusIcon, Trash2Icon } from "lucide-react";
import { Campo } from "khipu-design-system/formularios/campo";
import { errorDeReferencias, MAX_DOCUMENTO, MAX_OBSERVACIONES, MAX_ORDEN_COMPRA, TIPOS_DE_DOCUMENTO, TIPOS_DE_GUIA, type ReferenciasForm } from "@/lib/comprobantes/referencias";
import { AYUDA_CAMPO, BOTON_SECUNDARIO, CAMPO } from "@/lib/estilos";
import { cn } from "@/lib/utils";

const CAMPO_DENSO = cn(CAMPO, "text-[13px]");

/**
 * Datos adicionales (#300): orden de compra (solo factura), guías de remisión, otros documentos relacionados y observaciones. Ninguno cambia importes; el primer motivo por el
 * que no valen se dice debajo y el formulario no emite mientras exista.
 */
export function ReferenciasEditor({ valor, onCambio, boleta }: { valor: ReferenciasForm; onCambio: (r: ReferenciasForm) => void; boleta: boolean }) {
  const error = errorDeReferencias(valor, boleta);
  const set = (cambio: Partial<ReferenciasForm>) => onCambio({ ...valor, ...cambio });
  const tiposDeDocumento = TIPOS_DE_DOCUMENTO.filter((t) => !(boleta && t.soloFactura));
  return (
    <div className="grid gap-4" data-testid="referencias-form">
      {boleta ? null : (
        <Campo id="nc-oc" etiqueta="Orden de compra" opcional ayuda={`Hasta ${MAX_ORDEN_COMPRA} caracteres; la imprime el PDF`}>
          <input id="nc-oc" value={valor.ordenCompra} onChange={(e) => set({ ordenCompra: e.target.value })} maxLength={MAX_ORDEN_COMPRA} placeholder="OC-2026-0457" className={cn(CAMPO_DENSO, "font-mono sm:max-w-[260px]")} />
        </Campo>
      )}

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-[13px] font-medium">Guías de remisión</legend>
        {valor.guias.map((g, i) => (
          <div key={i} className="flex items-start gap-2">
            <select aria-label={`Tipo de la guía ${i + 1}`} value={g.tipo} onChange={(e) => set({ guias: valor.guias.map((x, j) => (j === i ? { ...x, tipo: e.target.value } : x)) })} className={cn(CAMPO_DENSO, "w-[230px] shrink-0")}>
              {TIPOS_DE_GUIA.map((t) => (
                <option key={t.codigo} value={t.codigo}>{t.etiqueta}</option>
              ))}
            </select>
            <input aria-label={`Número de la guía ${i + 1}`} value={g.numero} onChange={(e) => set({ guias: valor.guias.map((x, j) => (j === i ? { ...x, numero: e.target.value } : x)) })} placeholder="T001-123" className={cn(CAMPO_DENSO, "font-mono")} />
            <button type="button" aria-label={`Quitar la guía ${i + 1}`} onClick={() => set({ guias: valor.guias.filter((_, j) => j !== i) })} className="mt-1.5 text-muted-foreground hover:text-destructive">
              <Trash2Icon className="size-4" />
            </button>
          </div>
        ))}
        <button type="button" onClick={() => set({ guias: [...valor.guias, { tipo: "09", numero: "" }] })} className={cn(BOTON_SECUNDARIO, "justify-self-start")}>
          <PlusIcon className="size-3.5" />
          Agregar guía
        </button>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-[13px] font-medium">Otros documentos relacionados</legend>
        {valor.documentos.map((d, i) => (
          <div key={i} className="flex items-start gap-2">
            <select aria-label={`Tipo del documento ${i + 1}`} value={d.tipo} onChange={(e) => set({ documentos: valor.documentos.map((x, j) => (j === i ? { ...x, tipo: e.target.value } : x)) })} className={cn(CAMPO_DENSO, "w-[230px] shrink-0")}>
              {tiposDeDocumento.map((t) => (
                <option key={t.codigo} value={t.codigo}>{t.etiqueta}</option>
              ))}
            </select>
            <input aria-label={`Número del documento ${i + 1}`} value={d.numero} onChange={(e) => set({ documentos: valor.documentos.map((x, j) => (j === i ? { ...x, numero: e.target.value } : x)) })} maxLength={MAX_DOCUMENTO + 10} placeholder="SCOP-8841203" className={cn(CAMPO_DENSO, "font-mono")} />
            <button type="button" aria-label={`Quitar el documento ${i + 1}`} onClick={() => set({ documentos: valor.documentos.filter((_, j) => j !== i) })} className="mt-1.5 text-muted-foreground hover:text-destructive">
              <Trash2Icon className="size-4" />
            </button>
          </div>
        ))}
        <button type="button" onClick={() => set({ documentos: [...valor.documentos, { tipo: tiposDeDocumento[0].codigo, numero: "" }] })} className={cn(BOTON_SECUNDARIO, "justify-self-start")}>
          <PlusIcon className="size-3.5" />
          Agregar documento
        </button>
      </fieldset>

      <Campo id="nc-observaciones" etiqueta="Observaciones" opcional ayuda="Solo en el PDF: no van a SUNAT. Si queda vacío se imprimen las de la empresa">
        <textarea id="nc-observaciones" value={valor.observaciones} onChange={(e) => set({ observaciones: e.target.value })} rows={3} className={CAMPO_DENSO} />
        <span className={cn(AYUDA_CAMPO, "justify-self-end tabular-nums")}>{valor.observaciones.length} / {MAX_OBSERVACIONES}</span>
      </Campo>

      {error ? (
        <p role="alert" className={cn(AYUDA_CAMPO, "text-destructive")} data-testid="referencias-error">
          {error}
        </p>
      ) : null}
    </div>
  );
}
