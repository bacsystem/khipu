"use client";

import { FileXlsIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useState } from "react";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { CONTROL_FILTRO, TARJETA } from "@/lib/estilos";
import { usePanelDesplegable } from "@/lib/panel-desplegable";
import { cn } from "@/lib/utils";

/** Lo que admite una exportación por petición (`ExportacionDeComprobantes.MAXIMO` en la API). */
export const EXPORTACION_MAXIMA = 5000;

export type FormatoDeExportacion = "xlsx" | "csv";
export type SeparadorCsv = "coma" | "punto_y_coma";

/** Los formatos ofrecidos; su nombre y su ayuda están en `messages.comprobantes.exportar.formatos` (#25). */
const FORMATOS: FormatoDeExportacion[] = ["xlsx", "csv"];

/** La dirección de la exportación: los filtros activos de la tabla, el formato y, en CSV, el separador (la coma es el valor por defecto de la API). */
export function hrefDeExportacion(filtros: FiltrosComprobantes, formato: FormatoDeExportacion, separador: SeparadorCsv = "coma"): string {
  const qs = paramsDeFiltros(filtros);
  qs.set("formato", formato);
  if (formato === "csv" && separador !== "coma") qs.set("separador", separador);
  return `/api/proxy/facturas/exportacion?${qs.toString()}`;
}

/** El nombre que propone la API (`Content-Disposition`), o uno por defecto. */
export function nombreDelArchivo(disposicion: string | null, formato: FormatoDeExportacion): string {
  const m = disposicion?.match(/filename="?([^";]+)"?/i);
  return m?.[1] ?? `comprobantes.${formato}`;
}

/**
 * Exportación para el contador (#321): una fila por comprobante de los que muestra la tabla (sus filtros, no solo la página), en Excel o CSV. Se baja con `fetch` y no con un
 * enlace: así se ve que está en curso y, si la API responde un error (el tope, la sesión vencida), se dice cuál en vez de bajar un archivo con el JSON del error. Por encima de
 * {@link EXPORTACION_MAXIMA} ni se intenta: la API respondería 422.
 */
export function ExportarComprobantes({ filtros, total }: { filtros: FiltrosComprobantes; total: number }) {
  const t = useMessages().comprobantes.exportar;
  const locale = useLocale();
  const intl = locale === "en" ? "en-US" : "es-PE";
  const mensajeError = useMensajeError();
  const { abierto, alternar, cerrar, ref } = usePanelDesplegable("exportar-comprobantes");
  const [formato, setFormato] = useState<FormatoDeExportacion>("xlsx");
  const [separador, setSeparador] = useState<SeparadorCsv>("coma");
  const [exportando, setExportando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const motivo = total > EXPORTACION_MAXIMA ? interpolar(t.motivo, { total: total.toLocaleString(intl), maximo: EXPORTACION_MAXIMA.toLocaleString(intl) }) : null;

  async function exportar() {
    setExportando(true);
    setError(null);
    try {
      const res = await fetch(hrefDeExportacion(filtros, formato, separador));
      if (!res.ok) {
        const cuerpo = (await res.json().catch(() => null)) as { mensaje?: string | null; codigo?: string | null } | null;
        setError(cuerpo?.mensaje ?? mensajeError(cuerpo?.codigo ?? undefined));
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = nombreDelArchivo(res.headers.get("content-disposition"), formato);
      document.body.appendChild(a);
      a.click();
      a.remove();
      // El navegador ya tomó el archivo: liberar la URL después del clic, no antes.
      setTimeout(() => URL.revokeObjectURL(url), 0);
      cerrar();
    } catch {
      setError(t.errorRed);
    } finally {
      setExportando(false);
    }
  }

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={alternar}
        aria-expanded={abierto}
        aria-controls="exportar-panel"
        className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-control font-medium")}
      >
        <FileXlsIcon className="size-4" />
        {t.boton}
      </button>
      {abierto ? (
        <div id="exportar-panel" className={cn(TARJETA, "absolute right-0 z-20 mt-2 grid w-72 gap-3 p-3 shadow-lg")} data-testid="exportar-comprobantes">
          <p className="text-control text-muted-foreground">{interpolar(t.descripcion, { total: total.toLocaleString(intl) })}</p>
          <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-control font-medium">{t.formato}</legend>
            {FORMATOS.map((f) => (
              <label key={f} className="flex items-start gap-2 text-control">
                <input type="radio" name="formato-exportacion" checked={formato === f} onChange={() => setFormato(f)} className="mt-0.5 size-3.5 accent-primary" />
                <span>
                  {t.formatos[f].etiqueta}
                  <span className="block text-etiqueta text-muted-foreground">{t.formatos[f].ayuda}</span>
                </span>
              </label>
            ))}
          </fieldset>
          {formato === "csv" ? (
            <label className="grid gap-1 text-control font-medium">
              {t.separador}
              <select value={separador} onChange={(e) => setSeparador(e.target.value as SeparadorCsv)} className={cn(CONTROL_FILTRO, "px-2 font-normal")}>
                <option value="coma">{t.coma}</option>
                <option value="punto_y_coma">{t.puntoYComa}</option>
              </select>
            </label>
          ) : null}
          {motivo ? (
            <p role="status" className="text-control text-warning-foreground" data-testid="exportar-motivo">
              {motivo}
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="text-control text-destructive" data-testid="exportar-error">
              {error}
            </p>
          ) : null}
          <button
            type="button"
            onClick={exportar}
            disabled={exportando || motivo !== null}
            className="inline-flex h-control items-center justify-center rounded-lg bg-primary px-3 text-control font-medium text-primary-foreground disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
            data-testid="exportar-descargar"
          >
            {exportando ? t.exportando : t.descargar}
          </button>
        </div>
      ) : null}
    </div>
  );
}
