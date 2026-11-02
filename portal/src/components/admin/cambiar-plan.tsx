"use client";

import { ArrowsLeftRightIcon, HourglassIcon, WarningIcon } from "@phosphor-icons/react";
import { useLocale, useMessages } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { EntradaFecha } from "khipu-design-system/formularios/entrada-fecha";
import { SelectCampo } from "khipu-design-system/formularios/select-campo";
import { Dialog, DialogContent, DialogTrigger } from "khipu-design-system/ui/dialog";
import { limiteEnPalabras, precioEnSoles, type PlanAdmin } from "@/lib/api/admin-planes";
import type { PrevisualizacionDePlanAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { apiRequest } from "@/lib/api/browser";
import { noSeSabeSiLlego } from "@/lib/api/corte-de-red";
import { validarCambioDePlan, type ErroresDeCambioDePlan } from "@/lib/cambio-de-plan";
import { ACCION_PRINCIPAL, BOTON_PRIMARIO, BOTON_SECUNDARIO } from "@/lib/estilos";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";
import { BotonAsync } from "khipu-design-system/patrones/boton-async";
import { formatearFechaDeLima, formatearMes } from "@/lib/formato";
import { interpolar } from "@/i18n/mensajes";
import { useMensajeError } from "@/i18n/use-mensaje-error";
import { Aviso, CabeceraDialogo, PieDialogo } from "khipu-design-system/patrones/cabecera-dialogo";

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
  const messages = useMessages();
  const locale = useLocale();
  const mensajeError = useMensajeError();
  const t = messages.admin.planDeCuenta.dialogo;
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
    const r = validarCambioDePlan({ planId, precioDelPlan: previa.datos.plan_nuevo.precio_mensual, pagadoHasta, gracia }, hoy, messages);
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
    if (noSeSabeSiLlego(res)) {
      // No se sabe si el cambio llegó: se dice, y no se reintenta a ciegas (un reintento podría hacer un segundo cambio).
      setError(interpolar(messages.admin.recargar.estado, { error: res.mensaje ?? mensajeError(res.codigo) }));
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
        <ArrowsLeftRightIcon className="size-4" />
        {messages.admin.planDeCuenta.cambiar}
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] gap-0 overflow-y-auto p-0" data-testid="cambiar-plan-dialogo" showCloseButton={!enviando}>
        <CabeceraDialogo icon={ArrowsLeftRightIcon} titulo={<>{t.titulo.replace("{cuenta}", cuentaNombre)}</>} descripcion={t.descripcion} />

        <div className="grid gap-4 px-5 py-4">
          <Campo id="cambiar-plan-plan" etiqueta={t.plan} error={errores.planId}>
            <SelectCampo
              id="cambiar-plan-plan"
              opciones={[
                { value: "", label: t.elegir },
                ...planes.map((p) => ({ value: p.id, label: `${p.nombre} — ${precioEnSoles(p.precio_mensual, messages, locale)}${p.id === planActualId ? t.planActual : ""}` })),
              ]}
              valor={planId}
              onCambio={(v) => void elegir(v)}
              invalido={Boolean(errores.planId)}
            />
          </Campo>

          {previa.estado === "ninguna" ? null : <Efecto previa={previa} />}

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="cambiar-plan-hasta" etiqueta={t.pagadoHasta} error={errores.pagadoHasta} ayuda={t.pagadoHastaAyuda}>
              <EntradaFecha
                id="cambiar-plan-hasta"
                valor={pagadoHasta}
                min={hoy}
                onCambio={(v) => {
                  setPagadoHasta(v);
                  setErrores((x) => ({ ...x, pagadoHasta: undefined }));
                }}
                limpiable
                etiquetaLimpiar={messages.comun.quitarFecha}
                placeholder={messages.comun.elegirFecha}
                invalido={Boolean(errores.pagadoHasta)}
                describidoPor={errores.pagadoHasta ? "cambiar-plan-hasta-error" : "cambiar-plan-hasta-ayuda"}
              />
            </Campo>
            {/* `Entrada` y no `StepperNumerico`: los días viajan como texto a `validarCambioDePlan`, que explica qué está mal en vez de recortarlo. */}
            <Campo id="cambiar-plan-gracia" etiqueta={t.gracia} error={errores.gracia} ayuda={t.graciaAyuda}>
              <Entrada
                id="cambiar-plan-gracia"
                value={gracia}
                onChange={(e) => {
                  setGracia(e.target.value);
                  setErrores((x) => ({ ...x, gracia: undefined }));
                }}
                inputMode="numeric"
                autoComplete="off"
                placeholder="0"
                invalido={Boolean(errores.gracia)}
                aria-describedby={errores.gracia ? "cambiar-plan-gracia-error" : "cambiar-plan-gracia-ayuda"}
              />
            </Campo>
          </div>

          {error ? (
            <p ref={alertaRef} tabIndex={-1} className="text-cuerpo text-destructive outline-none" role="alert">
              {error}
            </p>
          ) : null}
        </div>

        <PieDialogo>
          <button type="button" disabled={enviando} onClick={() => cambiarAbierto(false)} className={BOTON_SECUNDARIO}>
            {t.cancelar}
          </button>
          <BotonAsync
            pendiente={enviando}
            disabled={previa.estado !== "lista"}
            onClick={confirmar}
            data-testid="cambiar-plan-confirmar"
            icon={<ArrowsLeftRightIcon className="size-4" />}
            textoPendiente={t.enviando}
            className={BOTON_PRIMARIO}
          >
            {t.confirmar}
          </BotonAsync>
        </PieDialogo>
      </DialogContent>
    </Dialog>
  );
}

/** Lo que pasaría con el cambio: qué clase de cambio es, cuándo entra y qué pasa con el consumo del mes. */
function Efecto({ previa }: { previa: Exclude<Previa, { estado: "ninguna" }> }) {
  const messages = useMessages();
  const locale = useLocale();
  const t = messages.admin.planDeCuenta.dialogo;
  if (previa.estado === "calculando")
    return (
      <div data-testid="cambiar-plan-previa" role="status">
        <Aviso tono="neutro" icon={HourglassIcon}>
          {t.calculando}
        </Aviso>
      </div>
    );
  if (previa.estado === "error")
    return (
      // El `Aviso` del DS no lleva rol ni `data-testid`: los conserva el envoltorio.
      <div data-testid="cambiar-plan-previa" role="status">
        <Aviso tono="peligro" icon={WarningIcon}>
          {previa.mensaje || t.errorPrevia}
        </Aviso>
      </div>
    );

  const d = previa.datos;
  const inmediato = d.efecto === "INMEDIATO";
  const limite = d.limite_de_documentos;
  const consumo = (limite.ilimitado ? t.consumoSinTope : t.consumo)
    .replace("{mes}", formatearMes(d.mes, locale))
    .replace("{n}", String(d.consumo_del_mes))
    .replace("{plan}", d.plan_nuevo.nombre)
    .replace("{limite}", limiteEnPalabras(limite, messages));
  return (
    // El `Aviso` neutro del DS; el rol, el `data-testid` y los `data-*` quedan en el envoltorio.
    <div data-testid="cambiar-plan-previa" data-direccion={d.direccion} data-efecto={d.efecto} role="status">
      <Aviso tono="neutro" icon={ArrowsLeftRightIcon}>
      <div className="grid gap-1.5">
      <p className="font-medium">{t.direcciones[d.direccion]}</p>
      <p>{inmediato ? t.efectoInmediato : t.efectoCicloSiguiente.replace("{fecha}", formatearFechaDeLima(d.aplica_desde, locale)).replace("{actual}", d.plan_actual.nombre)}</p>
      <p>{consumo}</p>
      {d.programado_que_se_descarta ? (
        <p data-testid="cambiar-plan-descarta" className="flex items-start gap-1.5 text-warning-foreground">
          <WarningIcon className="mt-0.5 size-3.5 shrink-0" />
          {(inmediato ? t.descartaInmediato : t.descartaBajada)
            .replace("{plan}", d.programado_que_se_descarta.plan.nombre)
            .replace("{fecha}", formatearFechaDeLima(d.programado_que_se_descarta.aplica_desde, locale))}
        </p>
      ) : null}
      {d.supera_el_limite ? (
        <p data-testid="cambiar-plan-supera" className="flex items-start gap-1.5 text-warning-foreground">
          <WarningIcon className="mt-0.5 size-3.5 shrink-0" />
          {inmediato ? t.superaInmediato : t.superaBajada}
        </p>
      ) : null}
      </div>
      </Aviso>
    </div>
  );
}
