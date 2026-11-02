import { backendFetch } from "./client";
import type { EntornoAdmin } from "./admin-empresas";

export const ENTORNOS_DE_EMPRESA: readonly EntornoAdmin[] = ["BETA", "PRODUCCION"];

/** Lo que responde cambiar el entorno de una empresa (#187): de cuál a cuál pasó. */
export type CambioDeEntornoAdmin = { empresa_id: string; desde: EntornoAdmin; hacia: EntornoAdmin };

/** Lo que responde revocar una API key: cuál y cuándo. Nunca lleva la clave. */
export type ApiKeyRevocadaAdmin = { api_key_id: string; revocada_en: string };

/** Cómo contestó SUNAT: `CONECTADO` con normalidad, `RECHAZADO` con un error definitivo (código y mensaje de SUNAT, tal cual), `SIN_RESPUESTA` sin una respuesta útil. */
export type ResultadoDeConexion = "CONECTADO" | "RECHAZADO" | "SIN_RESPUESTA";
export type ResultadoDeConexionAdmin = { resultado: ResultadoDeConexion; entorno: EntornoAdmin; codigo?: string; mensaje?: string };

/**
 * Solo desde el servidor: usan el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
export function cambiarEntornoEmpresa(access: string, empresaId: string, entorno: EntornoAdmin, origen: Record<string, string> = {}) {
  return backendFetch<CambioDeEntornoAdmin>(`/v1/admin/empresas/${empresaId}/entorno`, {
    method: "POST",
    body: { entorno },
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}

export function revocarApiKeyEmpresa(access: string, empresaId: string, apiKeyId: string, origen: Record<string, string> = {}) {
  return backendFetch<ApiKeyRevocadaAdmin>(`/v1/admin/empresas/${empresaId}/api-keys/${apiKeyId}/revocar`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}

export function probarConexionEmpresa(access: string, empresaId: string, origen: Record<string, string> = {}) {
  return backendFetch<ResultadoDeConexionAdmin>(`/v1/admin/empresas/${empresaId}/prueba-de-conexion`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}
