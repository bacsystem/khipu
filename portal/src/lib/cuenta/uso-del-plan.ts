import { interpolar, type Mensajes } from "@/i18n/mensajes";
import type { MiCuenta } from "@/lib/api/cuenta";
import { UMBRAL_CERCA } from "./resumen-de-plan";

export type RecursoDelPlan = "apiKeys" | "usuarios" | "rucs";

export type UsoDeRecurso = {
  clave: RecursoDelPlan;
  usados: number;
  /** `null` si el plan no tiene tope. */
  maximo: number | null;
  /** «1 / 1», o «3 · sin tope». */
  texto: string;
  /** Para la barra, de 0 a 100; `null` sin tope. */
  porcentaje: number | null;
  tono: "ok" | "aviso" | "error";
  /** Lo que el cliente tiene que saber de este recurso, si hay algo. */
  aviso: string | null;
};

/**
 * Lo que la cuenta ocupa de las API keys, los usuarios y los RUC de su plan (#498), con la misma regla que el backend: el N-ésimo cabe y el N+1 no, así que `usados === maximo`
 * es «llegaste» (no se puede sumar más) y solo `usados > maximo` es «te pasaste» (una cuenta a la que le bajaron el plan: no se le quitó nada). Desde el 80 % se avisa, como con los documentos.
 */
export function usoDelPlan(cuenta: MiCuenta, messages: Mensajes): UsoDeRecurso[] {
  const t = messages.cuenta.plan;
  const limites = cuenta.plan.plan.limites;
  const topes: Record<RecursoDelPlan, number | null> = {
    apiKeys: limites.api_keys.ilimitado ? null : (limites.api_keys.maximo ?? null),
    usuarios: limites.usuarios.ilimitado ? null : (limites.usuarios.maximo ?? null),
    rucs: limites.rucs,
  };
  const usados: Record<RecursoDelPlan, number> = { apiKeys: cuenta.uso.api_keys, usuarios: cuenta.uso.usuarios, rucs: cuenta.uso.rucs };
  return (["apiKeys", "usuarios", "rucs"] as const).map((clave) => {
    const n = usados[clave];
    const maximo = topes[clave];
    const recurso = t.usoDe[clave];
    if (maximo === null) return { clave, usados: n, maximo, texto: interpolar(t.usoSinTope, { n }), porcentaje: null, tono: "ok", aviso: null };
    const porcentaje = Math.min(100, Math.round((n / maximo) * 100));
    const texto = `${n} / ${maximo}`;
    if (n > maximo) return { clave, usados: n, maximo, texto, porcentaje, tono: "error", aviso: interpolar(t.usoAvisos.excedido, { recurso }) };
    if (n === maximo) return { clave, usados: n, maximo, texto, porcentaje, tono: "aviso", aviso: interpolar(t.usoAvisos.lleno, { recurso }) };
    if (n >= maximo * UMBRAL_CERCA) return { clave, usados: n, maximo, texto, porcentaje, tono: "aviso", aviso: interpolar(t.usoAvisos.cerca, { recurso }) };
    return { clave, usados: n, maximo, texto, porcentaje, tono: "ok", aviso: null };
  });
}
