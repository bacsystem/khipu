"use client";

import { DownloadSimpleIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useState } from "react";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { interpolar } from "@/i18n/mensajes";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { CONTROL_FILTRO, TARJETA } from "@/lib/estilos";
import { usePanelDesplegable } from "@/lib/panel-desplegable";
import { cn } from "@/lib/utils";

/** Lo que admite una descarga por petición (`DescargaEnZip.MAXIMO` en la API). */
export const DESCARGA_MAXIMA = 500;

/** Los archivos que se pueden llevar; su nombre está en `messages.comprobantes.descargaMasiva.archivos` (#25). */
const ARCHIVOS = ["pdf", "xml", "cdr"] as const;

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
  const t = useMessages().comprobantes.descargaMasiva;
  const locale = useLocale();
  const { abierto, alternar, cerrar, ref } = usePanelDesplegable("descarga-masiva");
  const [elegidos, setElegidos] = useState<string[]>(["pdf", "xml", "cdr"]);
  const href = hrefDeDescarga(filtros, elegidos);
  const motivo = total === 0 ? t.sinComprobantes : total > DESCARGA_MAXIMA ? interpolar(t.tope, { total, maximo: DESCARGA_MAXIMA }) : null;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={alternar}
        aria-expanded={abierto}
        aria-controls="descarga-masiva-panel"
        className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-control font-medium")}
      >
        <DownloadSimpleIcon className="size-4" />
        {t.boton}
      </button>
      {abierto ? (
        <div id="descarga-masiva-panel" className={cn(TARJETA, "absolute right-0 z-20 mt-2 grid w-72 gap-3 p-3 shadow-lg")} data-testid="descarga-masiva">
          <p className="text-control text-muted-foreground">{interpolar(t.descripcion, { total: total.toLocaleString(locale === "en" ? "en-US" : "es-PE") })}</p>
          <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-control font-medium">{t.archivosLegend}</legend>
            {ARCHIVOS.map((a) => (
              <Casilla
                key={a}
                id={`descarga-masiva-${a}`}
                etiqueta={t.archivos[a]}
                checked={elegidos.includes(a)}
                onCheckedChange={(v) => setElegidos((prev) => (v ? [...prev, a] : prev.filter((x) => x !== a)))}
              />
            ))}
          </fieldset>
          {motivo ? (
            <p role="status" className="text-control text-warning-foreground" data-testid="descarga-motivo">
              {motivo}
            </p>
          ) : null}
          {href && !motivo ? (
            <a href={href} download onClick={cerrar} className="inline-flex h-control items-center justify-center rounded-lg bg-primary px-3 text-control font-medium text-primary-foreground" data-testid="descarga-enlace">
              {t.descargar}
            </a>
          ) : (
            <span className="inline-flex h-control cursor-not-allowed items-center justify-center rounded-lg bg-muted px-3 text-control font-medium text-muted-foreground" aria-disabled="true">
              {href ? t.descargar : t.elegirArchivo}
            </span>
          )}
        </div>
      ) : null}
    </div>
  );
}
