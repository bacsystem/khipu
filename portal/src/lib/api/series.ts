import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

export type Serie = {
  tipo: string;
  serie: string;
  ultimo_numero: number;
  activa: boolean;
};

export function listarSeries(access: string, empresaId: string) {
  return backendFetch<Serie[]>("/v1/series", { headers: tenantHeaders(access, empresaId) });
}
