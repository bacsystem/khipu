import { ipDelCliente, saltosDeConfianza } from "./ip-cliente";

/**
 * Lo que el BFF agrega a una llamada al backend para que éste registre la IP real del cliente en la bitácora (#208).
 * Siempre sale **una sola IP ya resuelta** (ver `ipDelCliente`), nunca la cadena que llegó: así lo que el navegador pone en
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
): Record<string, string> {
  const ip = ipDelCliente(entrantes.get("x-forwarded-for"), saltos);
  return ip ? { "X-Forwarded-For": ip } : {};
}
