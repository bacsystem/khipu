import type { Locale } from "@/i18n/config";
import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { MiCuenta } from "@/lib/api/cuenta";
import { formatearFecha, ultimoDiaCubierto } from "@/lib/formato";

export type ResumenDePlan = {
  plan: string;
  /** «12 / 300», «3 de 10 en total» con un tope total (#613), o «5000 documentos» sin tope. */
  consumo: string;
  /** El nombre accesible del bloque del menú, que dice si el consumo es del mes o de toda la cuenta. */
  accesible: string;
  /** Para la barra, de 0 a 100; `null` sin tope. */
  porcentaje: number | null;
  tono: "ok" | "aviso" | "error";
  /** Lo que el cliente tiene que saber, si hay algo; el más grave gana. */
  aviso: string | null;
};

/** Desde aquí se avisa que el consumo se acerca al tope: el mismo 80 % que usa el backoffice en Consumo (#193). */
export const UMBRAL_CERCA = 0.8;

/** Lo que el menú dice del plan del cliente (C1): el estado de pago pesa más que el consumo. Los textos, en el idioma de `messages`. */
export function resumenDePlan(cuenta: MiCuenta, messages: Mensajes, locale: Locale): ResumenDePlan {
  const t = messages.resumenDePlan;
  const { documentos, maximo } = cuenta.consumo;
  // #613: un tope total (el plan Gratis) no es «del mes»: se dice «en total» y los avisos no hablan del mes.
  const total = cuenta.consumo.periodo === "TOTAL" && maximo !== undefined;
  const consumo =
    maximo === undefined ? interpolar(t.documentos, { n: documentos }) : total ? interpolar(t.deEnTotal, { n: documentos, maximo }) : `${documentos} / ${maximo}`;
  const accesible = total
    ? interpolar(messages.nav.plan.resumenAccesibleTotal, { plan: cuenta.plan.plan.nombre, n: documentos, maximo })
    : interpolar(messages.nav.plan.resumenAccesible, { plan: cuenta.plan.plan.nombre, consumo });
  const base = { plan: cuenta.plan.plan.nombre, consumo, accesible };
  const porcentaje = maximo === undefined ? null : maximo === 0 ? 100 : Math.min(100, Math.round((documentos / maximo) * 100));

  if (cuenta.plan.estado === "VENCIDA") return { ...base, porcentaje, tono: "error", aviso: t.vencido };
  // `>` y no `>=`: el backend rechaza recién el documento que pasa el tope, y el backoffice usa la misma regla (265-H1).
  if (maximo !== undefined && documentos > maximo) return { ...base, porcentaje, tono: "error", aviso: total ? t.superasteTotal : t.superaste };
  if (maximo !== undefined && documentos === maximo) return { ...base, porcentaje, tono: "aviso", aviso: total ? t.llegasteTotal : t.llegaste };
  if (cuenta.plan.estado === "EN_GRACIA") {
    const hasta = cuenta.plan.hasta_cuando_cubre ? formatearFecha(ultimoDiaCubierto(cuenta.plan.hasta_cuando_cubre), locale) : null;
    return { ...base, porcentaje, tono: "aviso", aviso: hasta ? interpolar(t.pagoVencidoHasta, { fecha: hasta }) : t.pagoVencido };
  }
  if (maximo !== undefined && documentos >= maximo * UMBRAL_CERCA) return { ...base, porcentaje, tono: "aviso", aviso: total ? t.cercaTotal : t.cerca };
  return { ...base, porcentaje, tono: "ok", aviso: null };
}
