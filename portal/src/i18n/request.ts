import { cookies } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import { COOKIE_LOCALE, localeDe, ZONA_HORARIA } from "./config";

/**
 * Configuración de next-intl por request, sin rutas por idioma: el locale sale de la cookie (ver `config.ts`).
 * Server Components la usan con `getTranslations`/`useTranslations` (el catálogo completo); los Client Components reciben solo los
 * namespaces de su segmento por `<TextosDeCliente>` (`textos-de-cliente.tsx`).
 */
export default getRequestConfig(async () => {
  const locale = localeDe((await cookies()).get(COOKIE_LOCALE)?.value);
  return {
    locale,
    timeZone: ZONA_HORARIA,
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
