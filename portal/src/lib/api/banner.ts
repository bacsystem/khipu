import { backendFetch } from "./client";

/** El aviso de mantenimiento que se muestra ahora a todos los clientes (#199): solo su texto y su vigencia. */
export type BannerVigente = { texto: string; desde: string; hasta: string };

/**
 * El aviso vigente, o `null` si no hay ninguno. **Nunca falla**: el aviso es un extra, y un portal que no carga porque no se pudo leer un aviso de mantenimiento sería peor que
 * no mostrarlo. Es público (sin credenciales): se pide también antes de iniciar sesión.
 */
export async function obtenerBannerVigente(): Promise<BannerVigente | null> {
  try {
    const b = await backendFetch<BannerVigente | null | undefined>("/v1/banner");
    return b && typeof b.texto === "string" && typeof b.hasta === "string" ? b : null;
  } catch {
    return null;
  }
}
