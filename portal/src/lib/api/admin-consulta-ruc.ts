import { backendFetch } from "./client";
import type { ContribuyenteSunat } from "./consulta-ruc";

/**
 * El padrón de RUC desde el backoffice (#575), para el alta asistida: todavía no hay empresa a la que contarle el cupo, así que el backend lo cuenta por
 * administrador. Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `ruc` ya viene validado (11 dígitos).
 */
export function consultarRucComoAdmin(access: string, ruc: string) {
  return backendFetch<ContribuyenteSunat>(`/v1/admin/consultas/ruc/${ruc}`, { headers: { Authorization: `Bearer ${access}` } });
}
