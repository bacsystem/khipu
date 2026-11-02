import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { fijarLocaleDePrueba } from "@/i18n/prueba";

// #25: next-intl sin provider ni request de Next. Los traductores son los reales de next-intl sobre src/messages/<locale>.json;
// solo cambia de dónde salen el locale y los mensajes (ver src/i18n/prueba.ts).
vi.mock("next-intl", async (importOriginal) => {
  const real = await importOriginal<typeof import("next-intl")>();
  const prueba = await import("@/i18n/prueba");
  return {
    ...real,
    useTranslations: (namespace?: string) => prueba.traductorDePrueba(namespace),
    useLocale: () => prueba.localeDePrueba(),
    useMessages: () => prueba.mensajesDePrueba(),
  };
});

vi.mock("next-intl/server", async () => {
  const prueba = await import("@/i18n/prueba");
  return {
    getTranslations: async (arg?: string | { namespace?: string }) =>
      prueba.traductorDePrueba(typeof arg === "string" ? arg : arg?.namespace),
    getLocale: async () => prueba.localeDePrueba(),
    getMessages: async () => prueba.mensajesDePrueba(),
    getRequestConfig: (f: unknown) => f,
  };
});

// H2: `<TextosDeCliente>` es un Server Component async (lee getMessages) y Testing Library no renderiza componentes async. En las
// pruebas los hooks ya leen el catálogo de prueba completo, así que basta con dejar pasar los hijos; lo que elige cada segmento lo
// prueba `src/i18n/textos-de-cliente.test.ts` con las funciones reales.
vi.mock("@/i18n/textos-de-cliente", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/i18n/textos-de-cliente")>()),
  TextosDeCliente: ({ children }: { children: React.ReactNode }) => children,
}));

afterEach(() => fijarLocaleDePrueba("es"));
