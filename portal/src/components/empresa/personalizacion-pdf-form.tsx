"use client";

import { CheckIcon, ImageIcon, PaletteIcon, RefreshCcwIcon, Trash2Icon, UploadCloudIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useEffect, useId, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/browser";
import { interpolar } from "@/i18n/mensajes";
import { plantillasPdf, type PersonalizacionPdf, type PlantillaPdf } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { cn } from "@/lib/utils";

const LOGO_MAX_BYTES = 200 * 1024;
/** Cada cambio de la vista previa es un PDF renderizado en el servidor: los textos esperan a que el usuario deje de escribir. */
const RETARDO_VISTA_PREVIA_MS = 500;

function useRetardado<T>(valor: T, ms: number): T {
  const [retardado, setRetardado] = useState(valor);
  useEffect(() => {
    const t = setTimeout(() => setRetardado(valor), ms);
    return () => clearTimeout(t);
  }, [valor, ms]);
  return retardado;
}

/**
 * Diseño del PDF de la empresa: plantilla, color, logo y textos, con la vista previa del backend
 * (`GET /v1/empresa/personalizacion-pdf/vista-previa`) que se regenera con cada cambio sin guardar nada hasta pulsar "Guardar".
 */
export function PersonalizacionPdfForm({ inicial }: { inicial: PersonalizacionPdf }) {
  const router = useRouter();
  const messages = useMessages();
  const t = messages.empresa.pdf;
  const mensajeError = useMensajeError();
  const logoInputId = useId();
  const [plantilla, setPlantilla] = useState<PlantillaPdf>(inicial.plantilla);
  const [color, setColor] = useState(inicial.color_primario);
  const [pie, setPie] = useState(inicial.pie_de_pagina ?? "");
  const [observaciones, setObservaciones] = useState(inicial.observaciones_por_defecto ?? "");
  const [tieneLogo, setTieneLogo] = useState(inicial.tiene_logo);
  const [logoVersion, setLogoVersion] = useState(0);
  const [guardando, setGuardando] = useState(false);
  const [subiendoLogo, setSubiendoLogo] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  const colorValido = /^#[0-9a-fA-F]{6}$/.test(color);
  // Plantilla y color refrescan al instante (un clic = un render); los textos y el hex a medio escribir, con retardo.
  const pieRetardado = useRetardado(pie, RETARDO_VISTA_PREVIA_MS);
  const observacionesRetardadas = useRetardado(observaciones, RETARDO_VISTA_PREVIA_MS);
  const colorRetardado = useRetardado(colorValido ? color : null, RETARDO_VISTA_PREVIA_MS);

  // La vista previa es el PDF real del backend con los valores del formulario; el logo cambia la URL para que el iframe recargue.
  const urlVistaPrevia = useMemo(() => {
    const q = new URLSearchParams({ plantilla, color_primario: colorRetardado ?? inicial.color_primario, pie_de_pagina: pieRetardado, observaciones_por_defecto: observacionesRetardadas, v: String(logoVersion) });
    return `/api/proxy/empresa/personalizacion-pdf/vista-previa?${q.toString()}`;
  }, [plantilla, colorRetardado, pieRetardado, observacionesRetardadas, logoVersion, inicial.color_primario]);

  async function guardar(e: FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    setOk(false);
    const res = await apiRequest<PersonalizacionPdf>("/api/proxy/empresa/personalizacion-pdf", {
      method: "PUT",
      body: { plantilla, color_primario: color, pie_de_pagina: pie.trim() || null, observaciones_por_defecto: observaciones.trim() || null },
    });
    setGuardando(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setOk(true);
    router.refresh();
  }

  async function subirLogo(archivo: File | null | undefined) {
    if (!archivo) return;
    setError(null);
    setOk(false);
    if (!["image/png", "image/jpeg"].includes(archivo.type)) {
      setError(t.logoTipoInvalido);
      return;
    }
    if (archivo.size > LOGO_MAX_BYTES) {
      setError(interpolar(t.logoPesado, { kb: (archivo.size / 1024).toFixed(0) }));
      return;
    }
    setSubiendoLogo(true);
    const form = new FormData();
    form.set("archivo", archivo);
    const res = await apiRequest<PersonalizacionPdf>("/api/proxy/empresa/logo", { method: "PUT", body: form });
    setSubiendoLogo(false);
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setTieneLogo(true);
    setLogoVersion((v) => v + 1);
  }

  async function quitarLogo() {
    setError(null);
    const res = await apiRequest<PersonalizacionPdf>("/api/proxy/empresa/logo", { method: "DELETE" });
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      return;
    }
    setTieneLogo(false);
    setLogoVersion((v) => v + 1);
  }

  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-12" data-testid="personalizacion-pdf">
      <form onSubmit={guardar} className="grid grid-cols-1 gap-5 xl:col-span-5">
        <fieldset className="flex flex-col gap-2">
          <legend className={ETIQUETA_CAMPO}>{t.plantilla}</legend>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2" role="radiogroup" aria-label={t.plantilla}>
            {plantillasPdf(messages).map((p) => (
              <button
                key={p.id}
                type="button"
                role="radio"
                aria-checked={plantilla === p.id}
                onClick={() => setPlantilla(p.id)}
                className={cn(
                  "flex flex-col items-start rounded-lg border px-3 py-2.5 text-left transition-colors",
                  plantilla === p.id ? "border-primary bg-accent/60 ring-1 ring-primary" : "border-border bg-card hover:bg-muted",
                )}
                data-testid={`plantilla-${p.id}`}
              >
                <span className="text-[13px] font-semibold text-foreground">{p.nombre}</span>
                <span className="text-[12px] text-muted-foreground">{p.descripcion}</span>
              </button>
            ))}
          </div>
        </fieldset>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="pdf-color" className={ETIQUETA_CAMPO}>{t.colorPrimario}</label>
            <div className="flex items-center gap-2">
              <input id="pdf-color" type="color" value={colorValido ? color : "#1E1E24"} onChange={(e) => setColor(e.target.value.toUpperCase())} className="h-9 w-12 cursor-pointer rounded-lg border border-border bg-card p-1" disabled={plantilla === "gris"} />
              <input aria-label={t.colorHex} value={color} onChange={(e) => setColor(e.target.value.toUpperCase())} maxLength={7} className={cn(CAMPO, "font-mono")} disabled={plantilla === "gris"} />
            </div>
            <span className={AYUDA_CAMPO}>{plantilla === "gris" ? t.grisSinColor : t.colorAyuda}</span>
          </div>
          <div className="flex flex-col gap-1.5">
            <span className={ETIQUETA_CAMPO}>{t.logo}</span>
            <div className="flex items-center gap-2">
              {tieneLogo ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={`/api/proxy/empresa/logo?v=${logoVersion}`} alt={t.logoActual} className="h-9 max-w-24 rounded-lg border border-border bg-card object-contain p-1" data-testid="logo-actual" />
              ) : (
                <span className="flex size-9 items-center justify-center rounded-lg border border-dashed border-border text-muted-foreground"><ImageIcon className="size-4" /></span>
              )}
              <label htmlFor={logoInputId} className={cn(BOTON_SECUNDARIO, "cursor-pointer")}>
                <UploadCloudIcon className="size-3.5" />
                {subiendoLogo ? messages.empresa.comun.subiendo : tieneLogo ? t.cambiar : t.subir}
              </label>
              <input id={logoInputId} type="file" accept="image/png,image/jpeg" className="sr-only" onChange={(e) => subirLogo(e.target.files?.[0])} disabled={subiendoLogo} />
              {tieneLogo ? (
                <button type="button" onClick={quitarLogo} className={cn(BOTON_SECUNDARIO, "text-destructive hover:text-destructive")} title={t.quitarLogo}>
                  <Trash2Icon className="size-3.5" />
                </button>
              ) : null}
            </div>
            <span className={AYUDA_CAMPO}>{t.logoAyuda}</span>
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="pdf-pie" className={ETIQUETA_CAMPO}>{t.pie}</label>
          <input id="pdf-pie" value={pie} onChange={(e) => setPie(e.target.value)} maxLength={300} placeholder={t.piePlaceholder} className={CAMPO} />
          <span className={AYUDA_CAMPO}>{interpolar(t.pieAyuda, { n: pie.length })}</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="pdf-observaciones" className={ETIQUETA_CAMPO}>{t.observaciones}</label>
          <textarea id="pdf-observaciones" value={observaciones} onChange={(e) => setObservaciones(e.target.value)} maxLength={1000} rows={3} placeholder={t.observacionesPlaceholder} className={cn(CAMPO, "h-auto py-2")} />
          <span className={AYUDA_CAMPO}>{interpolar(t.observacionesAyuda, { n: observaciones.length })}</span>
        </div>

        {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}

        <div className="flex flex-wrap items-center gap-3">
          <button type="submit" disabled={guardando || !colorValido} className={BOTON_PRIMARIO}>
            {guardando ? <RefreshCcwIcon className="size-4 animate-spin" /> : <PaletteIcon className="size-4" />}
            {guardando ? messages.empresa.comun.guardando : t.guardar}
          </button>
          {ok ? (
            <span className="inline-flex items-center gap-1 text-[12px] text-success-foreground" role="status" data-testid="diseno-guardado">
              <CheckIcon className="size-3.5" /> {t.guardado}
            </span>
          ) : null}
        </div>
      </form>

      <div className="flex min-h-[520px] flex-col gap-2 xl:col-span-7">
        <div className="flex items-center justify-between">
          <span className={ETIQUETA_CAMPO}>{t.vistaPrevia}</span>
          <a href={urlVistaPrevia} target="_blank" rel="noopener" className="text-[12px] text-primary hover:underline">{t.abrirEnPestana}</a>
        </div>
        <iframe key={urlVistaPrevia} src={urlVistaPrevia} title={t.vistaPreviaTitulo} className="min-h-[520px] w-full flex-1 rounded-lg border border-border bg-muted" data-testid="vista-previa" />
      </div>
    </div>
  );
}
