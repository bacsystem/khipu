import { afterEach, describe, expect, it, vi } from "vitest";
import { cambiarPlanDeCuenta, obtenerPlanDeCuenta, previsualizarCambioDePlan } from "./admin-plan-de-cuenta";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const PLAN = "1c2d3e4f-0f1c-4b53-9a1e-2f6f6d0c7a22";

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
describe("obtenerPlanDeCuenta (#191)", () => {
  it("hace GET a /v1/admin/cuentas/{id}/plan con el JWT del administrador", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA, estado: "VIGENTE" } });

    const r = await obtenerPlanDeCuenta("jwt-admin", CUENTA);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/plan`);
    expect(init.method ?? "GET").toBe("GET");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ cuenta_id: CUENTA, estado: "VIGENTE" });
  });

  it("un rechazo del backend sale como ApiError con su código", async () => {
    stubFetch(404, { estado: "error", codigo: "NO_ENCONTRADO", mensaje: "La cuenta no existe" });

    await expect(obtenerPlanDeCuenta("jwt-admin", CUENTA)).rejects.toMatchObject({ status: 404, codigo: "NO_ENCONTRADO" });
  });
});

describe("previsualizarCambioDePlan", () => {
  it("hace GET a la previsualización con el plan como parámetro y el JWT", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { direccion: "BAJADA" } });

    const r = await previsualizarCambioDePlan("jwt-admin", CUENTA, PLAN, { "x-forwarded-for": "203.0.113.7" });

    const { url, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/plan/previsualizacion?plan_id=${PLAN}`);
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
    expect(r).toEqual({ direccion: "BAJADA" });
  });

  it("el plan se escapa en la URL: lo que llegue no puede agregar parámetros", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: {} });

    await previsualizarCambioDePlan("jwt-admin", CUENTA, "x&otro=1");

    expect(ultimaLlamada(fetch).url).toContain("plan_id=x%26otro%3D1");
  });
});

describe("cambiarPlanDeCuenta", () => {
  it("hace POST a /v1/admin/cuentas/{id}/plan con el cuerpo, el JWT y la IP de origen", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA } });
    const cuerpo = { plan_id: PLAN, vence_en: "2026-11-01T05:00:00.000Z", dias_de_gracia: 5 };

    await cambiarPlanDeCuenta("jwt-admin", CUENTA, cuerpo, { "x-forwarded-for": "203.0.113.7" });

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/plan`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual(cuerpo);
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un conflicto del backend (otro administrador cambió el plan) sale como ApiError con su código", async () => {
    stubFetch(409, { estado: "error", codigo: "CAMBIO_CONCURRENTE", mensaje: "Otro administrador cambió el plan" });

    await expect(cambiarPlanDeCuenta("jwt-admin", CUENTA, { plan_id: PLAN })).rejects.toMatchObject({ status: 409, codigo: "CAMBIO_CONCURRENTE" });
  });
});
