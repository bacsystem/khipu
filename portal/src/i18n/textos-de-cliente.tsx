import type { ReactNode } from "react";
import { NextIntlClientProvider } from "next-intl";
import { getMessages } from "next-intl/server";
import type { Mensajes } from "./mensajes";

type Namespace = keyof Mensajes;

/**
 * Qué textos viajan al navegador en cada parte del portal (H2, #25). El `NextIntlClientProvider` serializa en el HTML todo lo que
 * recibe; sin `messages` mandaría el catálogo entero (es.json pesa ~340 KB, `developers` sola ~120 KB y `admin` ~50 KB) en cada
 * página, incluidas la portada y el login. Los Server Components no dependen de esto: leen el catálogo completo con
 * `getMessages()`/`useMessages()` en el servidor; esta lista es solo lo que usan los Client Components.
 *
 * Un provider anidado reemplaza al de arriba (no los suma), por eso cada segmento repite la base. `textos-de-cliente.test.ts`
 * recorre los imports desde cada página y falla si un Client Component lee un namespace que su segmento no envía: si agregas uno,
 * súmalo acá en el segmento donde se usa.
 */
const BASE = [
  "aceptarTerminos",
  "auth",
  "cuentaSuspendida",
  "errores",
  "legal",
  "onboarding",
  "paginaDeError",
  "roles",
  "soporte",
  "validacion",
] as const satisfies readonly Namespace[];

export const TEXTOS_DE_CLIENTE = {
  /** Layout raíz: las páginas públicas (login, registro, recuperación, onboarding…), la página de error y lo que envuelve a todo. */
  base: BASE,
  /** La portada (`/`). */
  landing: [...BASE, "landing"],
  /** El panel del cliente, `(privado)`. */
  privado: [
    ...BASE,
    "apiKeys",
    "app",
    "clientes",
    "comprobantes",
    "comun",
    "documentos",
    "empresa",
    "equipo",
    "establecimientos",
    "nav",
    "productos",
    "referenciaTecnica",
    "resumenDePlan",
    "series",
    "webhooks",
  ],
  /** El backoffice, `/admin` (su login incluido). */
  admin: [...BASE, "admin", "apiKeys", "app", "banner", "comun", "documentos"],
  /** La documentación pública, `/developers`. */
  developers: [...BASE, "developers"],
} as const satisfies Record<string, readonly Namespace[]>;

export type SegmentoDeTextos = keyof typeof TEXTOS_DE_CLIENTE;

/** Los namespaces pedidos de `messages`, nada más. */
export function elegirTextos(messages: Mensajes, namespaces: readonly Namespace[]): Partial<Mensajes> {
  return Object.fromEntries(namespaces.filter((ns) => Object.hasOwn(messages, ns)).map((ns) => [ns, messages[ns]]));
}

/** Provee a los Client Components de `children` los textos de su segmento, en el locale de la sesión. */
export async function TextosDeCliente({ segmento, children }: { segmento: SegmentoDeTextos; children: ReactNode }) {
  const messages = await getMessages();
  return <NextIntlClientProvider messages={elegirTextos(messages, TEXTOS_DE_CLIENTE[segmento])}>{children}</NextIntlClientProvider>;
}
