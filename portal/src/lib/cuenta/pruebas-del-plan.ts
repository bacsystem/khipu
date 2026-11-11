import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { MiCuenta } from "@/lib/api/cuenta";
import { UMBRAL_CERCA } from "./resumen-de-plan";

export type ResumenDePruebas = {
  /** «40 / 3,000», o «40 · sin tope». */
  texto: string;
  /** Para la barra, de 0 a 100; `null` sin tope. */
  porcentaje: number | null;
  tono: "ok" | "aviso";
  aviso: string | null;
  /** De dónde sale el tope: las pruebas por RUC del plan. */
  nota: string;
};

const miles = (n: number) => n.toLocaleString("en-US");

/**
 * Las pruebas en Beta del mes contra la bolsa de la cuenta (#613). Como con los documentos, el backend rechaza recién la prueba que pasa el tope, así que `usadas === maximo`
 * es «llegaste»: la siguiente ya no entra y hay que esperar al mes siguiente o cambiar de plan.
 */
export function resumenDePruebas(cuenta: MiCuenta, messages: Mensajes): ResumenDePruebas {
  const t = messages.cuenta.plan;
  const { usadas, maximo } = cuenta.pruebas;
  const porRuc = cuenta.plan.plan.limites.pruebas_beta_por_ruc;
  if (maximo === undefined) return { texto: interpolar(t.usoSinTope, { n: miles(usadas) }), porcentaje: null, tono: "ok", aviso: null, nota: t.pruebasNotaSinTope };
  const nota = interpolar(t.pruebasNota, { porRuc: porRuc.maximo === undefined ? "—" : miles(porRuc.maximo) });
  const texto = `${miles(usadas)} / ${miles(maximo)}`;
  const porcentaje = maximo === 0 ? 100 : Math.min(100, Math.round((usadas / maximo) * 100));
  if (usadas >= maximo) return { texto, porcentaje, tono: "aviso", aviso: interpolar(t.pruebasAvisos.lleno, { n: miles(maximo) }), nota };
  if (usadas >= maximo * UMBRAL_CERCA) return { texto, porcentaje, tono: "aviso", aviso: t.pruebasAvisos.cerca, nota };
  return { texto, porcentaje, tono: "ok", aviso: null, nota };
}
