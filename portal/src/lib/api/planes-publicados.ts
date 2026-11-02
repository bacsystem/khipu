import type { LimitesAdmin } from "./admin-planes";
import { backendFetch } from "./client";

/** Un plan tal como se publica (H20): nombre, precio y límites de hoy. Sin id ni cuentas: es público. */
export type PlanPublicado = { nombre: string; precio_mensual: number; limites: LimitesAdmin };

/**
 * Los planes de la página de precios, del más barato al más caro, o `null` si no se pudieron leer. **Nunca falla**: la portada tiene su propia lista de respaldo, y
 * una portada que no carga porque el backend no respondió sería peor que mostrar precios de respaldo. Público, sin credenciales.
 */
export async function obtenerPlanesPublicados(): Promise<PlanPublicado[] | null> {
  try {
    const planes = await backendFetch<PlanPublicado[] | null | undefined>("/v1/planes");
    return Array.isArray(planes) && planes.length > 0 ? planes : null;
  } catch {
    return null;
  }
}
