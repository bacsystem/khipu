import type { Usuario } from "./auth";
import { backendFetch } from "./client";

/**
 * La sesión de soporte que abre el backend (#184): un JWT del usuario al que se mira, de **solo lectura** y que vence a los 15 minutos, sin refresh. Solo la
 * recibe el BFF, que lo deja en una cookie `httpOnly`: el JS del navegador del administrador nunca lo ve.
 */
export type ImpersonacionAdmin = { access_token: string; expira_en: string; usuario: Usuario };

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
export function impersonarUsuario(access: string, cuentaId: string, usuarioId: string, origen: Record<string, string> = {}) {
  return backendFetch<ImpersonacionAdmin>(`/v1/admin/cuentas/${cuentaId}/usuarios/${usuarioId}/impersonar`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}
