import { afterEach, describe, expect, it, vi } from "vitest";
import { cambiarEntornoEmpresa, ENTORNOS_DE_EMPRESA, probarConexionEmpresa, revocarApiKeyEmpresa } from "./admin-acciones-empresa";
import { ApiError } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const EMPRESA = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11";
const KEY = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

function stubFetch(status: number, cuerpo: object) {
  const fn = vi.fn(async () => new Response(JSON.stringify({ mensaje: null, codigo: null, errores: null, datos: null, ...cuerpo }), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function ultimaLlamada(fn: ReturnType<typeof vi.fn>) {
  const [url, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

/** Solo desde el servidor: el JWT del administrador y la IP resuelta salen del BFF; el navegador nunca los ve (#187). */
describe("cambiarEntornoEmpresa", () => {
  it("hace POST a /entorno con el entorno en el cuerpo y el JWT del administrador", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { empresa_id: EMPRESA, desde: "BETA", hacia: "PRODUCCION" } });

    const r = await cambiarEntornoEmpresa("jwt-admin", EMPRESA, "PRODUCCION");

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/empresas/${EMPRESA}/entorno`);
    expect(init.method).toBe("POST");
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(JSON.parse(init.body as string)).toEqual({ entorno: "PRODUCCION" });
    expect(r).toEqual({ empresa_id: EMPRESA, desde: "BETA", hacia: "PRODUCCION" });
  });

  it("manda la IP ya resuelta para que la bitácora registre la del administrador", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: {} });

    await cambiarEntornoEmpresa("jwt-admin", EMPRESA, "BETA", { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError con su status y su código", async () => {
    stubFetch(409, { estado: "error", codigo: "EMPRESA_CON_ENVIOS_PENDIENTES", mensaje: "pendientes" });

    await expect(cambiarEntornoEmpresa("jwt-admin", EMPRESA, "PRODUCCION")).rejects.toMatchObject({ status: 409, codigo: "EMPRESA_CON_ENVIOS_PENDIENTES" });
    await expect(cambiarEntornoEmpresa("jwt-admin", EMPRESA, "PRODUCCION")).rejects.toBeInstanceOf(ApiError);
  });

  it("los entornos que se aceptan son exactamente los dos que existen", () => {
    expect([...ENTORNOS_DE_EMPRESA]).toEqual(["BETA", "PRODUCCION"]);
  });
});

describe("revocarApiKeyEmpresa", () => {
  it("hace POST a la ruta de esa key, sin cuerpo, con el JWT del administrador", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { api_key_id: KEY, revocada_en: "2026-10-03T09:00:00Z" } });

    const r = await revocarApiKeyEmpresa("jwt-admin", EMPRESA, KEY);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/empresas/${EMPRESA}/api-keys/${KEY}/revocar`);
    expect(init.method).toBe("POST");
    expect(init.body).toBeUndefined();
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ api_key_id: KEY, revocada_en: "2026-10-03T09:00:00Z" });
  });

  it("manda la IP ya resuelta", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: {} });

    await revocarApiKeyEmpresa("jwt-admin", EMPRESA, KEY, { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError", async () => {
    stubFetch(409, { estado: "error", codigo: "API_KEY_YA_REVOCADA", mensaje: "ya" });

    await expect(revocarApiKeyEmpresa("jwt-admin", EMPRESA, KEY)).rejects.toMatchObject({ status: 409, codigo: "API_KEY_YA_REVOCADA" });
  });
});

describe("probarConexionEmpresa", () => {
  it("hace POST a /prueba-de-conexion con el JWT del administrador y devuelve cómo contestó SUNAT", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const fetch = stubFetch(200, { estado: "exito", datos: { resultado: "RECHAZADO", entorno: "BETA", codigo: "1033", mensaje: "El ticket no existe" } });

    const r = await probarConexionEmpresa("jwt-admin", EMPRESA);

    const { url, init, headers } = ultimaLlamada(fetch);
    expect(url).toBe(`http://backend.test/v1/admin/empresas/${EMPRESA}/prueba-de-conexion`);
    expect(init.method).toBe("POST");
    expect(init.body).toBeUndefined();
    expect(headers.get("authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ resultado: "RECHAZADO", entorno: "BETA", codigo: "1033", mensaje: "El ticket no existe" });
  });

  it("manda la IP ya resuelta", async () => {
    const fetch = stubFetch(200, { estado: "exito", datos: {} });

    await probarConexionEmpresa("jwt-admin", EMPRESA, { "X-Forwarded-For": "203.0.113.7" });

    expect(ultimaLlamada(fetch).headers.get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("un error del backend llega como ApiError", async () => {
    stubFetch(409, { estado: "error", codigo: "SOL_NO_CARGADAS", mensaje: "sin SOL" });

    await expect(probarConexionEmpresa("jwt-admin", EMPRESA)).rejects.toMatchObject({ status: 409, codigo: "SOL_NO_CARGADAS" });
  });
});
