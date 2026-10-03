/**
 * IP real del cliente a partir de `X-Forwarded-For` (#208). Sin dependencias de Node (solo regex y `URL`), para que sirva igual en
 * cualquier runtime.
 *
 * Cómo se lee es lo que importa. Un navegador puede mandar cualquier `X-Forwarded-For`, y los proxies de confianza **agregan a la
 * derecha**: la única parte fiable de la cadena es la que escribieron ellos. Por eso se cuenta desde la derecha la cantidad de
 * saltos de confianza (`TRUSTED_PROXY_HOPS`) y lo de más a la izquierda nunca se mira. Con 0 saltos no hay ninguna entrada de
 * confianza, así que no se devuelve nada: es el valor por defecto y no cambia lo que hoy ve el backend.
 *
 * Next tampoco sirve de fuente por sí solo: `base-server` hace `x-forwarded-for ??= socket.remoteAddress`, es decir, solo rellena
 * la cabecera si falta; si el navegador mandó una falsa y no hay proxy, la conserva y pierde la IP de la conexión.
 */

const IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/;

function esIpv4(s: string): boolean {
  return IPV4.test(s) && s.split(".").every((octeto) => Number(octeto) <= 255);
}

/** IPv6 válida en su forma canónica (minúsculas, comprimida), o `undefined`. Se apoya en el parser de URL, presente en edge y en Node. */
function ipv6Canonica(s: string): string | undefined {
  try {
    const host = new URL(`http://[${s}]/`).hostname; // «[2001:db8::1]»
    return host.startsWith("[") && host.endsWith("]") ? host.slice(1, -1) : undefined;
  } catch {
    return undefined;
  }
}

/** Quita corchetes y puerto y deja la IP en su forma canónica; `undefined` si no es una IP. */
function normalizar(entrada: string): string | undefined {
  let s = entrada.trim();
  const conCorchetes = /^\[(.+)\](?::\d+)?$/.exec(s);
  if (conCorchetes) s = conCorchetes[1];
  else if (/^\d{1,3}(\.\d{1,3}){3}:\d+$/.test(s)) s = s.slice(0, s.lastIndexOf(":"));

  if (esIpv4(s)) return s;

  const v4Mapeada = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i.exec(s);
  if (v4Mapeada) return esIpv4(v4Mapeada[1]) ? v4Mapeada[1] : undefined;

  return s.includes(":") ? ipv6Canonica(s) : undefined;
}

export function ipDelCliente(cadena: string | null | undefined, saltos: number): string | undefined {
  if (!cadena || !Number.isInteger(saltos) || saltos < 1) return undefined;
  // Solo cuenta lo que escribieron los proxies de confianza: las últimas `saltos` entradas. Lo de la izquierda es del navegador y
  // no se mira, ni siquiera para descartarlo: si un `,` suelto bastara para anular la lectura, cualquiera podría borrar su IP.
  const confiables = cadena.split(",").map((e) => e.trim()).slice(-saltos);
  // Una entrada vacía ahí desplazaría el recuento y se terminaría leyendo la IP de un proxy como si fuera la del cliente.
  if (confiables.length < saltos || confiables.some((e) => e === "")) return undefined;
  return normalizar(confiables[0]);
}

/** `TRUSTED_PROXY_HOPS`: cuántos proxies de confianza hay delante del portal. Solo un entero no negativo; cualquier otra cosa vale 0. */
export function saltosDeConfianza(valor: string | undefined = process.env.TRUSTED_PROXY_HOPS): number {
  const limpio = valor?.trim() ?? "";
  return /^\d+$/.test(limpio) ? Number(limpio) : 0;
}
