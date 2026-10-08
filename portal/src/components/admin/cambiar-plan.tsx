"use client";

import { ArrowRightLeftIcon, TriangleAlertIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { limiteEnPalabras, precioEnSoles, type PlanAdmin } from "@/lib/api/admin-planes";
import type { PrevisualizacionDePlanAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { apiRequest } from "@/lib/api/browser";
import { validarCambioDePlan, type ErroresDeCambioDePlan } from "@/lib/cambio-de-plan";
import { ACCION_PRINCIPAL, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearFechaDeLima, formatearMes } from "@/lib/formato";
import { messages, mensajeError } from "@/lib/messages";

const t = messages.admin.planDeCuenta.dialogo;

type Previa = { estado: "ninguna" } | { estado: "calculando" } | { estado: "lista"; datos: PrevisualizacionDePlanAdmin } | { estado: "error"; mensaje: string };

type Props = {
  cuentaId: string;
  cuentaNombre: string;
  planActualId: string;
  /** Los planes que se pueden asignar (los de la oferta). */
  planes: PlanAdmin[];
  /** La fecha de hoy en Lima, `YYYY-MM-DD`: para no aceptar un vencimiento que ya pasó. */
  hoy: string;
};

/**
 * Cambiar el plan de una cuenta (#191). Al elegir el plan, el modal pide al backend lo que pasaría —si sube, baja o renueva, cuándo entra y cuánto consumió la cuenta
 * este mes frente al tope del plan nuevo— y **no deja confirmar hasta tenerlo**: el administrador decide viendo el efecto. Subir entra ya; bajar, al inicio del ciclo
 * siguiente, y el modal lo dice con la fecha. Lo que no se puede saber acá (otro administrador cambió el plan en el medio) lo contesta el backend.
 */
export function CambiarPlan({ cuentaId, cuentaNombre, planActualId, planes, hoy }: Props) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [planId, setPlanId] = useState("");
  const [pagadoHasta, setPagadoHasta] = useState("");
  const [gracia, setGracia] = useState("");
  const [errores, setErrores] = useState<ErroresDeCambioDePlan>({});
  const [previa, setPrevia] = useState<Previa>({ estado: "ninguna" });
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure.
  const enviandoRef = useRef(false);
  // Cada pedido de previsualización lleva su número: una respuesta atrasada de otro plan no pisa a la del plan elegido al final.
  const pedidoRef = useRef(0);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  function reiniciar() {
    pedidoRef.current += 1;
    setPlanId("");
    setPagadoHasta("");
    setGracia("");
    setErrores({});
    setPrevia({ estado: "ninguna" });
    setError(null);
  }

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras se envía: el administrador debe ver el resultado
    setAbierto(valor);
    reiniciar();
  }

  async function elegir(id: string) {
    const pedido = ++pedidoRef.current;
    setPlanId(id);
    setErrores((e) => ({ ...e, planId: undefined, pagadoHasta: undefined }));
    setError(null);
    if (!id) {
      setPrevia({ estado: "ninguna" });
      return;
    }
    setPrevia({ estado: "calculando" });
    const res = await apiRequest<PrevisualizacionDePlanAdmin>(`/api/admin/cuentas/${cuentaId}/plan/previsualizacion?plan_id=${id}`, { method: "GET" });
    if (pedido !== pedidoRef.current) return;
    setPrevia(res.estado === "exito" && res.datos ? { estado: "lista", datos: res.datos } : { estado: "error", mensaje: res.mensaje ?? mensajeError(res.codigo) });
  }

  async function confirmar() {
    if (enviandoRef.current || previa.estado !== "lista") return;
    const r = validarCambioDePlan({ planId, precioDelPlan: previa.datos.plan_nuevo.precio_mensual, pagadoHasta, gracia }, hoy);
    if ("errores" in r) {
      setErrores(r.errores);
      return;
    }
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<unknown>(`/api/admin/cuentas/${cuentaId}/plan`, { method: "POST", body: r.cuerpo });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas (un reintento podría hacer un segundo cambio).
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver el estado real.`);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      // Otro administrador cambió el plan, o el plan salió de la oferta: la página muestra lo real.
      if (res.codigo === "CAMBIO_CONCURRENTE" || res.codigo === "PLAN_INACTIVO" || res.codigo === "NO_ENCONTRADO") router.refresh();
      return;
    }
    setAbierto(false);
    reiniciar();
    router.refresh();
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger className={ACCION_PRINCIPAL} data-testid="cambiar-plan">
        <ArrowRightLeftIcon className="size-4" />
        {messages.admin.planDeCuenta.cambiar}
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] gap-0 overflow-y-auto p-0" data-testid="cambiar-plan-dialogo" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground">
              <ArrowRightLeftIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{t.titulo.replace("{cuenta}", cuentaNombre)}</DialogTitle>
              <DialogDescription className="text-[13px]">{t.descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="cambiar-plan-plan" className={ETIQUETA_CAMPO}>
              {t.plan}
            </label>
            <select
              id="cambiar-plan-plan"
              value={planId}
              onChange={(e) => void elegir(e.target.value)}
              aria-invalid={errores.planId ? true : undefined}
              className={CAMPO}
            >
              <option value="">{t.elegir}</option>
              {planes.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} — {precioEnSoles(p.precio_mensual)}
                  {p.id === planActualId ? " (plan actual)" : ""}
                </option>
              ))}
            </select>
            {errores.planId ? <span className="text-[12px] text-destructive">{errores.planId}</span> : null}
          </div>

          {previa.estado === "ninguna" ? null : <Efecto previa={previa} />}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="cambiar-plan-hasta" className={ETIQUETA_CAMPO}>
                {t.pagadoHasta}
              </label>
              <input
                id="cambiar-plan-hasta"
                type="date"
                value={pagadoHasta}
                min={hoy}
                onChange={(e) => {
                  setPagadoHasta(e.target.value);
                  setErrores((x) => ({ ...x, pagadoHasta: undefined }));
                }}
                aria-invalid={errores.pagadoHasta ? true : undefined}
                className={CAMPO}
              />
              {errores.pagadoHasta ? <span className="text-[12px] text-destructive">{errores.pagadoHasta}</span> : <span className={AYUDA_CAMPO}>{t.pagadoHastaAyuda}</span>}
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="cambiar-plan-gracia" className={ETIQUETA_CAMPO}>
                {t.gracia}
              </label>
              <input
                id="cambiar-plan-gracia"
                value={gracia}
                onChange={(e) => {
                  setGracia(e.target.value);
                  setErrores((x) => ({ ...x, gracia: undefined }));
                }}
                inputMode="numeric"
                autoComplete="off"
                placeholder="0"
                aria-invalid={errores.gracia ? true : undefined}
                className={CAMPO}
              />
              {errores.gracia ? <span className="text-[12px] text-destructive">{errores.gracia}</span> : <span className={AYUDA_CAMPO}>{t.graciaAyuda}</span>}
            </div>
          </div>

          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-sm text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border/60 px-5 py-3">
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {t.cancelar}
          </button>
          <button
            type="button"
            disabled={enviando || previa.estado !== "lista"}
            onClick={confirmar}
            data-testid="cambiar-plan-confirmar"
            className={BOTON_PRIMARIO}
          >
            <ArrowRightLeftIcon className="size-4" />
            {enviando ? t.enviando : t.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** Lo que pasaría con el cambio: qué clase de cambio es, cuándo entra y qué pasa con el consumo del mes. */
function Efecto({ previa }: { previa: Exclude<Previa, { estado: "ninguna" }> }) {
  if (previa.estado === "calculando")
    return (
      <p data-testid="cambiar-plan-previa" role="status" className="rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[12px] text-muted-foreground">
        {t.calculando}
      </p>
    );
  if (previa.estado === "error")
    return (
      <p data-testid="cambiar-plan-previa" role="status" className="rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2.5 text-[12px] text-destructive">
        {previa.mensaje || t.errorPrevia}
      </p>
    );

  const d = previa.datos;
  const inmediato = d.efecto === "INMEDIATO";
  const limite = d.limite_de_documentos;
  const consumo = (limite.ilimitado ? t.consumoSinTope : t.consumo)
    .replace("{mes}", formatearMes(d.mes))
    .replace("{n}", String(d.consumo_del_mes))
    .replace("{plan}", d.plan_nuevo.nombre)
    .replace("{limite}", limiteEnPalabras(limite));
  return (
    <div data-testid="cambiar-plan-previa" data-direccion={d.direccion} data-efecto={d.efecto} role="status" className="grid gap-1.5 rounded-lg border border-border bg-muted/50 px-3 py-2.5 text-[12px] leading-relaxed text-foreground">
      <p className="font-medium">{t.direcciones[d.direccion]}</p>
      <p>{inmediato ? t.efectoInmediato : t.efectoCicloSiguiente.replace("{fecha}", formatearFechaDeLima(d.aplica_desde)).replace("{actual}", d.plan_actual.nombre)}</p>
      <p>{consumo}</p>
      {d.programado_que_se_descarta ? (
        <p data-testid="cambiar-plan-descarta" className="flex items-start gap-1.5 text-warning-foreground">
          <TriangleAlertIcon className="mt-0.5 size-3.5 shrink-0" />
          {(inmediato ? t.descartaInmediato : t.descartaBajada)
            .replace("{plan}", d.programado_que_se_descarta.plan.nombre)
            .replace("{fecha}", formatearFechaDeLima(d.programado_que_se_descarta.aplica_desde))}
        </p>
      ) : null}
      {d.supera_el_limite ? (
        <p data-testid="cambiar-plan-supera" className="flex items-start gap-1.5 text-warning-foreground">
          <TriangleAlertIcon className="mt-0.5 size-3.5 shrink-0" />
          {inmediato ? t.superaInmediato : t.superaBajada}
        </p>
      ) : null}
    </div>
  );
}
