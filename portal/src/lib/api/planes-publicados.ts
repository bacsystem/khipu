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

/**
 * El plan de pago que eligió en la página de precios (#581), por la clave de `/registro?plan=<clave>`: el nombre en minúsculas, como la portada empareja sus
 * tarjetas. `null` si no hay clave, si no es un plan publicado o si es gratis (con ese nace toda cuenta: no hay nada que pedir). Un enlace viejo no rompe nada.
 */
export function planDePagoElegido(publicados: PlanPublicado[] | null, clave: string | undefined): PlanPublicado | null {
  const buscado = clave?.trim().toLowerCase();
  if (!buscado || !publicados) return null;
  return publicados.find((p) => p.nombre.toLowerCase() === buscado && p.precio_mensual > 0) ?? null;
}
