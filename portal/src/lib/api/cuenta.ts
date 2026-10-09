import { backendFetch } from "./client";
import type { PlanDeCuentaAdmin } from "./admin-plan-de-cuenta";

/**
 * Lo que el cliente ve de su cuenta (`GET /v1/cuenta`, C1/C7): su nombre, su plan (la misma forma que el backoffice) y lo consumido este mes contra el tope.
 * `consumo.maximo` falta si el plan no tiene tope de documentos.
 */
export type MiCuenta = {
  nombre: string;
  plan: PlanDeCuentaAdmin;
  consumo: { mes: string; documentos: number; maximo?: number };
};

export function obtenerMiCuenta(access: string) {
  return backendFetch<MiCuenta>("/v1/cuenta", { headers: { Authorization: `Bearer ${access}` } });
}
