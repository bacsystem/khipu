import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { TIPOS_DE_PROBLEMA, verificarIntegridad } from "./admin-integridad";

afterEach(() => vi.unstubAllGlobals());

const sobre = (datos: unknown, status = 200) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });

describe("TIPOS_DE_PROBLEMA", () => {
  it("son los cuatro que informa el backend", () => {
    expect([...TIPOS_DE_PROBLEMA]).toEqual(["XML_FALTANTE", "XML_CORRUPTO", "CDR_FALTANTE", "STORAGE_INACCESIBLE"]);
  });
});

describe("verificarIntegridad", () => {
  const INFORME = { desde: "2026-09-01", hasta: "2026-09-30", verificados: 12, problemas: [] };

  it("manda un POST con las dos fechas en la URL y el JWT del administrador, y devuelve el informe", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre(INFORME));
    vi.stubGlobal("fetch", fetchMock);

    const r = await verificarIntegridad("jwt-admin", "2026-09-01", "2026-09-30");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/integridad?desde=2026-09-01&hasta=2026-09-30`);
    expect(init.method).toBe("POST");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual(INFORME);
  });

  it("codifica las fechas: nada que llegue acá puede agregar parámetros a la URL del backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre(INFORME));
    vi.stubGlobal("fetch", fetchMock);

    await verificarIntegridad("jwt-admin", "2026-09-01&x=1", "a b");

    expect(fetchMock.mock.calls[0][0]).toBe(`${apiBaseUrl()}/v1/admin/integridad?desde=2026-09-01%26x%3D1&hasta=a%20b`);
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "rango", codigo: "RANGO_INVALIDO", errores: null }), { status: 400 })));

    await expect(verificarIntegridad("jwt-admin", "2026-09-30", "2026-09-01")).rejects.toMatchObject({ status: 400, codigo: "RANGO_INVALIDO" });
  });
});
