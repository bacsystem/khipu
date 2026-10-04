import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { periodoDelResumen, resumirFacturas } from "./facturas";

afterEach(() => vi.unstubAllGlobals());

const RESUMEN = {
  emitidos: 12,
  aceptados_con_cdr: 9,
  atencion_requerida: { total: 3, rechazados: 1, errores_de_envio: 1, fuera_de_plazo: 1 },
  facturado: [{ moneda: "PEN", total: 1234.5 }],
};

const sobre = (datos: unknown, status = 200) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status });

describe("periodoDelResumen", () => {
  it("sin filtros de fecha es el mes en curso, de su día 1 a hoy", () => {
    expect(periodoDelResumen({}, "2026-10-04")).toEqual({ desde: "2026-10-01", hasta: "2026-10-04", esElMesEnCurso: true });
  });

  it("el primer día del mes es un mes de un solo día", () => {
    expect(periodoDelResumen({}, "2026-10-01")).toEqual({ desde: "2026-10-01", hasta: "2026-10-01", esElMesEnCurso: true });
  });

  it("con las dos fechas es ese rango", () => {
    expect(periodoDelResumen({ desde: "2026-08-01", hasta: "2026-08-31" }, "2026-10-04")).toEqual({ desde: "2026-08-01", hasta: "2026-08-31", esElMesEnCurso: false });
  });

  it("con una sola fecha deja el otro lado abierto y no inventa el mes en curso", () => {
    expect(periodoDelResumen({ desde: "2026-08-15" }, "2026-10-04")).toEqual({ desde: "2026-08-15", hasta: undefined, esElMesEnCurso: false });
    expect(periodoDelResumen({ hasta: "2026-08-15" }, "2026-10-04")).toEqual({ desde: undefined, hasta: "2026-08-15", esElMesEnCurso: false });
  });
});

describe("resumirFacturas", () => {
  it("pide el resumen con el JWT y la empresa y el rango en la URL, y devuelve los datos", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre(RESUMEN));
    vi.stubGlobal("fetch", fetchMock);

    const r = await resumirFacturas("jwt-usuario", "empresa-1", { desde: "2026-10-01", hasta: "2026-10-31" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/facturas/resumen?desde=2026-10-01&hasta=2026-10-31`);
    const h = new Headers(init.headers);
    expect(h.get("Authorization")).toBe("Bearer jwt-usuario");
    expect(h.get("X-Empresa")).toBe("empresa-1");
    expect(r).toEqual(RESUMEN);
  });

  it("sin rango no manda parámetros ni deja un signo de pregunta suelto", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre(RESUMEN));
    vi.stubGlobal("fetch", fetchMock);

    await resumirFacturas("jwt", "e", {});

    expect(fetchMock.mock.calls[0][0]).toBe(`${apiBaseUrl()}/v1/facturas/resumen`);
  });

  it("un solo lado manda solo ese parámetro", async () => {
    // Una respuesta nueva por llamada: un `Response` solo se puede leer una vez.
    const fetchMock = vi.fn().mockImplementation(async () => sobre(RESUMEN));
    vi.stubGlobal("fetch", fetchMock);

    await resumirFacturas("jwt", "e", { hasta: "2026-10-31" });
    await resumirFacturas("jwt", "e", { desde: "2026-10-01" });

    expect(fetchMock.mock.calls[0][0]).toBe(`${apiBaseUrl()}/v1/facturas/resumen?hasta=2026-10-31`);
    expect(fetchMock.mock.calls[1][0]).toBe(`${apiBaseUrl()}/v1/facturas/resumen?desde=2026-10-01`);
  });

  it("un rechazo del backend sube con su status y su código, para que la página lo trate", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "rango", codigo: "RANGO_INVALIDO", errores: null }), { status: 400 })));

    await expect(resumirFacturas("jwt", "e", { desde: "2026-10-31", hasta: "2026-10-01" })).rejects.toMatchObject({ status: 400, codigo: "RANGO_INVALIDO" });
  });
});
