"use client";

import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { CONTROL_FILTRO } from "@/lib/estilos";
import { usePanelDesplegable } from "@/lib/panel-desplegable";
import { cn } from "@/lib/utils";

/** Lo que admite una descarga por petición (`DescargaEnZip.MAXIMO` en la API). */
export const DESCARGA_MAXIMA = 500;

const ARCHIVOS = [
  { id: "pdf", etiqueta: "PDF" },
  { id: "xml", etiqueta: "XML firmado" },
  { id: "cdr", etiqueta: "CDR de SUNAT" },
] as const;

/** La dirección de la descarga: los filtros activos de la tabla más los archivos elegidos. Vacía si no se eligió ninguno. */
export function hrefDeDescarga(filtros: FiltrosComprobantes, elegidos: string[]): string | null {
  if (elegidos.length === 0) return null;
  const qs = paramsDeFiltros(filtros);
  qs.set("tipos", elegidos.join(","));
  return `/api/proxy/facturas/descarga?${qs.toString()}`;
}

/**
 * Descarga masiva (#324): un ZIP con los archivos de los comprobantes que muestra la tabla (sus filtros, no solo la página). Se elige qué archivos llevar; sin resultados o con más
 * de {@link DESCARGA_MAXIMA} no se ofrece y se dice por qué: la API respondería 404 o 422, y mejor saberlo antes del clic.
 */
export function DescargaMasiva({ filtros, total }: { filtros: FiltrosComprobantes; total: number }) {
  const { abierto, alternar, cerrar, ref } = usePanelDesplegable("descarga-masiva");
  const [elegidos, setElegidos] = useState<string[]>(["pdf", "xml", "cdr"]);
  const href = hrefDeDescarga(filtros, elegidos);
  const motivo = total === 0 ? "No hay comprobantes con estos filtros" : total > DESCARGA_MAXIMA ? `Hay ${total}: una descarga admite hasta ${DESCARGA_MAXIMA}. Acota las fechas.` : null;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={alternar}
        aria-expanded={abierto}
        aria-controls="descarga-masiva-panel"
        className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-[12px] font-medium")}
      >
        <DownloadIcon className="size-4" />
        Descargar ZIP
      </button>
      {abierto ? (
        <div id="descarga-masiva-panel" className="absolute right-0 z-20 mt-2 grid w-72 gap-3 rounded-xl border border-border bg-card p-3 shadow-lg" data-testid="descarga-masiva">
          <p className="text-[12px] text-muted-foreground">Los {total.toLocaleString("es-PE")} comprobantes que muestra la lista con sus filtros.</p>
          <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-[12px] font-medium">Archivos</legend>
            {ARCHIVOS.map((a) => (
              <label key={a.id} className="flex items-center gap-2 text-[13px]">
                <input
                  type="checkbox"
                  checked={elegidos.includes(a.id)}
                  onChange={(e) => setElegidos((prev) => (e.target.checked ? [...prev, a.id] : prev.filter((x) => x !== a.id)))}
                  className="size-3.5 accent-primary"
                />
                {a.etiqueta}
              </label>
            ))}
          </fieldset>
          {motivo ? (
            <p role="status" className="text-[12px] text-warning-foreground" data-testid="descarga-motivo">
              {motivo}
            </p>
          ) : null}
          {href && !motivo ? (
            <a href={href} download onClick={cerrar} className="inline-flex h-9 items-center justify-center rounded-lg bg-primary px-3 text-[13px] font-medium text-primary-foreground" data-testid="descarga-enlace">
              Descargar
            </a>
          ) : (
            <span className="inline-flex h-9 cursor-not-allowed items-center justify-center rounded-lg bg-muted px-3 text-[13px] font-medium text-muted-foreground" aria-disabled="true">
              {href ? "Descargar" : "Elige al menos un archivo"}
            </span>
          )}
        </div>
      ) : null}
    </div>
  );
}
