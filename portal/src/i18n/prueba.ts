/**
 * Solo para Vitest (#25): `vitest.setup.ts` reemplaza los hooks y funciones de next-intl por traductores reales (`createTranslator`,
 * el mismo formateador ICU que usa la app) sobre los mensajes del locale de prueba, para no tener que envolver cada `render` en un
 * `NextIntlClientProvider` ni simular `next/headers` en los Server Components. Por defecto el locale es `es`; una prueba que quiera
 * ver la pantalla en inglés llama a `fijarLocaleDePrueba("en")` (se vuelve a `es` después de cada prueba).
 */
// De `use-intl/core` (el núcleo que usa next-intl, misma versión) y no de "next-intl": ese módulo lo reemplaza vitest.setup.ts y
// importarlo desde acá cerraría un ciclo entre el mock y este archivo.
import { createTranslator } from "use-intl/core";
import en from "@/messages/en.json";
import es from "@/messages/es.json";
import { LOCALE_POR_DEFECTO, type Locale, ZONA_HORARIA } from "./config";

const MENSAJES: Record<Locale, typeof es> = { es, en };

let actual: Locale = LOCALE_POR_DEFECTO;

export function fijarLocaleDePrueba(locale: Locale): void {
  actual = locale;
}

export function localeDePrueba(): Locale {
  return actual;
}

export function mensajesDePrueba(): typeof es {
  return MENSAJES[actual];
}

const cache = new Map<string, unknown>();

/** Un traductor por (locale, namespace), siempre el mismo objeto: si cambiara en cada render, un `useEffect([t])` no pararía. */
export function traductorDePrueba(namespace?: string) {
  const clave = `${actual}:${namespace ?? ""}`;
  if (!cache.has(clave)) {
    cache.set(
      clave,
      createTranslator({ locale: actual, messages: MENSAJES[actual], timeZone: ZONA_HORARIA, namespace: namespace as never }),
    );
  }
  return cache.get(clave);
}
