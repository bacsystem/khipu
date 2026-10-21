import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * Los textos en castellano, fijos. **Código heredado (#25):** no respeta el locale de la sesión. Lo nuevo y lo que se migra lee los
 * textos con `useMessages()`/`getMessages()` (ver `src/i18n/mensajes.ts`); esto queda para lo que todavía no se migró.
 */
export const messages = es;

/** El texto de un código de error de la API en los textos dados, o el genérico si el código no está en `errores`. */
export function mensajeErrorEn(mensajes: Mensajes, codigo: string | null | undefined): string {
  const errores = mensajes.errores as Record<string, string>;
  return codigo && Object.hasOwn(errores, codigo) ? errores[codigo] : errores.generico;
}

/** `mensajeErrorEn` en castellano. En un componente, `useMensajeError()` (src/i18n/use-mensaje-error.ts) respeta el locale. */
export function mensajeError(codigo: string | null | undefined): string {
  return mensajeErrorEn(es, codigo);
}
