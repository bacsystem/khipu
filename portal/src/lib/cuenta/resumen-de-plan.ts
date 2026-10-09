import type { MiCuenta } from "@/lib/api/cuenta";
import { formatearFecha, ultimoDiaCubierto } from "@/lib/formato";

export type ResumenDePlan = {
  plan: string;
  /** «12 / 300», o «5000 documentos» sin tope. */
  consumo: string;
  /** Para la barra, de 0 a 100; `null` sin tope. */
  porcentaje: number | null;
  tono: "ok" | "aviso" | "error";
  /** Lo que el cliente tiene que saber, si hay algo; el más grave gana. */
  aviso: string | null;
};

/** Desde aquí se avisa que el consumo se acerca al tope: el mismo 80 % que usa el backoffice en Consumo (#193). */
const UMBRAL_CERCA = 0.8;

/** Lo que el menú dice del plan del cliente (C1): el estado de pago pesa más que el consumo. */
export function resumenDePlan(cuenta: MiCuenta): ResumenDePlan {
  const { documentos, maximo } = cuenta.consumo;
  const base = { plan: cuenta.plan.plan.nombre };

  const consumo = maximo === undefined ? `${documentos} documentos` : `${documentos} / ${maximo}`;
  const porcentaje = maximo === undefined ? null : maximo === 0 ? 100 : Math.min(100, Math.round((documentos / maximo) * 100));

  if (cuenta.plan.estado === "VENCIDA") return { ...base, consumo, porcentaje, tono: "error", aviso: "Tu plan venció: escríbenos para renovarlo" };
  // `>` y no `>=`: el backend rechaza recién el documento que pasa el tope, y el backoffice usa la misma regla (265-H1).
  if (maximo !== undefined && documentos > maximo) return { ...base, consumo, porcentaje, tono: "error", aviso: "Superaste el tope de documentos del mes" };
  if (maximo !== undefined && documentos === maximo) return { ...base, consumo, porcentaje, tono: "aviso", aviso: "Llegaste al tope de documentos del mes" };
  if (cuenta.plan.estado === "EN_GRACIA") {
    const hasta = cuenta.plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(cuenta.plan.hasta_cuando_cubre)) : null;
    return { ...base, consumo, porcentaje, tono: "aviso", aviso: hasta ? `Pago vencido: se sirve hasta el ${hasta}` : "Pago vencido" };
  }
  if (maximo !== undefined && documentos >= maximo * UMBRAL_CERCA) return { ...base, consumo, porcentaje, tono: "aviso", aviso: "Cerca del tope de documentos del mes" };
  return { ...base, consumo, porcentaje, tono: "ok", aviso: null };
}
