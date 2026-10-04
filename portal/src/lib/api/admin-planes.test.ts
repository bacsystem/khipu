import { afterEach, describe, expect, it, vi } from "vitest";
import {
  activarPlan,
  crearPlan,
  desactivarPlan,
  editarPlan,
  eliminarPlan,
  cambiosDeLimites,
  limiteEnPalabras,
  listarPlanesAdmin,
  precioEnSoles,
  retencionEnPalabras,
  type LimitesAdmin,
  type PlanAdmin,
} from "./admin-planes";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const ID = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const CUERPO = { nombre: "Estudio", precio_mensual: 49.9, limites: { rucs: 2 } };

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function ultimaLlamada(fn: ReturnType<typeof vi.fn>) {
  const [url, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

/** Solo desde el servidor: el JWT del administrador y la IP resuelta salen del BFF; el navegador nunca los ve. */
describe("listarPlanesAdmin (#190)", () => {
  it("hace GET a /v1/admin/planes con el JWT del administrador y devuelve los planes", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: [{ id: ID, nombre: "Gratis" }] });

    const r = await listarPlanesAdmin("jwt-admin");

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe("http://backend.test/v1/admin/planes");
    expect(init.method ?? "GET").toBe("GET");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual([{ id: ID, nombre: "Gratis" }]);
  });
});

describe("las acciones sobre un plan", () => {
  it("crear hace POST a /v1/admin/planes con el cuerpo, el JWT y la IP de origen", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(201, { estado: "exito", datos: { id: ID } });

    await crearPlan("jwt-admin", CUERPO, { "x-forwarded-for": "203.0.113.7" });

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe("http://backend.test/v1/admin/planes");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual(CUERPO);
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("editar hace PUT a /v1/admin/planes/{id} con el cuerpo", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { id: ID } });

    await editarPlan("jwt-admin", ID, CUERPO, { "x-forwarded-for": "203.0.113.7" });

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/planes/${ID}`);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body as string)).toEqual(CUERPO);
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("desactivar y activar hacen POST a su ruta, sin cuerpo", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    for (const [accion, nombre] of [
      [desactivarPlan, "desactivar"],
      [activarPlan, "activar"],
    ] as const) {
      const fetch = stubFetch(200, { estado: "exito", datos: { id: ID } });

      await accion("jwt-admin", ID, { "x-forwarded-for": "203.0.113.7" });

      const { url, init, headers } = ultimaLlamada(fetch);
      expect(url, nombre).toBe(`http://backend.test/v1/admin/planes/${ID}/${nombre}`);
      expect(init.method, nombre).toBe("POST");
      expect(init.body, nombre).toBeUndefined();
      expect(headers.get("authorization"), nombre).toBe("Bearer jwt-admin");
      expect(headers.get("x-forwarded-for"), nombre).toBe("203.0.113.7");
    }
  });

  it("eliminar hace DELETE a /v1/admin/planes/{id}", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito" });

    await eliminarPlan("jwt-admin", ID, { "x-forwarded-for": "203.0.113.7" });

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/planes/${ID}`);
    expect(init.method).toBe("DELETE");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un rechazo del backend sale como ApiError con su código", async () => {
    stubFetch(409, { estado: "error", codigo: "PLAN_EN_USO", mensaje: "lo tienen 2 cuentas" });

    await expect(eliminarPlan("jwt-admin", ID)).rejects.toMatchObject({ status: 409, codigo: "PLAN_EN_USO" });
  });
});

describe("limiteEnPalabras", () => {
  it("dice la cifra con separador de miles, o «Ilimitados»", () => {
    expect(limiteEnPalabras({ maximo: 300, ilimitado: false })).toBe("300");
    expect(limiteEnPalabras({ maximo: 1500, ilimitado: false })).toBe("1,500");
    expect(limiteEnPalabras({ ilimitado: true })).toBe("Ilimitados");
  });

  it("un límite sin máximo ni marca de ilimitado se ve como un guion, no como «undefined»", () => {
    expect(limiteEnPalabras({ ilimitado: false })).toBe("—");
  });
});

describe("precioEnSoles", () => {
  it("con dos decimales y «Gratis» para cero", () => {
    expect(precioEnSoles(29)).toBe("S/ 29.00");
    expect(precioEnSoles(129)).toBe("S/ 129.00");
    expect(precioEnSoles(1234.5)).toBe("S/ 1,234.50");
    expect(precioEnSoles(49.9)).toBe("S/ 49.90");
    expect(precioEnSoles(0)).toBe("Gratis");
  });
});

/** Tipo de apoyo: que PlanAdmin describa lo que el backend responde (si cambia, esto deja de compilar). */
describe("PlanAdmin", () => {
  it("lleva los límites vigentes y, si lo hay, el cambio programado", () => {
    const plan: PlanAdmin = {
      id: ID,
      nombre: "Estudio",
      precio_mensual: 49.9,
      limites: { documentos_al_mes: { maximo: 800, ilimitado: false }, rucs: 2, usuarios: { maximo: 1, ilimitado: false }, api_keys: { ilimitado: true }, retencion_anios: 6 },
      limites_programados: {
        aplica_desde: "2026-11-01T05:00:00Z",
        limites: { documentos_al_mes: { maximo: 2000, ilimitado: false }, rucs: 2, usuarios: { maximo: 1, ilimitado: false }, api_keys: { ilimitado: true }, retencion_anios: 6 },
      },
      estado: "ACTIVO",
      por_defecto: false,
      cuentas: 3,
    };
    expect(plan.limites_programados?.limites.documentos_al_mes.maximo).toBe(2000);
  });
});

describe("retencionEnPalabras", () => {
  it("1 año, N años", () => {
    expect(retencionEnPalabras(1)).toBe("1 año");
    expect(retencionEnPalabras(5)).toBe("5 años");
    expect(retencionEnPalabras(10)).toBe("10 años");
  });
});

/** Un cambio de límites programado (#190) se muestra como lo que cambia, no como el conjunto entero. */
describe("cambiosDeLimites", () => {
  const base: LimitesAdmin = { documentos_al_mes: { maximo: 800, ilimitado: false }, rucs: 2, usuarios: { maximo: 1, ilimitado: false }, api_keys: { maximo: 2, ilimitado: false }, retencion_anios: 5 };

  it("sin diferencias no hay cambios", () => {
    expect(cambiosDeLimites(base, { ...base })).toEqual([]);
  });

  it("lista solo lo que cambia, de qué a qué, en el orden de la tabla", () => {
    const nuevo: LimitesAdmin = { ...base, documentos_al_mes: { maximo: 2000, ilimitado: false }, retencion_anios: 7, usuarios: { ilimitado: true } };

    expect(cambiosDeLimites(base, nuevo)).toEqual([
      { campo: "documentos", de: "800", a: "2,000" },
      { campo: "usuarios", de: "1", a: "Ilimitados" },
      { campo: "retencion", de: "5 años", a: "7 años" },
    ]);
  });

  it("cubre los cinco límites", () => {
    const nuevo: LimitesAdmin = { documentos_al_mes: { ilimitado: true }, rucs: 3, usuarios: { maximo: 9, ilimitado: false }, api_keys: { ilimitado: true }, retencion_anios: 1 };

    expect(cambiosDeLimites(base, nuevo).map((c) => c.campo)).toEqual(["documentos", "rucs", "usuarios", "apiKeys", "retencion"]);
    expect(cambiosDeLimites(base, nuevo).find((c) => c.campo === "rucs")).toEqual({ campo: "rucs", de: "2", a: "3" });
    expect(cambiosDeLimites(base, nuevo).find((c) => c.campo === "apiKeys")).toEqual({ campo: "apiKeys", de: "2", a: "Ilimitados" });
  });

  it("pasar de ilimitado a una cifra también es un cambio", () => {
    const ilimitado: LimitesAdmin = { ...base, documentos_al_mes: { ilimitado: true } };

    expect(cambiosDeLimites(ilimitado, base)).toEqual([{ campo: "documentos", de: "Ilimitados", a: "800" }]);
  });
});
