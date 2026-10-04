import { backendFetch } from "./client";

/** `BAJA` (#201): el cliente que se fue. Manda sobre la suspensión: una cuenta de baja que además estaba suspendida es `BAJA`. */
export type EstadoCuentaAdmin = "ACTIVA" | "SUSPENDIDA" | "BAJA";

/** Lo que responde suspender o reactivar una cuenta (#182): el estado en que quedó. La fecha falta si está activa. */
export type EstadoDeCuentaAdmin = { cuenta_id: string; estado: EstadoCuentaAdmin; suspendida_en?: string };

/** Lo más que cabe en el motivo (el backend lo rechaza con 422 `MOTIVO_INVALIDO` si pasa de aquí). */
export const MOTIVO_MAX = 200;

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
export function suspenderCuenta(access: string, id: string, motivo: string | undefined, origen: Record<string, string> = {}) {
  return backendFetch<EstadoDeCuentaAdmin>(`/v1/admin/cuentas/${id}/suspender`, {
    method: "POST",
    body: motivo ? { motivo } : undefined,
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}

export function reactivarCuenta(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<EstadoDeCuentaAdmin>(`/v1/admin/cuentas/${id}/reactivar`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}
