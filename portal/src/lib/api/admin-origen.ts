import { backendFetch } from "./client";

/** Lo que el backend resolvió como IP de origen para esta llamada: la misma que la bitácora de auditoría registraría (#208). */
export type OrigenBackend = { ip: string };

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por
 * el BFF (`cabecerasDeOrigen`): sin ella, `ip` sería la del propio portal y no habría nada que calibrar.
 */
export function origenDelBackend(access: string, origen: Record<string, string> = {}) {
  return backendFetch<OrigenBackend>("/v1/admin/origen", { headers: { Authorization: `Bearer ${access}`, ...origen } });
}
