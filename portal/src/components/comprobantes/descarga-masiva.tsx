"use client";

import { DownloadSimpleIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useState } from "react";
import { Casilla } from "khipu-design-system/formularios/casilla";
import { interpolar } from "@/i18n/mensajes";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { Popover, PopoverContent, PopoverTrigger } from "khipu-design-system/ui/popover";
import { BOTON_PRIMARIO, CONTROL_FILTRO, ETIQUETA_CAMPO } from "@/lib/estilos";
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
  const [abierto, setAbierto] = useState(false);
  const [elegidos, setElegidos] = useState<string[]>(["pdf", "xml", "cdr"]);
  const href = hrefDeDescarga(filtros, elegidos);
  const motivo = total === 0 ? t.sinComprobantes : total > DESCARGA_MAXIMA ? interpolar(t.tope, { total, maximo: DESCARGA_MAXIMA }) : null;

  return (
    <Popover open={abierto} onOpenChange={setAbierto}>
      <PopoverTrigger className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-control font-medium")}>
        <DownloadSimpleIcon className="size-4" />
        {t.boton}
      </PopoverTrigger>
      <PopoverContent align="end" className="grid gap-3" data-testid="descarga-masiva">
        <p className="text-control text-muted-foreground">{interpolar(t.descripcion, { total: total.toLocaleString(locale === "en" ? "en-US" : "es-PE") })}</p>
        <fieldset className="grid gap-1.5">
          <legend className={cn(ETIQUETA_CAMPO, "mb-1")}>{t.archivosLegend}</legend>
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
          <a href={href} download onClick={() => setAbierto(false)} className={BOTON_PRIMARIO} data-testid="descarga-enlace">
            {t.descargar}
          </a>
        ) : (
          <button type="button" disabled className={BOTON_PRIMARIO}>
            {href ? t.descargar : t.elegirArchivo}
          </button>
        )}
      </PopoverContent>
    </Popover>
  );
}
