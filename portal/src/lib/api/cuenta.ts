import { backendFetch } from "./client";
import type { PlanDeCuentaAdmin } from "./admin-plan-de-cuenta";
import type { PeriodoDeDocumentos } from "./admin-planes";

/**
 * Lo que el cliente ve de su cuenta (`GET /v1/cuenta`, C1/C7): su nombre, su plan (la misma forma que el backoffice) y lo consumido contra el tope.
 * `consumo.maximo` falta si el plan no tiene tope de documentos; los topes de API keys, usuarios y RUCs están en `plan.plan.limites`. Con `consumo.periodo` `TOTAL`
 * (#613) `documentos` es lo de toda la vida de la cuenta y `maximo` el tope total, que no se renueva. `pruebas` son las de Beta del mes, de todas las empresas, contra
 * la bolsa de la cuenta (las de cada RUC por sus RUC); sin `maximo`, sin tope.
 */
export type MiCuenta = {
  nombre: string;
  plan: PlanDeCuentaAdmin;
  consumo: { mes: string; documentos: number; maximo?: number; periodo: PeriodoDeDocumentos };
  pruebas: { mes: string; usadas: number; maximo?: number };
  /** #498: lo que la cuenta ocupa de lo que su plan limita además de los documentos. Los usuarios incluyen las invitaciones pendientes que no vencieron. */
  uso: { api_keys: number; usuarios: number; rucs: number };
};

export function obtenerMiCuenta(access: string) {
  return backendFetch<MiCuenta>("/v1/cuenta", { headers: { Authorization: `Bearer ${access}` } });
}
