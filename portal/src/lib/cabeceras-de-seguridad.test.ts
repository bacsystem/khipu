import { afterEach, describe, expect, it, vi } from "vitest";
import { cabecerasDeSeguridad } from "./cabeceras-de-seguridad";

const comoMapa = (produccion: boolean) => Object.fromEntries(cabecerasDeSeguridad(produccion).map((c) => [c.key, c.value]));

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("cabecerasDeSeguridad (#602)", () => {
  it("solo el propio portal puede enmarcar sus páginas: la vista previa del PDF es un iframe propio, un sitio ajeno no", () => {
    const c = comoMapa(true);
    expect(c["X-Frame-Options"]).toBe("SAMEORIGIN");
    expect(c["Content-Security-Policy"]).toBe("frame-ancestors 'self'");
  });

  it("la CSP no restringe scripts ni estilos: solo el enmarcado", () => {
    expect(comoMapa(true)["Content-Security-Policy"]).not.toMatch(/script-src|default-src|style-src/);
  });

  it("nosniff, Referrer-Policy y Permissions-Policy siempre", () => {
    for (const produccion of [true, false]) {
      const c = comoMapa(produccion);
      expect(c["X-Content-Type-Options"]).toBe("nosniff");
      expect(c["Referrer-Policy"]).toBe("strict-origin-when-cross-origin");
      expect(c["Permissions-Policy"]).toContain("camera=()");
    }
  });

  it("HSTS solo en producción, sin includeSubDomains ni preload", () => {
    expect(comoMapa(true)["Strict-Transport-Security"]).toBe("max-age=31536000");
    expect(comoMapa(false)["Strict-Transport-Security"]).toBeUndefined();
  });
});

describe("next.config.ts", () => {
  it("aplica las cabeceras a todas las rutas y no anuncia x-powered-by", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const { default: config } = await import("../../next.config");

    expect(config.poweredByHeader).toBe(false);
    const reglas = await config.headers!();
    expect(reglas).toEqual([{ source: "/:path*", headers: cabecerasDeSeguridad(true) }]);
  });
});
