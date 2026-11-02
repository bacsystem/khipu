import { backendFetch } from "./client";

/**
 * Qué hacer con las cuentas dadas de baja (#201) en un listado del backoffice. Por defecto el backend las oculta, así que «ocultar» no se
 * representa: es la ausencia del filtro. Los nombres son los del backend, en mayúsculas: en minúsculas respondería 400.
 */
export const VISIBILIDADES_DE_BAJAS = ["INCLUIDAS", "SOLO"] as const;
export type VisibilidadDeBajasAdmin = (typeof VISIBILIDADES_DE_BAJAS)[number];

/** Del texto que llega por la URL al filtro: lo que el backend no conoce (o escrito en minúsculas) se descarta en vez de mandarlo. */
export function bajasDesdeUrl(valor: string | undefined): VisibilidadDeBajasAdmin | undefined {
  return VISIBILIDADES_DE_BAJAS.find((v) => v === valor);
}

/** Lo que responde dar de baja o reponer una cuenta (#201). La fecha falta si la cuenta quedó repuesta. */
export type BajaDeCuentaAdmin = { cuenta_id: string; baja_en?: string };

/**
 * Solo desde el servidor: usa el JWT del administrador, que el navegador nunca ve. `origen` es la IP del cliente ya resuelta por el BFF
 * (`cabecerasDeOrigen`, #208) para que la bitácora registre la del administrador y no la del portal.
 */
export function darDeBajaCuenta(access: string, id: string, motivo: string | undefined, origen: Record<string, string> = {}) {
  return backendFetch<BajaDeCuentaAdmin>(`/v1/admin/cuentas/${id}/baja`, {
    method: "POST",
    body: motivo ? { motivo } : undefined,
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}

export function reponerCuenta(access: string, id: string, origen: Record<string, string> = {}) {
  return backendFetch<BajaDeCuentaAdmin>(`/v1/admin/cuentas/${id}/reponer`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, ...origen },
  });
}
