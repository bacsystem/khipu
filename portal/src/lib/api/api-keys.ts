import { backendFetch } from "./client";
import { tenantHeaders } from "./tenant";

/** Vista de una API key en listados: el secreto solo se entrega una vez, al crearla. */
export type ApiKeyResumen = {
  id: string;
  prefijo: string;
  activa: boolean;
  creada_en: string;
  revocada_en?: string | null;
};

export function listarApiKeys(access: string, empresaId: string) {
  return backendFetch<ApiKeyResumen[]>("/v1/empresa/api-keys", { headers: tenantHeaders(access, empresaId) });
}
