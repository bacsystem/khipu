/**
 * Si una IP es de Cloudflare (#594). Sirve para creer en `CF-Connecting-IP` solo cuando quien se conectó al borde de Railway fue
 * Cloudflare: cualquiera puede mandar esa cabecera entrando directo al dominio `*.up.railway.app` del portal, pero solo Cloudflare
 * se conecta desde estas direcciones. Sin dependencias de Node, como `ip-cliente.ts`.
 *
 * Los rangos son los que Cloudflare publica en https://www.cloudflare.com/ips-v4 y https://www.cloudflare.com/ips-v6, copiados el
 * 2026-11-03. Cambian rara vez; si Cloudflare agrega uno y una petición llega por él, `CF-Connecting-IP` no se cree y la bitácora
 * no registra ninguna IP del cliente (falla segura), hasta actualizar esta lista.
 */
export const RANGOS_CLOUDFLARE: readonly string[] = [
  "173.245.48.0/20",
  "103.21.244.0/22",
  "103.22.200.0/22",
  "103.31.4.0/22",
  "141.101.64.0/18",
  "108.162.192.0/18",
  "190.93.240.0/20",
  "188.114.96.0/20",
  "197.234.240.0/22",
  "198.41.128.0/17",
  "162.158.0.0/15",
  "104.16.0.0/13",
  "104.24.0.0/14",
  "172.64.0.0/13",
  "131.0.72.0/22",
  "2400:cb00::/32",
  "2606:4700::/32",
  "2803:f800::/32",
  "2405:b500::/32",
  "2405:8100::/32",
  "2a06:98c0::/29",
  "2c0f:f248::/32",
];

/** La IP como número y su ancho en bits, o `undefined` si no es una IPv4 o IPv6 escrita sin corchetes ni puerto. */
function comoNumero(ip: string): { valor: bigint; bits: 32 | 128 } | undefined {
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) {
    const octetos = ip.split(".").map(Number);
    if (octetos.some((o) => o > 255)) return undefined;
    return { valor: octetos.reduce((n, o) => (n << 8n) | BigInt(o), 0n), bits: 32 };
  }
  if (!ip.includes(":")) return undefined;
  let canonica: string;
  try {
    // El parser de URL valida y canoniza (también pasa a hexadecimal una IPv4 incrustada al final).
    canonica = new URL(`http://[${ip}]/`).hostname.slice(1, -1);
  } catch {
    return undefined;
  }
  const [izquierda, derecha] = canonica.split("::");
  const grupos = (s: string | undefined) => (s ? s.split(":") : []);
  const izq = grupos(izquierda);
  const der = grupos(derecha);
  const todos = derecha === undefined ? izq : [...izq, ...Array<string>(8 - izq.length - der.length).fill("0"), ...der];
  if (todos.length !== 8) return undefined;
  return { valor: todos.reduce((n, g) => (n << 16n) | BigInt(parseInt(g, 16)), 0n), bits: 128 };
}

function enRango(ip: { valor: bigint; bits: number }, rango: string): boolean {
  const [base, prefijo] = rango.split("/");
  const red = comoNumero(base);
  if (!red || red.bits !== ip.bits) return false;
  const corrimiento = BigInt(ip.bits - Number(prefijo));
  return ip.valor >> corrimiento === red.valor >> corrimiento;
}

/** Si la IP (ya normalizada: sin corchetes ni puerto) cae en un rango publicado de Cloudflare. */
export function esDeCloudflare(ip: string): boolean {
  const numero = comoNumero(ip);
  return numero !== undefined && RANGOS_CLOUDFLARE.some((rango) => enRango(numero, rango));
}

/**
 * `TRUSTED_CLOUDFLARE`: si delante del borde de Railway está el proxy de Cloudflare. Solo `true` (sin importar mayúsculas ni
 * espacios) lo enciende; vacía, ausente o cualquier otro valor deja el comportamiento de antes.
 */
export function confiarEnCloudflare(valor: string | undefined = process.env.TRUSTED_CLOUDFLARE): boolean {
  return valor?.trim().toLowerCase() === "true";
}
