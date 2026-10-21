import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { MiCuenta } from "@/lib/api/cuenta";
import { formatearFecha, ultimoDiaCubierto } from "@/lib/formato";
import es from "@/messages/es.json";

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

/** Lo que el menú dice del plan del cliente (C1): el estado de pago pesa más que el consumo. Los textos, en el idioma de `messages`. */
export function resumenDePlan(cuenta: MiCuenta, messages: Mensajes = es, locale: Locale = "es"): ResumenDePlan {
  const t = messages.resumenDePlan;
  const { documentos, maximo } = cuenta.consumo;
  const base = { plan: cuenta.plan.plan.nombre };

  const consumo = maximo === undefined ? interpolar(t.documentos, { n: documentos }) : `${documentos} / ${maximo}`;
  const porcentaje = maximo === undefined ? null : maximo === 0 ? 100 : Math.min(100, Math.round((documentos / maximo) * 100));

  if (cuenta.plan.estado === "VENCIDA") return { ...base, consumo, porcentaje, tono: "error", aviso: t.vencido };
  // `>` y no `>=`: el backend rechaza recién el documento que pasa el tope, y el backoffice usa la misma regla (265-H1).
  if (maximo !== undefined && documentos > maximo) return { ...base, consumo, porcentaje, tono: "error", aviso: t.superaste };
  if (maximo !== undefined && documentos === maximo) return { ...base, consumo, porcentaje, tono: "aviso", aviso: t.llegaste };
  if (cuenta.plan.estado === "EN_GRACIA") {
    const hasta = cuenta.plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(cuenta.plan.hasta_cuando_cubre), locale) : null;
    return { ...base, consumo, porcentaje, tono: "aviso", aviso: hasta ? interpolar(t.pagoVencidoHasta, { fecha: hasta }) : t.pagoVencido };
  }
  if (maximo !== undefined && documentos >= maximo * UMBRAL_CERCA) return { ...base, consumo, porcentaje, tono: "aviso", aviso: t.cerca };
  return { ...base, consumo, porcentaje, tono: "ok", aviso: null };
}
