"use client";

import { FileXlsIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useState } from "react";
import { GrupoOpciones } from "khipu-design-system/formularios/opciones";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { fetchDelPortal } from "@/lib/api/browser";
import { type FiltrosComprobantes, paramsDeFiltros } from "@/lib/api/facturas";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { Popover, PopoverContent, PopoverTrigger } from "khipu-design-system/ui/popover";
import { BOTON_PRIMARIO, CONTROL_FILTRO, ETIQUETA_CAMPO } from "@/lib/estilos";
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
  const [abierto, setAbierto] = useState(false);
  const [formato, setFormato] = useState<FormatoDeExportacion>("xlsx");
  const [separador, setSeparador] = useState<SeparadorCsv>("coma");
  const [exportando, setExportando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const motivo = total > EXPORTACION_MAXIMA ? interpolar(t.motivo, { total: total.toLocaleString(intl), maximo: EXPORTACION_MAXIMA.toLocaleString(intl) }) : null;

  async function exportar() {
    setExportando(true);
    setError(null);
    try {
      const res = await fetchDelPortal(hrefDeExportacion(filtros, formato, separador));
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
      setAbierto(false);
    } catch {
      setError(t.errorRed);
    } finally {
      setExportando(false);
    }
  }

  // El `Popover` del DS se cierra con Esc y con un clic afuera, y abrir el de «Descargar ZIP» cierra este (es un clic afuera). La lista del combo de separador
  // es un popup anidado de base-ui: elegir en ella no cierra el panel.
  return (
    <Popover open={abierto} onOpenChange={setAbierto}>
      <PopoverTrigger className={cn(CONTROL_FILTRO, "inline-flex items-center gap-1.5 px-3 text-control font-medium")}>
        <FileXlsIcon className="size-4" />
        {t.boton}
      </PopoverTrigger>
      <PopoverContent align="end" className="grid gap-3" data-testid="exportar-comprobantes">
        <p className="text-control text-muted-foreground">{interpolar(t.descripcion, { total: total.toLocaleString(intl) })}</p>
        <fieldset className="grid gap-1.5">
          <legend className={cn(ETIQUETA_CAMPO, "mb-1")}>{t.formato}</legend>
          <GrupoOpciones<FormatoDeExportacion>
            nombre="formato-exportacion"
            opciones={FORMATOS.map((f) => ({ valor: f, etiqueta: t.formatos[f].etiqueta, descripcion: t.formatos[f].ayuda }))}
            valor={formato}
            onCambio={setFormato}
          />
        </fieldset>
        {formato === "csv" ? (
          <div className="grid gap-1.5">
            <label htmlFor="exportar-separador" className={ETIQUETA_CAMPO}>
              {t.separador}
            </label>
            <SelectCampo
              id="exportar-separador"
              valor={separador}
              onCambio={(v) => setSeparador(v as SeparadorCsv)}
              opciones={[
                { value: "coma", label: t.coma },
                { value: "punto_y_coma", label: t.puntoYComa },
              ]}
            />
          </div>
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
        <BotonAsync
          pendiente={exportando}
          onClick={exportar}
          disabled={motivo !== null}
          className={BOTON_PRIMARIO}
          textoPendiente={t.exportando}
          data-testid="exportar-descargar"
        >
          {t.descargar}
        </BotonAsync>
      </PopoverContent>
    </Popover>
  );
}
