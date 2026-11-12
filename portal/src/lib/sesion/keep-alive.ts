import { ESPERA_MAXIMA_LECTURA_MS } from "@/lib/api/browser";
import type { ResultadoKeepAlive } from "./inactividad";

const HTTP_NO_AUTORIZADO = 401;
const HTTP_PROHIBIDO = 403;

/**
 * El keep-alive (#637): `POST /api/auth/sesion`, la ruta del BFF que renueva la sesión si hace falta. Se lee por el status porque eso es todo lo que decide: 401 es una sesión muerta, 403 una cuenta
 * suspendida (la única causa de un 403 en esa ruta), y cualquier otra cosa —sin red, un 5xx, una espera vencida— no dice nada de la sesión, así que no se navega.
 */
export async function pedirKeepAlive(): Promise<ResultadoKeepAlive> {
  try {
    const res = await fetch("/api/auth/sesion", { method: "POST", signal: AbortSignal.timeout(ESPERA_MAXIMA_LECTURA_MS) });
    if (res.ok) return "vivo";
    if (res.status === HTTP_NO_AUTORIZADO) return "muerta";
    if (res.status === HTTP_PROHIBIDO) return "suspendida";
    return "fallo";
  } catch {
    return "fallo";
  }
}
