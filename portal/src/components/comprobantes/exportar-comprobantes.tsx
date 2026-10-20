"use client";

import { FileSpreadsheetIcon } from "lucide-react";
import { useState } from "react";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { CONTROL_FILTRO } from "@/lib/estilos";
import { mensajeError } from "@/lib/messages";
import { usePanelDesplegable } from "@/lib/panel-desplegable";
import { cn } from "@/lib/utils";

/** Lo que admite una exportación por petición (`ExportacionDeComprobantes.MAXIMO` en la API). */
export const EXPORTACION_MAXIMA = 5000;

export type FormatoDeExportacion = "xlsx" | "csv";
export type SeparadorCsv = "coma" | "punto_y_coma";

const FORMATOS: Array<{ id: FormatoDeExportacion; etiqueta: string; ayuda: string }> = [
  { id: "xlsx", etiqueta: "Excel (.xlsx)", ayuda: "Para abrirlo en Excel: los importes llegan como números." },
  { id: "csv", etiqueta: "CSV", ayuda: "Texto separado. Los textos que empiezan por = + - @ llevan delante una comilla ('), para que la hoja de cálculo no los tome como fórmula." },
];

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
  const { abierto, alternar, cerrar, ref } = usePanelDesplegable("exportar-comprobantes");
  const [formato, setFormato] = useState<FormatoDeExportacion>("xlsx");
  const [separador, setSeparador] = useState<SeparadorCsv>("coma");
  const [exportando, setExportando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const motivo = total > EXPORTACION_MAXIMA ? `Hay ${total.toLocaleString("es-PE")}: una exportación admite hasta ${EXPORTACION_MAXIMA.toLocaleString("es-PE")}. Acota las fechas.` : null;

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
      setError("No se pudo exportar: revisa tu conexión y vuelve a intentarlo.");
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
        className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-[12px] font-medium")}
      >
        <FileSpreadsheetIcon className="size-4" />
        Exportar
      </button>
      {abierto ? (
        <div id="exportar-panel" className="absolute right-0 z-20 mt-2 grid w-72 gap-3 rounded-xl border border-border bg-card p-3 shadow-lg" data-testid="exportar-comprobantes">
          <p className="text-[12px] text-muted-foreground">
            Una fila por comprobante de los {total.toLocaleString("es-PE")} que muestra la lista con sus filtros: bases, IGV, ICBPER, ISC, cargos, descuentos, anticipos, total, estado y
            documento modificado. Las notas de crédito salen en negativo; sin filtro de estado no entran los rechazados, inválidos ni descartados.
          </p>
          <fieldset className="grid gap-1.5">
            <legend className="mb-1 text-[12px] font-medium">Formato</legend>
            {FORMATOS.map((f) => (
              <label key={f.id} className="flex items-start gap-2 text-[13px]">
                <input type="radio" name="formato-exportacion" checked={formato === f.id} onChange={() => setFormato(f.id)} className="mt-0.5 size-3.5 accent-primary" />
                <span>
                  {f.etiqueta}
                  <span className="block text-[11px] text-muted-foreground">{f.ayuda}</span>
                </span>
              </label>
            ))}
          </fieldset>
          {formato === "csv" ? (
            <label className="grid gap-1 text-[12px] font-medium">
              Separador
              <select value={separador} onChange={(e) => setSeparador(e.target.value as SeparadorCsv)} className={cn(CONTROL_FILTRO, "px-2 font-normal")}>
                <option value="coma">Coma (Excel en Perú)</option>
                <option value="punto_y_coma">Punto y coma</option>
              </select>
            </label>
          ) : null}
          {motivo ? (
            <p role="status" className="text-[12px] text-warning-foreground" data-testid="exportar-motivo">
              {motivo}
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="text-[12px] text-destructive" data-testid="exportar-error">
              {error}
            </p>
          ) : null}
          <button
            type="button"
            onClick={exportar}
            disabled={exportando || motivo !== null}
            className="inline-flex h-9 items-center justify-center rounded-lg bg-primary px-3 text-[13px] font-medium text-primary-foreground disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground"
            data-testid="exportar-descargar"
          >
            {exportando ? "Exportando…" : "Descargar"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
