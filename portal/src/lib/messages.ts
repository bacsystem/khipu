import type { Mensajes } from "@/i18n/mensajes";

/**
 * El texto de un código de error de la API en los textos dados, o el genérico si el código no está en `errores`. En un componente,
 * `useMensajeError()` (src/i18n/use-mensaje-error.ts) lo da con el locale de la sesión.
 */
export function mensajeErrorEn(mensajes: Mensajes, codigo: string | null | undefined): string {
  const errores = mensajes.errores as Record<string, string>;
  return codigo && Object.hasOwn(errores, codigo) ? errores[codigo] : errores.generico;
}
