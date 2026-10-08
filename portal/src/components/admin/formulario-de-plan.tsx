"use client";

import { PencilIcon, PlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import type { PlanAdmin } from "@/lib/api/admin-planes";
import { apiRequest } from "@/lib/api/browser";
import { ACCION_PRINCIPAL, AYUDA_CAMPO, BOTON_PRIMARIO_PIE, BOTON_SECUNDARIO_PIE, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearFechaDeLima, inicioDelProximoCiclo } from "@/lib/formato";
import { messages, mensajeError } from "@/lib/messages";
import { NOMBRE_MAX, validarPlan, valoresDePlan, VALORES_NUEVO_PLAN, type ErroresDePlan, type ValoresDePlan } from "@/lib/planes-formulario";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const t = messages.admin.planes;
const f = t.formulario;

type CampoDeNumero = "documentos" | "rucs" | "usuarios" | "apiKeys" | "retencion";

/**
 * Crear o editar un plan (#190), en un modal que dice el efecto antes de guardar. Lo que no se puede saber acá (que el nombre no esté repetido) lo contesta el
 * backend y se muestra bajo el campo. Editar cambia el nombre y el precio al instante, pero los límites entran al inicio del ciclo siguiente: el modal dice la
 * fecha, y si ya hay un cambio programado muestra esos límites y avisa que volver a poner los de hoy lo cancela.
 *
 * `claseDelBoton` reemplaza el estilo del botón que lo abre: el de «Nuevo plan» vive en la cabecera del backoffice y lleva el de su acción principal.
 */
export function FormularioDePlan({ plan, claseDelBoton }: { plan?: PlanAdmin; claseDelBoton?: string }) {
  const router = useRouter();
  const edicion = plan !== undefined;
  const inicial = plan ? valoresDePlan(plan) : VALORES_NUEVO_PLAN;
  const [abierto, setAbierto] = useState(false);
  const [valores, setValores] = useState<ValoresDePlan>(inicial);
  const [errores, setErrores] = useState<ErroresDePlan>({});
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras se envía: el administrador debe ver el resultado
    setAbierto(valor);
    // Al abrir o cerrar se parte de cero: lo escrito y no guardado no sobrevive, y un plan editado por otro se vuelve a leer de la página.
    setValores(inicial);
    setErrores({});
    setError(null);
  }

  function poner<K extends keyof ValoresDePlan>(campo: K, valor: ValoresDePlan[K]) {
    setValores((v) => ({ ...v, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));
  }

  async function guardar() {
    if (enviandoRef.current) return;
    const r = validarPlan(valores);
    if ("errores" in r) {
      setErrores(r.errores);
      return;
    }
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<PlanAdmin>(plan ? `/api/admin/planes/${plan.id}` : "/api/admin/planes", { method: plan ? "PUT" : "POST", body: r.cuerpo });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas (un alta repetida chocaría con el nombre, pero una edición sí se aplicaría dos veces).
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real.`);
      return;
    }
    if (res.estado !== "exito") {
      const mensaje = res.mensaje ?? mensajeError(res.codigo);
      if (res.codigo === "NOMBRE_DUPLICADO") setErrores({ nombre: mensaje });
      setError(mensaje);
      // El plan ya no existe (otro administrador lo borró): la página muestra el real.
      if (res.codigo === "NO_ENCONTRADO") router.refresh();
      return;
    }
    setAbierto(false);
    setValores(inicial);
    router.refresh();
  }

  const Icono = edicion ? PencilIcon : PlusIcon;
  const fechaProximoCiclo = formatearFechaDeLima(inicioDelProximoCiclo(new Date()));
  const programado = plan?.limites_programados;
  const efectos = edicion ? f.efectosEditar.map((e) => e.replace("{fecha}", fechaProximoCiclo)) : f.efectosNuevo;
  const id = plan?.id ?? "nuevo";

  function campoDeNumero(campo: CampoDeNumero, etiqueta: string, ilimitado?: "documentosIlimitado" | "usuariosIlimitado" | "apiKeysIlimitado") {
    const marcado = ilimitado ? valores[ilimitado] : false;
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={`plan-${id}-${campo}`} className={ETIQUETA_CAMPO}>
          {etiqueta}
        </label>
        <input
          id={`plan-${id}-${campo}`}
          value={marcado ? "" : valores[campo]}
          onChange={(e) => poner(campo, e.target.value)}
          disabled={marcado}
          inputMode="numeric"
          autoComplete="off"
          aria-invalid={errores[campo] ? true : undefined}
          aria-describedby={errores[campo] ? `plan-${id}-${campo}-error` : undefined}
          className={CAMPO}
        />
        {ilimitado ? (
          <label className="flex items-center gap-2 text-[12px] text-foreground/80">
            <input type="checkbox" checked={marcado} onChange={(e) => poner(ilimitado, e.target.checked)} className="size-3.5 accent-primary" />
            {f.ilimitado}
          </label>
        ) : null}
        {errores[campo] ? (
          <span id={`plan-${id}-${campo}-error`} className="text-[12px] text-destructive">
            {errores[campo]}
          </span>
        ) : null}
      </div>
    );
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger
        className={claseDelBoton ?? (edicion ? cn(buttonVariants({ variant: "outline", size: "sm" })) : ACCION_PRINCIPAL)}
        data-testid={edicion ? `plan-editar-${id}` : "plan-nuevo"}
      >
        <Icono className={edicion ? "size-3.5" : "size-4"} />
        {edicion ? t.editar : t.nuevo}
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] gap-0 overflow-y-auto p-0" data-testid="plan-formulario" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground">
              <Icono className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{plan ? f.tituloEditar.replace("{nombre}", plan.nombre) : f.tituloNuevo}</DialogTitle>
              <DialogDescription className="text-[13px]">{edicion ? f.descripcionEditar : f.descripcionNuevo}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
            <ul className="grid list-disc gap-1 pl-4">
              {efectos.map((efecto) => (
                <li key={efecto}>{efecto}</li>
              ))}
            </ul>
          </div>
          {programado ? (
            <p data-testid="plan-hay-programado" className="rounded-lg border border-warning-border bg-warning px-3 py-2 text-[12px] text-warning-foreground">
              {f.hayProgramado.replace("{fecha}", formatearFechaDeLima(programado.aplica_desde))}
            </p>
          ) : null}

          <div className="flex flex-col gap-1.5">
            <label htmlFor={`plan-${id}-nombre`} className={ETIQUETA_CAMPO}>
              {f.nombre}
            </label>
            <input
              id={`plan-${id}-nombre`}
              value={valores.nombre}
              onChange={(e) => poner("nombre", e.target.value)}
              maxLength={NOMBRE_MAX}
              placeholder={f.nombreEjemplo}
              autoComplete="off"
              aria-invalid={errores.nombre ? true : undefined}
              aria-describedby={errores.nombre ? `plan-${id}-nombre-error` : undefined}
              className={CAMPO}
              autoFocus
            />
            {errores.nombre ? (
              <span id={`plan-${id}-nombre-error`} className="text-[12px] text-destructive">
                {errores.nombre}
              </span>
            ) : null}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor={`plan-${id}-precio`} className={ETIQUETA_CAMPO}>
              {f.precio}
            </label>
            <input
              id={`plan-${id}-precio`}
              value={valores.precio}
              onChange={(e) => poner("precio", e.target.value)}
              inputMode="decimal"
              autoComplete="off"
              aria-invalid={errores.precio ? true : undefined}
              aria-describedby={errores.precio ? `plan-${id}-precio-error` : undefined}
              className={CAMPO}
            />
            {errores.precio ? (
              <span id={`plan-${id}-precio-error`} className="text-[12px] text-destructive">
                {errores.precio}
              </span>
            ) : (
              <span className={AYUDA_CAMPO}>{f.precioAyuda}</span>
            )}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            {campoDeNumero("documentos", f.documentos, "documentosIlimitado")}
            {campoDeNumero("rucs", f.rucs)}
            {campoDeNumero("usuarios", f.usuarios, "usuariosIlimitado")}
            {campoDeNumero("apiKeys", f.apiKeys, "apiKeysIlimitado")}
            {campoDeNumero("retencion", f.retencion)}
          </div>

          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO_PIE}>
            {f.cancelar}
          </button>
          <button type="button" disabled={enviando} onClick={guardar} data-testid="plan-guardar" className={BOTON_PRIMARIO_PIE}>
            <Icono className="size-4" />
            {enviando ? (edicion ? f.guardando : f.creando) : edicion ? f.guardar : f.crear}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
