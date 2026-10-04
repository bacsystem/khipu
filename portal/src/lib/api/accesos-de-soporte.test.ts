import { afterEach, describe, expect, it, vi } from "vitest";
import { listarAccesosDeSoporte } from "./accesos-de-soporte";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("listarAccesosDeSoporte (#184)", () => {
  it("pide el historial de la cuenta con el JWT del cliente y devuelve lo que dice el backend", async () => {
    vi.stubEnv("API_BASE_URL", "http://backend.test");
    const acceso = { ocurrido_en: "2026-10-04T10:00:00Z", usuario: "ana@negocio.pe", duracion_segundos: 900 };
    const fetch = vi.fn(async () => new Response(JSON.stringify({ estado: "exito", datos: [acceso], mensaje: null, codigo: null, errores: null }), { status: 200 }));
    vi.stubGlobal("fetch", fetch);

    const r = await listarAccesosDeSoporte("jwt-cliente");

    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://backend.test/v1/cuenta/accesos-de-soporte");
    expect(new Headers(init.headers).get("authorization")).toBe("Bearer jwt-cliente");
    expect(init.method ?? "GET").toBe("GET");
    expect(r).toEqual([acceso]);
  });

  it("un error del backend llega como ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ estado: "error", codigo: "NO_AUTORIZADO", mensaje: "Token inválido" }), { status: 401 })));

    await expect(listarAccesosDeSoporte("malo")).rejects.toMatchObject({ status: 401, codigo: "NO_AUTORIZADO" });
  });
});
