"use client";

import { BanknoteIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { MEDIOS_DE_PAGO } from "@/lib/api/admin-pagos";
import type { PlanDeCuentaAdmin } from "@/lib/api/admin-plan-de-cuenta";
import { apiRequest } from "@/lib/api/browser";
import { fechaValida } from "@/lib/cambio-de-plan";
import { ACCION_PRINCIPAL, AYUDA_CAMPO, BOTON_PRIMARIO, BOTON_SECUNDARIO, CAMPO, ETIQUETA_CAMPO } from "@/lib/estilos";
import { formatearFecha, sumarDias, ultimoDiaCubierto } from "@/lib/formato";
import { messages, mensajeError } from "@/lib/messages";
import { validarPago, type ErroresDePago } from "@/lib/pago-formulario";

const t = messages.admin.pagos.dialogo;
const medios = messages.admin.pagos.medios;

type Props = {
  cuentaId: string;
  cuentaNombre: string;
  /** El plan de la cuenta; nulo si no se pudo cargar (entonces no se ofrece extender el vencimiento). */
  plan: PlanDeCuentaAdmin | null;
  /** La fecha de hoy en Lima, `YYYY-MM-DD`: la fecha de pago no puede ser posterior. */
  hoy: string;
};

/**
 * Registrar a mano el pago de una cuenta (#194). Pide el periodo que cubre, el monto, el medio y la fecha, y ofrece **extender el vencimiento** del plan hasta el final
 * del periodo: solo si el plan vence y el periodo lo adelanta, y entonces viene marcado (es lo que casi siempre se quiere), diciendo de qué día a qué día se mueve.
 * No hay pasarela: esto solo anota. Lo que no se puede saber acá (un pago repetido, otro administrador moviendo el vencimiento) lo contesta el backend.
 */
export function RegistrarPago({ cuentaId, cuentaNombre, plan, hoy }: Props) {
  const router = useRouter();
  const pagadoHastaActual = plan?.vence_en ? ultimoDiaCubierto(plan.vence_en) : undefined;
  const planVence = Boolean(plan?.vence_en);
  const desdeSugerido = pagadoHastaActual && pagadoHastaActual >= hoy ? sumarDias(pagadoHastaActual, 1) : hoy;

  const [abierto, setAbierto] = useState(false);
  const [desde, setDesde] = useState(desdeSugerido);
  const [hasta, setHasta] = useState("");
  const [monto, setMonto] = useState("");
  const [medio, setMedio] = useState("");
  const [fecha, setFecha] = useState(hoy);
  const [referencia, setReferencia] = useState("");
  const [nota, setNota] = useState("");
  // Nulo mientras el administrador no toque la casilla: entonces vale lo que sugiere el formulario (extender si se puede).
  const [extenderElegido, setExtenderElegido] = useState<boolean | null>(null);
  const [errores, setErrores] = useState<ErroresDePago>({});
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const alertaRef = useRef<HTMLParagraphElement>(null);
  // Un ref, no el estado: dos clics en el mismo tick leen el `enviando` viejo del closure.
  const enviandoRef = useRef(false);
  useEffect(() => {
    if (error) alertaRef.current?.focus();
  }, [error]);

  const hastaValida = fechaValida(hasta);
  const adelanta = Boolean(pagadoHastaActual) && hastaValida && hasta > (pagadoHastaActual as string);
  const puedeExtender = planVence && adelanta;
  const extender = puedeExtender && (extenderElegido ?? true);

  function limpiar<K extends keyof ErroresDePago>(campo: K) {
    setErrores((e) => ({ ...e, [campo]: undefined }));
  }

  function reiniciar() {
    setDesde(desdeSugerido);
    setHasta("");
    setMonto("");
    setMedio("");
    setFecha(hoy);
    setReferencia("");
    setNota("");
    setExtenderElegido(null);
    setErrores({});
    setError(null);
  }

  function cambiarAbierto(valor: boolean) {
    if (enviando) return; // no se cierra mientras se envía: el administrador debe ver el resultado
    setAbierto(valor);
    reiniciar();
  }

  async function confirmar() {
    if (enviandoRef.current) return;
    const r = validarPago({ desde, hasta, monto, medio, fecha, referencia, nota, extender }, { hoy, planVence, pagadoHastaActual });
    if ("errores" in r) {
      setErrores(r.errores);
      return;
    }
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    const res = await apiRequest<unknown>(`/api/admin/cuentas/${cuentaId}/pagos`, { method: "POST", body: r.cuerpo });
    enviandoRef.current = false;
    setEnviando(false);
    if (res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA") {
      // No se sabe si el pago llegó: se dice, y no se reintenta a ciegas (un reintento podría anotarlo dos veces).
      setError(`${res.mensaje ?? mensajeError(res.codigo)} Recarga la página para ver si quedó registrado.`);
      return;
    }
    if (res.estado !== "exito") {
      setError(res.mensaje ?? mensajeError(res.codigo));
      // Otro administrador movió el vencimiento: la página muestra lo real.
      if (res.codigo === "CAMBIO_CONCURRENTE" || res.codigo === "NO_ENCONTRADO") router.refresh();
      return;
    }
    setAbierto(false);
    reiniciar();
    router.refresh();
  }

  const notaDeExtension = !plan
    ? t.extenderSinPlan
    : !planVence
      ? t.extenderNoVence
      : !hastaValida
        ? t.extenderElegirPeriodo
        : !adelanta
          ? t.extenderNoAdelanta.replace("{actual}", formatearFecha(pagadoHastaActual as string))
          : t.extenderHoy.replace("{actual}", formatearFecha(pagadoHastaActual as string)).replace("{nuevo}", formatearFecha(hasta));

  return (
    <Dialog open={abierto} onOpenChange={cambiarAbierto}>
      <DialogTrigger className={ACCION_PRINCIPAL} data-testid="registrar-pago">
        <BanknoteIcon className="size-4" />
        {messages.admin.pagos.registrar}
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] gap-0 overflow-y-auto p-0" data-testid="registrar-pago-dialogo" showCloseButton={!enviando}>
        <DialogHeader className="border-b border-border/60 px-5 py-4 pr-14">
          <div className="flex items-center gap-2.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground">
              <BanknoteIcon className="size-4" />
            </div>
            <div className="min-w-0">
              <DialogTitle className="text-base font-semibold tracking-tight">{t.titulo.replace("{cuenta}", cuentaNombre)}</DialogTitle>
              <DialogDescription className="text-[13px]">{t.descripcion}</DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="grid gap-4 px-5 py-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="registrar-pago-desde" etiqueta={t.desde} error={errores.desde}>
              <input
                id="registrar-pago-desde"
                type="date"
                value={desde}
                onChange={(e) => {
                  setDesde(e.target.value);
                  limpiar("desde");
                  limpiar("hasta");
                }}
                aria-invalid={errores.desde ? true : undefined}
                className={CAMPO}
              />
            </Campo>
            <Campo id="registrar-pago-hasta" etiqueta={t.hasta} error={errores.hasta}>
              <input
                id="registrar-pago-hasta"
                type="date"
                value={hasta}
                onChange={(e) => {
                  setHasta(e.target.value);
                  limpiar("hasta");
                }}
                aria-invalid={errores.hasta ? true : undefined}
                className={CAMPO}
              />
            </Campo>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="registrar-pago-monto" etiqueta={t.monto} error={errores.monto} ayuda={t.montoAyuda}>
              <input
                id="registrar-pago-monto"
                value={monto}
                onChange={(e) => {
                  setMonto(e.target.value);
                  limpiar("monto");
                }}
                inputMode="decimal"
                autoComplete="off"
                placeholder="29.00"
                aria-invalid={errores.monto ? true : undefined}
                className={CAMPO}
              />
            </Campo>
            <Campo id="registrar-pago-medio" etiqueta={t.medio} error={errores.medio}>
              <select
                id="registrar-pago-medio"
                value={medio}
                onChange={(e) => {
                  setMedio(e.target.value);
                  limpiar("medio");
                }}
                aria-invalid={errores.medio ? true : undefined}
                className={CAMPO}
              >
                <option value="">{t.elegirMedio}</option>
                {MEDIOS_DE_PAGO.map((m) => (
                  <option key={m} value={m}>
                    {medios[m]}
                  </option>
                ))}
              </select>
            </Campo>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="registrar-pago-fecha" etiqueta={t.fecha} error={errores.fecha}>
              <input
                id="registrar-pago-fecha"
                type="date"
                value={fecha}
                max={hoy}
                onChange={(e) => {
                  setFecha(e.target.value);
                  limpiar("fecha");
                }}
                aria-invalid={errores.fecha ? true : undefined}
                className={CAMPO}
              />
            </Campo>
            <Campo id="registrar-pago-referencia" etiqueta={t.referencia} error={errores.referencia} ayuda={t.referenciaAyuda}>
              <input
                id="registrar-pago-referencia"
                value={referencia}
                onChange={(e) => {
                  setReferencia(e.target.value);
                  limpiar("referencia");
                }}
                autoComplete="off"
                aria-invalid={errores.referencia ? true : undefined}
                className={CAMPO}
              />
            </Campo>
          </div>

          <Campo id="registrar-pago-nota" etiqueta={t.nota} error={errores.nota}>
            <input
              id="registrar-pago-nota"
              value={nota}
              onChange={(e) => {
                setNota(e.target.value);
                limpiar("nota");
              }}
              autoComplete="off"
              aria-invalid={errores.nota ? true : undefined}
              className={CAMPO}
            />
          </Campo>

          <div className="grid gap-1 rounded-lg border border-border bg-muted/50 px-3 py-2.5">
            <label className="flex items-start gap-2 text-[13px] font-medium text-foreground">
              <input
                type="checkbox"
                data-testid="registrar-pago-extender"
                checked={extender}
                disabled={!puedeExtender}
                onChange={(e) => setExtenderElegido(e.target.checked)}
                className="mt-0.5 size-4"
              />
              <span>{t.extender}</span>
            </label>
            <p data-testid="registrar-pago-extender-nota" className="pl-6 text-[12px] text-muted-foreground">
              {notaDeExtension}
            </p>
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
          <button type="button" disabled={enviando} onClick={confirmar} data-testid="registrar-pago-confirmar" className={BOTON_PRIMARIO}>
            <BanknoteIcon className="size-4" />
            {enviando ? t.enviando : t.confirmar}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/** Un campo con su etiqueta, y debajo su error o, si no hay, su ayuda. */
function Campo({ id, etiqueta, error, ayuda, children }: { id: string; etiqueta: string; error?: string; ayuda?: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={ETIQUETA_CAMPO}>
        {etiqueta}
      </label>
      {children}
      {error ? <span className="text-[12px] text-destructive">{error}</span> : ayuda ? <span className={AYUDA_CAMPO}>{ayuda}</span> : null}
    </div>
  );
}
