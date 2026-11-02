import { backendFetch } from "./client";

/** A quién se le mandó el correo de acceso (#183). A propósito, sin el enlace ni su token: solo el usuario puede usarlo. */
export type DestinatarioAdmin = { usuario_id: string; correo: string };

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
export function enviarRestablecimiento(access: string, cuentaId: string, usuarioId: string, origen: Record<string, string> = {}) {
  return backendFetch<DestinatarioAdmin>(`/v1/admin/cuentas/${cuentaId}/usuarios/${usuarioId}/restablecimiento`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}

export function reenviarVerificacion(access: string, cuentaId: string, usuarioId: string, origen: Record<string, string> = {}) {
  return backendFetch<DestinatarioAdmin>(`/v1/admin/cuentas/${cuentaId}/usuarios/${usuarioId}/verificacion`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}
