// @vitest-environment node
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";

const PORTAL = resolve(__dirname, "..");
const fetchOriginal = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = fetchOriginal;
  vi.unstubAllEnvs();
});

/**
 * Con la carpeta `src/`, Next solo toma `src/instrumentation.ts`: en la raíz del portal el build de producción lo dejaba fuera sin avisar, y con él la
 * corrección de la fuga de memoria y la comprobación de soporte (#250). Estas pruebas fallan si el archivo vuelve a la raíz o si register() deja de aislar fetch.
 */
describe("instrumentation", () => {
  it("vive en src/, donde Next lo incluye en el build, y no hay otro en la raíz del portal", () => {
    expect(existsSync(resolve(PORTAL, "src/instrumentation.ts"))).toBe(true);
    expect(existsSync(resolve(PORTAL, "instrumentation.ts"))).toBe(false);
  });

  it("register() en Node deja el fetch de red fuera de la petición (fuga de memoria bajo carga)", async () => {
    vi.stubEnv("NEXT_RUNTIME", "nodejs");
    globalThis.fetch = (async () => new Response("ok")) as typeof fetch;
    const { register } = await import("./instrumentation");

    await register();

    expect((globalThis.fetch as unknown as Record<symbol, unknown>)[Symbol.for("khipu.fetchFueraDeLaPeticion")]).toBe(true);
  });
});
