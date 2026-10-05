"use client";

import { EyeIcon, SaveIcon, Undo2Icon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { DialogoDeAccion } from "@/components/admin/dialogo-de-accion";
import type { PlantillaDeCorreo, TextoDeCorreo } from "@/lib/api/admin-configuracion";
import { apiRequest } from "@/lib/api/browser";
import { insertarEnPosicion } from "@/lib/configuracion-formulario";
import { AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO, TARJETA } from "@/lib/estilos";
import { formatearFechaHora } from "@/lib/formato";
import { messages, mensajeError } from "@/lib/messages";
import { cn } from "@/lib/utils";

const t = messages.admin.configuracion.plantillas;

type Campo = "asunto" | "cuerpo";

/**
 * El texto de un correo de la plataforma (#199): asunto y cuerpo, con las variables que ese correo admite a un clic de donde está el cursor, una vista previa con valores de
 * ejemplo que pasa por las mismas reglas que guardar, y el botón de volver al texto de fábrica. Las reglas (variables que existen, las indispensables, los límites) las pone el
 * backend, una sola vez; acá solo se muestra lo que dice. Cambiar el texto borra la vista previa: ya no diría lo que se va a guardar. Que quedó guardado lo dice quien lo contiene
 * ({@code alResultado}): al guardar, la página se recarga y este editor se vuelve a montar con lo guardado, y el mensaje no puede morir con él.
 */
export function EditorDePlantilla({ plantilla, alResultado }: { plantilla: PlantillaDeCorreo; alResultado: (mensaje: string | null) => void }) {
  const router = useRouter();
  const [asunto, setAsunto] = useState(plantilla.vigente.asunto);
  const [cuerpo, setCuerpo] = useState(plantilla.vigente.cuerpo);
  const [vistaPrevia, setVistaPrevia] = useState<TextoDeCorreo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [calculando, setCalculando] = useState(false);
  const asuntoRef = useRef<HTMLInputElement>(null);
  const cuerpoRef = useRef<HTMLTextAreaElement>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  /** Dónde está el cursor: la variable se inserta en el campo que el administrador tocó por última vez. */
  const campoActivo = useRef<Campo>("cuerpo");
  const cursorPendiente = useRef<{ campo: Campo; posicion: number } | null>(null);
  // Refs, no el estado: dos clics en el mismo tick leen el `enviando`/`calculando` viejo del closure.
  const enviandoRef = useRef(false);
  const calculandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);
  useEffect(() => {
    const pendiente = cursorPendiente.current;
    if (!pendiente) return;
    cursorPendiente.current = null;
    const el = pendiente.campo === "asunto" ? asuntoRef.current : cuerpoRef.current;
    el?.focus();
    el?.setSelectionRange(pendiente.posicion, pendiente.posicion);
  }, [asunto, cuerpo]);

  const hayCambios = asunto !== plantilla.vigente.asunto || cuerpo !== plantilla.vigente.cuerpo;

  function editado() {
    setVistaPrevia(null);
    setError(null);
    alResultado(null);
  }

  function insertar(marca: string) {
    const campo = campoActivo.current;
    const el = campo === "asunto" ? asuntoRef.current : cuerpoRef.current;
    const actual = campo === "asunto" ? asunto : cuerpo;
    const r = insertarEnPosicion(actual, el?.selectionStart ?? actual.length, el?.selectionEnd ?? actual.length, marca);
    cursorPendiente.current = { campo, posicion: r.cursor };
    if (campo === "asunto") setAsunto(r.texto);
    else setCuerpo(r.texto);
    editado();
  }

  async function llamar<T>(ruta: string, metodo: "PUT" | "POST"): Promise<{ datos: T } | { mensaje: string }> {
    const res = await apiRequest<T>(ruta, { method: metodo, body: { asunto, cuerpo } });
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas.
      return { mensaje: `${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real.` };
    }
    if (res.estado !== "exito") return { mensaje: res.mensaje ?? mensajeError(res.codigo) };
    return { datos: res.datos as T };
  }

  async function verVistaPrevia() {
    if (calculandoRef.current) return;
    calculandoRef.current = true;
    setCalculando(true);
    setError(null);
    const r = await llamar<TextoDeCorreo>(`/api/admin/configuracion/plantillas/${plantilla.tipo}/vista-previa`, "POST");
    calculandoRef.current = false;
    setCalculando(false);
    if ("mensaje" in r) {
      setVistaPrevia(null);
      setError(r.mensaje);
      return;
    }
    setVistaPrevia(r.datos);
  }

  async function guardar() {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    alResultado(null);
    const r = await llamar<PlantillaDeCorreo>(`/api/admin/configuracion/plantillas/${plantilla.tipo}`, "PUT");
    enviandoRef.current = false;
    setEnviando(false);
    if ("mensaje" in r) {
      setError(r.mensaje);
      return;
    }
    alResultado(t.guardado);
    router.refresh();
  }

  return (
    <section className={cn(TARJETA, "grid gap-4 p-5")} aria-labelledby="plantilla-titulo" data-testid="plantilla-editor" data-tipo={plantilla.tipo}>
      <div className="grid gap-1">
        <h2 id="plantilla-titulo" className="font-heading text-lg">
          {plantilla.etiqueta}
        </h2>
        <p className="text-[13px] text-muted-foreground">
          <span className="font-medium text-foreground">{t.cuandoSeManda}:</span> {plantilla.cuando_se_manda}
        </p>
        <p data-testid="plantilla-origen" className="text-[12px] text-muted-foreground">
          {plantilla.personalizada && plantilla.actualizada_en ? t.actualizada.replace("{fecha}", formatearFechaHora(plantilla.actualizada_en)) : t.deFabrica}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_16rem]">
        <div className="grid gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="plantilla-asunto" className={ETIQUETA_CAMPO}>
              {t.asunto}
            </label>
            <input
              ref={asuntoRef}
              id="plantilla-asunto"
              data-testid="plantilla-asunto"
              value={asunto}
              maxLength={150}
              onFocus={() => (campoActivo.current = "asunto")}
              onChange={(e) => {
                setAsunto(e.target.value);
                editado();
              }}
              autoComplete="off"
              className={cn(CAMPO, "h-9")}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="plantilla-cuerpo" className={ETIQUETA_CAMPO}>
              {t.cuerpo}
            </label>
            <textarea
              ref={cuerpoRef}
              id="plantilla-cuerpo"
              data-testid="plantilla-cuerpo"
              rows={12}
              value={cuerpo}
              maxLength={5000}
              onFocus={() => (campoActivo.current = "cuerpo")}
              onChange={(e) => {
                setCuerpo(e.target.value);
                editado();
              }}
              className={cn(CAMPO, "h-auto min-h-48 py-2 font-mono text-[13px] leading-relaxed")}
            />
          </div>
        </div>

        <aside className="grid content-start gap-2" aria-label={t.variables}>
          <h3 className="text-[12px] font-semibold text-foreground">{t.variables}</h3>
          <p className={AYUDA_CAMPO}>{t.variablesAyuda}</p>
          <ul className="grid gap-1.5">
            {plantilla.variables.map((v) => (
              <li key={v.nombre} className="grid gap-0.5 rounded-lg border border-border/70 bg-muted/40 px-2.5 py-2">
                <button
                  type="button"
                  data-testid={`plantilla-variable-${v.nombre}`}
                  onClick={() => insertar(`{${v.nombre}}`)}
                  title={t.insertar.replace("{variable}", `{${v.nombre}}`)}
                  className="w-fit rounded-md bg-card px-1.5 py-0.5 font-mono text-[12px] font-medium text-foreground shadow-2xs transition-colors hover:bg-secondary"
                >
                  {`{${v.nombre}}`}
                </button>
                <span className="text-[12px] text-foreground/80">
                  {v.descripcion}
                  {v.indispensable ? <strong className="ml-1 font-semibold text-foreground">({t.indispensable})</strong> : null}
                </span>
                <span className="break-all font-mono text-[11px] text-muted-foreground">{t.ejemplo.replace("{ejemplo}", v.ejemplo)}</span>
              </li>
            ))}
          </ul>
        </aside>
      </div>

      {vistaPrevia ? (
        <div data-testid="plantilla-vista-previa" className="grid gap-2 rounded-lg border border-border bg-muted/50 px-4 py-3">
          <h3 className="text-[12px] font-semibold text-foreground">{t.vistaPreviaTitulo}</h3>
          <p className="text-[13px]">
            <span className="font-medium">{t.vistaPreviaAsunto}:</span> <span data-testid="plantilla-vista-previa-asunto">{vistaPrevia.asunto}</span>
          </p>
          <pre data-testid="plantilla-vista-previa-cuerpo" className="font-sans text-[13px] leading-relaxed break-words whitespace-pre-wrap">
            {vistaPrevia.cuerpo}
          </pre>
        </div>
      ) : null}

      {error ? (
        <p ref={alertaRef} tabIndex={-1} data-testid="plantilla-error" className="text-sm text-destructive outline-none" role="alert">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="button" disabled={enviando || calculando} onClick={verVistaPrevia} data-testid="plantilla-ver-vista-previa" className={cn(BOTON_SECUNDARIO, "h-9 px-3.5 text-[13px]")}>
          <EyeIcon className="size-4" />
          {calculando ? t.calculandoVistaPrevia : t.vistaPrevia}
        </button>
        <button type="button" disabled={enviando || calculando} onClick={guardar} data-testid="plantilla-guardar" className={cn(BOTON_PRIMARIO, "h-9 px-3.5 text-[13px]")}>
          <SaveIcon className="size-4" />
          {enviando ? t.guardando : t.guardar}
        </button>
        {plantilla.personalizada ? (
          <DialogoDeAccion
            testId="plantilla-restaurar"
            boton={t.restaurar.boton}
            icono={Undo2Icon}
            titulo={t.restaurar.titulo}
            descripcion={t.restaurar.descripcion.replace("{correo}", plantilla.etiqueta)}
            efectos={t.restaurar.efectos}
            confirmar={t.restaurar.confirmar}
            enviando={t.restaurar.enviando}
            cancelar={t.restaurar.cancelar}
            ruta={`/api/admin/configuracion/plantillas/${plantilla.tipo}`}
            metodo="DELETE"
          />
        ) : null}
        <span data-testid="plantilla-estado" className="ml-auto text-[12px] text-muted-foreground">
          {hayCambios ? t.conCambios : t.sinCambios}
        </span>
      </div>
    </section>
  );
}
