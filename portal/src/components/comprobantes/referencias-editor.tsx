"use client";

import { PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useMessages } from "next-intl";
import { AreaTexto, Campo } from "khipu-design-system/formularios/campo";
import { Button } from "khipu-design-system/ui/button";
import { interpolar } from "@/i18n/mensajes";
import { errorDeReferencias, MAX_DOCUMENTO, MAX_OBSERVACIONES, MAX_ORDEN_COMPRA, tiposDeDocumento as tiposDeDocumentoDe, tiposDeGuia, type ReferenciasForm } from "@/lib/comprobantes/referencias";
import { AYUDA_CAMPO, BOTON_SECUNDARIO, CAMPO, SELECT_NATIVO } from "@/lib/estilos";
import { cn } from "@/lib/utils";


/**
 * Datos adicionales (#300): orden de compra (solo factura), guías de remisión, otros documentos relacionados y observaciones. Ninguno cambia importes; el primer motivo por el
 * que no valen se dice debajo y el formulario no emite mientras exista.
 */
export function ReferenciasEditor({ valor, onCambio, boleta }: { valor: ReferenciasForm; onCambio: (r: ReferenciasForm) => void; boleta: boolean }) {
  const messages = useMessages();
  const t = messages.comprobantes.editores.referencias;
  const error = errorDeReferencias(valor, boleta, messages);
  const set = (cambio: Partial<ReferenciasForm>) => onCambio({ ...valor, ...cambio });
  const tiposDeDocumento = tiposDeDocumentoDe(messages).filter((d) => !(boleta && d.soloFactura));
  return (
    <div className="grid gap-4" data-testid="referencias-form">
      {boleta ? null : (
        <Campo id="nc-oc" etiqueta={t.ordenCompra} opcional ayuda={interpolar(t.ordenCompraAyuda, { max: MAX_ORDEN_COMPRA })}>
          <input id="nc-oc" value={valor.ordenCompra} onChange={(e) => set({ ordenCompra: e.target.value })} maxLength={MAX_ORDEN_COMPRA} placeholder="OC-2026-0457" className={cn(CAMPO, "font-mono sm:max-w-[260px]")} />
        </Campo>
      )}

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-control font-medium">{t.guias}</legend>
        {valor.guias.map((g, i) => (
          <div key={i} className="flex items-start gap-2">
            <select aria-label={interpolar(t.tipoGuia, { n: i + 1 })} value={g.tipo} onChange={(e) => set({ guias: valor.guias.map((x, j) => (j === i ? { ...x, tipo: e.target.value } : x)) })} className={cn(SELECT_NATIVO, "w-[230px] shrink-0")}>
              {tiposDeGuia(messages).map((g) => (
                <option key={g.codigo} value={g.codigo}>{g.etiqueta}</option>
              ))}
            </select>
            <input aria-label={interpolar(t.numeroGuia, { n: i + 1 })} value={g.numero} onChange={(e) => set({ guias: valor.guias.map((x, j) => (j === i ? { ...x, numero: e.target.value } : x)) })} placeholder="T001-123" className={cn(CAMPO, "font-mono")} />
            <Button type="button" aria-label={interpolar(t.quitarGuia, { n: i + 1 })} onClick={() => set({ guias: valor.guias.filter((_, j) => j !== i) })} variant="ghost" size="icon-sm" className="text-muted-foreground hover:text-destructive">
              <TrashIcon className="size-4" />
            </Button>
          </div>
        ))}
        <button type="button" onClick={() => set({ guias: [...valor.guias, { tipo: "09", numero: "" }] })} className={cn(BOTON_SECUNDARIO, "justify-self-start")}>
          <PlusIcon weight="bold" className="size-3.5" />
          {t.agregarGuia}
        </button>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-control font-medium">{t.documentos}</legend>
        {valor.documentos.map((d, i) => (
          <div key={i} className="flex items-start gap-2">
            <select aria-label={interpolar(t.tipoDocumento, { n: i + 1 })} value={d.tipo} onChange={(e) => set({ documentos: valor.documentos.map((x, j) => (j === i ? { ...x, tipo: e.target.value } : x)) })} className={cn(SELECT_NATIVO, "w-[230px] shrink-0")}>
              {tiposDeDocumento.map((x) => (
                <option key={x.codigo} value={x.codigo}>{x.etiqueta}</option>
              ))}
            </select>
            <input aria-label={interpolar(t.numeroDocumento, { n: i + 1 })} value={d.numero} onChange={(e) => set({ documentos: valor.documentos.map((x, j) => (j === i ? { ...x, numero: e.target.value } : x)) })} maxLength={MAX_DOCUMENTO + 10} placeholder="SCOP-8841203" className={cn(CAMPO, "font-mono")} />
            <Button type="button" aria-label={interpolar(t.quitarDocumento, { n: i + 1 })} onClick={() => set({ documentos: valor.documentos.filter((_, j) => j !== i) })} variant="ghost" size="icon-sm" className="text-muted-foreground hover:text-destructive">
              <TrashIcon className="size-4" />
            </Button>
          </div>
        ))}
        <button type="button" onClick={() => set({ documentos: [...valor.documentos, { tipo: tiposDeDocumento[0].codigo, numero: "" }] })} className={cn(BOTON_SECUNDARIO, "justify-self-start")}>
          <PlusIcon weight="bold" className="size-3.5" />
          {t.agregarDocumento}
        </button>
      </fieldset>

      <Campo id="nc-observaciones" etiqueta={t.observaciones} opcional ayuda={t.observacionesAyuda}>
        <AreaTexto id="nc-observaciones" value={valor.observaciones} onChange={(e) => set({ observaciones: e.target.value })} rows={3} />
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
