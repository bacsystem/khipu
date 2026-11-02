import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { obtenerBannerVigente } from "./banner";

afterEach(() => vi.unstubAllGlobals());

const sobre = (datos: unknown, status = 200) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });

/** El aviso es un extra: un portal que no carga porque no se pudo leer un aviso de mantenimiento sería peor que no mostrarlo. */
describe("obtenerBannerVigente (#199)", () => {
  it("pide el aviso público, sin credenciales", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({ texto: "Mantenimiento", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T01:00:00Z" }));
    vi.stubGlobal("fetch", fetchMock);

    const b = await obtenerBannerVigente();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/banner`);
    expect(new Headers(init.headers).get("Authorization")).toBeNull();
    expect(b).toEqual({ texto: "Mantenimiento", desde: "2026-10-15T20:00:00Z", hasta: "2026-10-16T01:00:00Z" });
  });

  it("sin aviso devuelve null, venga el dato nulo o ausente", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre(null)));
    expect(await obtenerBannerVigente()).toBeNull();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "exito", mensaje: null, codigo: null, errores: null }), { status: 200 })));
    expect(await obtenerBannerVigente()).toBeNull();
  });

  it("si la API falla, no falla el portal: devuelve null", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre(null, 500)));
    expect(await obtenerBannerVigente()).toBeNull();

    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("fetch failed")));
    expect(await obtenerBannerVigente()).toBeNull();
  });

  it("una respuesta que no tiene la forma de un aviso se ignora en lugar de romper la página", async () => {
    for (const rara of ["texto suelto", 42, [], { texto: 5, hasta: "x" }, { texto: "x" }, { hasta: "x" }]) {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre(rara)));
      expect(await obtenerBannerVigente(), JSON.stringify(rara)).toBeNull();
    }
  });
});
