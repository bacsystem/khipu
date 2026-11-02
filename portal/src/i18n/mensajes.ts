import type es from "@/messages/es.json";

/**
 * La forma de los textos del portal: la de `es.json`, que es la fuente. `en.json` tiene que tener exactamente las mismas claves
 * (lo comprueba `src/messages/paridad.test.ts`).
 *
 * Cómo se leen (#25), siempre con el locale de la sesión:
 *  - Client Component o Server Component no async: `const messages = useMessages();` (next-intl).
 *  - Server Component async, `generateMetadata`, route handler: `const messages = await getMessages();` (next-intl/server).
 *  - Código que no es de React (`lib/`): recibe `messages: Mensajes` (y `locale`) por parámetro, **sin valor por defecto**: una
 *    llamada que lo olvida no compila, en vez de caer a castellano en silencio. Tampoco importa el JSON: cada `import es from
 *    "@/messages/es.json"` en código que llega al navegador mete el catálogo entero en un chunk de cliente.
 * Después se usa como objeto tipado (`messages.auth.login.titulo`): una clave que no existe no compila. Si el texto lleva datos,
 * `{nombre}` en el JSON y `interpolar(texto, { nombre })`; si necesita plurales o formato ICU, `useTranslations`/`getTranslations`.
 */
export type Mensajes = typeof es;

/** Reemplaza cada `{clave}` del texto por su valor. Una clave que no viene en `valores` queda tal cual, visible, para que se note. */
export function interpolar(texto: string, valores: Record<string, string | number>): string {
  return texto.replace(/\{(\w+)\}/g, (todo, clave: string) => (Object.hasOwn(valores, clave) ? String(valores[clave]) : todo));
}
