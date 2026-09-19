import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

export type Serie = {
  tipo: string;
  serie: string;
  ultimo_numero: number;
  activa: boolean;
  /** Código del establecimiento desde el que emite (`0000` = domicilio fiscal); ausente en backends anteriores. */
  establecimiento?: string;
};

export function listarSeries(access: string, empresaId: string) {
  return backendFetch<Serie[]>("/v1/series", { headers: tenantHeaders(access, empresaId) });
}
