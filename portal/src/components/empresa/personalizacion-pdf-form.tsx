"use client";

import { CheckIcon, CloudArrowUpIcon, ImageIcon, PaletteIcon, TrashIcon } from "@phosphor-icons/react";
import { useRouter } from "next/navigation";
import { useMessages } from "next-intl";
import { type FormEvent, useEffect, useId, useMemo, useState } from "react";
import { AreaTexto, Campo, Entrada } from "khipu-design-system/formularios/campo";
import { Spinner } from "khipu-design-system/feedback/spinner";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { GrupoOpciones } from "khipu-design-system/formularios/opciones";
import { apiRequest } from "@/lib/api/browser";
import { interpolar } from "@/i18n/mensajes";
import { plantillasPdf, type PersonalizacionPdf, type PlantillaPdf } from "@/lib/api/empresas";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, ETIQUETA_CAMPO } from "@/lib/estilos";
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
          <GrupoOpciones<PlantillaPdf>
            nombre="pdf-plantilla"
            estilo="tarjetas"
            opciones={plantillasPdf(messages).map((p) => ({ valor: p.id, etiqueta: p.nombre, descripcion: p.descripcion }))}
            valor={plantilla}
            onCambio={setPlantilla}
          />
        </fieldset>

        <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
          <Campo id="pdf-color" etiqueta={t.colorPrimario} ayuda={plantilla === "gris" ? t.grisSinColor : t.colorAyuda}>
            <div className="flex items-center gap-2">
              <input id="pdf-color" type="color" value={colorValido ? color : "#1E1E24"} onChange={(e) => setColor(e.target.value.toUpperCase())} className="h-control w-12 cursor-pointer rounded-lg border border-border bg-card p-1" disabled={plantilla === "gris"} />
              <Entrada aria-label={t.colorHex} value={color} onChange={(e) => setColor(e.target.value.toUpperCase())} maxLength={7} mono disabled={plantilla === "gris"} aria-describedby="pdf-color-ayuda" />
            </div>
          </Campo>
          <div className="flex flex-col gap-1.5">
            <span className={ETIQUETA_CAMPO}>{t.logo}</span>
            <div className="flex items-center gap-2">
              {tieneLogo ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={`/api/proxy/empresa/logo?v=${logoVersion}`} alt={t.logoActual} className="h-control max-w-24 rounded-lg border border-border bg-card object-contain p-1" data-testid="logo-actual" />
              ) : (
                <span className="flex size-control items-center justify-center rounded-lg border border-dashed border-border text-muted-foreground"><ImageIcon className="size-4" /></span>
              )}
              <label htmlFor={logoInputId} className={cn(BOTON_SECUNDARIO, "cursor-pointer")}>
                {subiendoLogo ? <Spinner tamano="sm" /> : <CloudArrowUpIcon className="size-3.5" />}
                {subiendoLogo ? messages.empresa.comun.subiendo : tieneLogo ? t.cambiar : t.subir}
              </label>
              <input id={logoInputId} type="file" accept="image/png,image/jpeg" className="sr-only" onChange={(e) => subirLogo(e.target.files?.[0])} disabled={subiendoLogo} />
              {tieneLogo ? (
                <button type="button" onClick={quitarLogo} className={cn(BOTON_SECUNDARIO, "text-destructive hover:text-destructive")} title={t.quitarLogo}>
                  <TrashIcon className="size-3.5" />
                </button>
              ) : null}
            </div>
            <span className={AYUDA_CAMPO}>{t.logoAyuda}</span>
          </div>
        </div>

        <Campo id="pdf-pie" etiqueta={t.pie} ayuda={interpolar(t.pieAyuda, { n: pie.length })}>
          <Entrada id="pdf-pie" value={pie} onChange={(e) => setPie(e.target.value)} maxLength={300} placeholder={t.piePlaceholder} aria-describedby="pdf-pie-ayuda" />
        </Campo>

        <Campo id="pdf-observaciones" etiqueta={t.observaciones} ayuda={interpolar(t.observacionesAyuda, { n: observaciones.length })}>
          <AreaTexto id="pdf-observaciones" value={observaciones} onChange={(e) => setObservaciones(e.target.value)} maxLength={1000} rows={3} placeholder={t.observacionesPlaceholder} aria-describedby="pdf-observaciones-ayuda" />
        </Campo>

        {error ? <p className="text-cuerpo text-destructive" role="alert">{error}</p> : null}

        <div className="flex flex-wrap items-center gap-3">
          <BotonAsync type="submit" pendiente={guardando} disabled={!colorValido} icon={<PaletteIcon className="size-4" />} textoPendiente={messages.empresa.comun.guardando} className={BOTON_PRIMARIO}>
            {t.guardar}
          </BotonAsync>
          {ok ? (
            <span className="inline-flex items-center gap-1 text-control text-success-foreground" role="status" data-testid="diseno-guardado">
              <CheckIcon weight="bold" className="size-3.5" /> {t.guardado}
            </span>
          ) : null}
        </div>
      </form>

      <div className="flex min-h-[520px] flex-col gap-2 xl:col-span-7">
        <div className="flex items-center justify-between">
          <span className={ETIQUETA_CAMPO}>{t.vistaPrevia}</span>
          <a href={urlVistaPrevia} target="_blank" rel="noopener" className="text-control text-primary hover:underline">{t.abrirEnPestana}</a>
        </div>
        <iframe key={urlVistaPrevia} src={urlVistaPrevia} title={t.vistaPreviaTitulo} className="min-h-[520px] w-full flex-1 rounded-lg border border-border bg-muted" data-testid="vista-previa" />
      </div>
    </div>
  );
}
