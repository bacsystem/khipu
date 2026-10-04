import { afterEach, describe, expect, it, vi } from "vitest";
import { bajasDesdeUrl, darDeBajaCuenta, reponerCuenta, VISIBILIDADES_DE_BAJAS } from "./admin-baja";
import { ApiError } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const CUENTA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function ultimaLlamada(fn: ReturnType<typeof vi.fn>) {
  const [url, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

describe("bajasDesdeUrl (#201)", () => {
  it("solo INCLUIDAS y SOLO son filtros: OCULTAS es el defecto y no se representa", () => {
    expect(VISIBILIDADES_DE_BAJAS).toEqual(["INCLUIDAS", "SOLO"]);
    expect(bajasDesdeUrl("INCLUIDAS")).toBe("INCLUIDAS");
    expect(bajasDesdeUrl("SOLO")).toBe("SOLO");
    for (const malo of [undefined, "", "OCULTAS", "solo", "TODAS"]) expect(bajasDesdeUrl(malo), String(malo)).toBeUndefined();
  });
});

/** Solo desde el servidor: el JWT del administrador y la IP resuelta salen del BFF; el navegador nunca los ve. */
describe("darDeBajaCuenta", () => {
  it("hace POST a /baja con el motivo y el JWT del administrador", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA, baja_en: "2026-10-03T09:00:00Z" } });

    const r = await darDeBajaCuenta("jwt-admin", CUENTA, "cerró su negocio");

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/baja`);
    expect(init.method).toBe("POST");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(JSON.parse(init.body as string)).toEqual({ motivo: "cerró su negocio" });
    expect(r).toEqual({ cuenta_id: CUENTA, baja_en: "2026-10-03T09:00:00Z" });
  });

  it("sin motivo no manda cuerpo", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA } });

    await darDeBajaCuenta("jwt-admin", CUENTA, undefined);

    expect(ultimaLlamada(fetch).init.body).toBeUndefined();
  });

  it("manda la IP ya resuelta para que la bitácora registre la del administrador", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA } });

    await darDeBajaCuenta("jwt-admin", CUENTA, undefined, { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError con su status y su código", async () => {
    stubFetch(409, { estado: "error", codigo: "CUENTA_YA_DE_BAJA", mensaje: "La cuenta ya está dada de baja" });

    await expect(darDeBajaCuenta("jwt-admin", CUENTA, undefined)).rejects.toMatchObject({ status: 409, codigo: "CUENTA_YA_DE_BAJA" });
    await expect(darDeBajaCuenta("jwt-admin", CUENTA, undefined)).rejects.toBeInstanceOf(ApiError);
  });
});

describe("reponerCuenta", () => {
  it("hace POST a /reponer con el JWT del administrador y sin cuerpo", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA } });

    const r = await reponerCuenta("jwt-admin", CUENTA);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/cuentas/${CUENTA}/reponer`);
    expect(init.method).toBe("POST");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(init.body).toBeUndefined();
    expect(r).toEqual({ cuenta_id: CUENTA });
  });

  it("manda la IP ya resuelta", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: { cuenta_id: CUENTA } });

    await reponerCuenta("jwt-admin", CUENTA, { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError", async () => {
    stubFetch(409, { estado: "error", codigo: "CUENTA_NO_DE_BAJA", mensaje: "La cuenta no está dada de baja" });

    await expect(reponerCuenta("jwt-admin", CUENTA)).rejects.toMatchObject({ status: 409, codigo: "CUENTA_NO_DE_BAJA" });
  });
});
