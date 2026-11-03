import { confiarEnCloudflare, esDeCloudflare } from "./cloudflare";
import { ipDelCliente, normalizar, saltosDeConfianza } from "./ip-cliente";

export interface Origen {
  /** La IP del cliente que se reenvía al backend, o `undefined` si no se puede saber con confianza. */
  ip: string | undefined;
  /** Si la IP salió de `CF-Connecting-IP` porque quien se conectó al borde de Railway fue Cloudflare. */
  deCloudflare: boolean;
}

/**
 * De dónde viene la petición (#208, #594). Primero `X-Forwarded-For` leído desde la derecha (`ipDelCliente`): es quien se conectó
 * al proxy de confianza. Con `TRUSTED_CLOUDFLARE` y esa IP en un rango de Cloudflare, el cliente real está en `CF-Connecting-IP`,
 * que escribe Cloudflare: el borde de Railway reemplaza el `X-Forwarded-For` que trae Cloudflare, así que la IP del cliente no
 * llega por ahí (medido en producción el 2026-11-03).
 *
 * `CF-Connecting-IP` solo se cree si la conexión vino de Cloudflare. Quien entra directo al dominio de Railway del portal puede
 * mandarla inventada, pero su IP no es de Cloudflare y se toma la de la conexión, como sin Cloudflare. Si la conexión vino de
 * Cloudflare y la cabecera falta o no es una IP, no sale ninguna: la IP de Cloudflare no es la del cliente.
 */
export function origenDe(
  entrantes: { get(nombre: string): string | null },
  saltos: number = saltosDeConfianza(),
  cloudflare: boolean = confiarEnCloudflare(),
): Origen {
  const par = ipDelCliente(entrantes.get("x-forwarded-for"), saltos);
  if (!cloudflare || par === undefined || !esDeCloudflare(par)) return { ip: par, deCloudflare: false };
  const ip = normalizar(entrantes.get("cf-connecting-ip") ?? "");
  return { ip, deCloudflare: ip !== undefined };
}

/**
 * Lo que el BFF agrega a una llamada al backend para que éste registre la IP real del cliente en la bitácora (#208).
 * Siempre sale **una sola IP ya resuelta** (ver `origenDe`), nunca la cadena que llegó: así lo que el navegador pone en
 * `X-Forwarded-For` no cruza al backend. Sin saltos de confianza o sin una IP válida no sale nada, y el backend ve la IP del
 * portal, igual que antes.
 *
 * Lo llama quien tiene la petición a mano (rutas del BFF: `req.headers`) y lo pasa a la función de API. No se inyecta dentro de
 * `backendFetch`: varios componentes de cliente importan valores de módulos que importan `client.ts`, y `next/headers` no puede
 * llegar al bundle del navegador (el build de producción lo rechaza).
 */
export function cabecerasDeOrigen(
  entrantes: { get(nombre: string): string | null },
  saltos: number = saltosDeConfianza(),
  cloudflare: boolean = confiarEnCloudflare(),
): Record<string, string> {
  const { ip } = origenDe(entrantes, saltos, cloudflare);
  return ip ? { "X-Forwarded-For": ip } : {};
}
