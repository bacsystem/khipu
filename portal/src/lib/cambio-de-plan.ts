import { GRACIA_MAX_DIAS, type CuerpoDeCambioDePlan } from "@/lib/api/admin-plan-de-cuenta";
import { venceDesdeFechaDeLima } from "@/lib/formato";
import { messages } from "@/lib/messages";

const t = messages.admin.planDeCuenta.dialogo.errores;

/** Lo que el administrador tiene escrito en el formulario de cambio de plan. `precioDelPlan`: el del plan elegido (nulo mientras no haya elegido). */
export type ValoresDeCambioDePlan = { planId: string; precioDelPlan?: number; pagadoHasta: string; gracia: string };

export type ErroresDeCambioDePlan = Partial<Record<"planId" | "pagadoHasta" | "gracia", string>>;

/** `YYYY-MM-DD` de un día que existe: `2026-02-30` no. */
function fechaValida(texto: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(texto)) return false;
  const [a, m, d] = texto.split("-").map(Number);
  const f = new Date(Date.UTC(a, m - 1, d));
  // Un día que no existe (30 de febrero, día 00) desborda al mes vecino: con comparar el año y el mes alcanza.
  return f.getUTCFullYear() === a && f.getUTCMonth() === m - 1;
}

/**
 * Valida lo evidente antes de enviar y arma el cuerpo del cambio de plan (#191). «Pagado hasta» es el último día cubierto, inclusive; el vencimiento que se manda es
 * la medianoche de Lima del día siguiente (el vencimiento es exclusivo). Un plan de pago exige la fecha; uno gratis no, pero si la trae también tiene que ser de
 * hoy en adelante. El backend vuelve a decidir todo: esto evita ir y volver por un campo vacío. `hoy` es la fecha de Lima, `YYYY-MM-DD`.
 */
export function validarCambioDePlan(v: ValoresDeCambioDePlan, hoy: string): { errores: ErroresDeCambioDePlan } | { cuerpo: CuerpoDeCambioDePlan } {
  const errores: ErroresDeCambioDePlan = {};
  if (!v.planId) errores.planId = t.planRequerido;

  const fecha = v.pagadoHasta.trim();
  if (v.planId) {
    if (!fecha) {
      if ((v.precioDelPlan ?? 0) > 0) errores.pagadoHasta = t.vencimientoRequerido;
    } else if (!fechaValida(fecha) || fecha < hoy) {
      errores.pagadoHasta = t.vencimientoPasado;
    }
  }

  const graciaTexto = v.gracia.trim();
  const gracia = graciaTexto === "" ? 0 : /^\d+$/.test(graciaTexto) ? Number(graciaTexto) : NaN;
  if (Number.isNaN(gracia) || gracia > GRACIA_MAX_DIAS) errores.gracia = t.graciaInvalida;

  if (Object.keys(errores).length > 0) return { errores };
  return { cuerpo: { plan_id: v.planId, ...(fecha ? { vence_en: venceDesdeFechaDeLima(fecha) } : {}), dias_de_gracia: gracia } };
}
