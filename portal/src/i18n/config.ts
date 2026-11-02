/**
 * Idiomas del portal (#25). El locale **no va en la URL** (nada de `/en/...`): se lee de la cookie `khipu_locale` en cada request
 * (`src/i18n/request.ts`). Sin cookie, o con un valor que no está acá, es castellano: no se mira `Accept-Language`, para que el
 * portal no cambie de idioma solo porque el navegador está en inglés (los e2e corren con un Chromium en en-US).
 */
export const LOCALES = ["es", "en"] as const;
export type Locale = (typeof LOCALES)[number];

export const LOCALE_POR_DEFECTO: Locale = "es";
export const COOKIE_LOCALE = "khipu_locale";

/** Todas las horas del portal son de Lima: es la zona del emisor y la de SUNAT, no la del navegador. */
export const ZONA_HORARIA = "America/Lima";

export function esLocale(valor: unknown): valor is Locale {
  return typeof valor === "string" && (LOCALES as readonly string[]).includes(valor);
}

/** El locale de un valor cualquiera (cookie, parámetro), o el de por defecto si no es uno de los soportados. */
export function localeDe(valor: unknown): Locale {
  return esLocale(valor) ? valor : LOCALE_POR_DEFECTO;
}
