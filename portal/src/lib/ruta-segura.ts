/**
 * Valida que `next` sea una ruta interna relativa antes de usarla como destino de
 * redirección tras el login: evita que un enlace con `?next=https://evil.com` (o
 * `//evil.com`, `/\evil.com`) mande al usuario ya autenticado a un sitio externo.
 */
export function rutaSegura(next: string | null, porDefecto = "/comprobantes"): string {
  if (!next) return porDefecto;
  if (!next.startsWith("/")) return porDefecto;
  if (next.startsWith("//") || next.startsWith("/\\")) return porDefecto;
  return next;
}
