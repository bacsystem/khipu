/**
 * Valida que `next` sea una ruta interna relativa antes de usarla como destino de
 * redirección tras el login: evita que un enlace con `?next=https://evil.com` (o
 * `//evil.com`, `/\evil.com`) mande al usuario ya autenticado a un sitio externo.
 */
export function rutaSegura(next: string | null, porDefecto = "/comprobantes"): string {
  if (!next) return porDefecto;
  // El parser de URL de los navegadores descarta tabs y saltos de línea *antes* de resolver el
  // destino, así que "/\n/evil.com" pasa cualquier chequeo de prefijo y llega a navegar a
  // "//evil.com", que es protocolo-relativo y sale del portal. Se rechaza todo el rango de
  // control, no solo esos tres caracteres, porque qué se descarta depende del navegador.
  if (/[\u0000-\u001f\u007f]/.test(next)) return porDefecto;
  // Una barra invertida en cualquier posición: los navegadores la normalizan a "/", así que
  // "/\evil.com" equivale a "//evil.com".
  if (next.includes("\\")) return porDefecto;
  if (!next.startsWith("/")) return porDefecto;
  if (next.startsWith("//")) return porDefecto;
  return next;
}
