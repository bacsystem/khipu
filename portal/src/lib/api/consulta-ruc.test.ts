import { afterEach, describe, expect, it, vi } from "vitest";
import { consultarRuc, observacionesDeSunat } from "./consulta-ruc";

const CONTRIBUYENTE = {
  ruc: "20100066603", razon_social: "TIENDAS DEL SUR S.A.C.", estado: "ACTIVO", condicion: "HABIDO", activo: true, habido: true,
  ubigeo: "150101", direccion: "AV. REPUBLICA DE PANAMA NRO. 2457", actualizado_al: "2026-10-16T09:30:00Z",
};

function responde(status: number, cuerpo: unknown) {
  const fetch = vi.fn(() => Promise.resolve(new Response(JSON.stringify(cuerpo), { status })));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
const exito = (datos: unknown) => ({ estado: "exito", datos, mensaje: null, codigo: null, errores: null });
const error = (codigo: string) => ({ estado: "error", datos: null, mensaje: "x", codigo, errores: null });

afterEach(() => vi.unstubAllGlobals());

describe("consultarRuc", () => {
  it("pide el RUC al proxy del portal con GET", async () => {
    const fetch = responde(200, exito(CONTRIBUYENTE));

    const r = await consultarRuc("20100066603");

    expect(fetch).toHaveBeenCalledWith("/api/proxy/consultas/ruc/20100066603", expect.objectContaining({ method: "GET" }));
    expect(r).toEqual({ tipo: "encontrado", contribuyente: CONTRIBUYENTE });
  });

  it("cada código del backend es un desenlace distinto", async () => {
    responde(404, error("NO_ENCONTRADO"));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "no-encontrado" });
    responde(503, error("PADRON_NO_CARGADO"));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "sin-padron" });
    responde(429, error("DEMASIADAS_CONSULTAS"));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "cupo" });
    responde(400, error("PARAMETRO_INVALIDO"));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "error" });
  });

  it("una red caída o una respuesta que no es JSON no lanza: es un error", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "error" });
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("<html>Bad Gateway</html>", { status: 502 }))));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "error" });
  });

  it("un éxito que no trae un contribuyente (un mock que contesta lo mismo a todo) no se toma por uno", async () => {
    responde(200, exito({ id: "c-1" }));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "error" });
    responde(200, exito(null));
    expect(await consultarRuc("20100066603")).toEqual({ tipo: "error" });
  });
});

describe("observacionesDeSunat", () => {
  it("un RUC activo y habido no tiene observaciones", () => {
    expect(observacionesDeSunat(CONTRIBUYENTE)).toEqual([]);
  });

  it("4013 cuando no está activo, con el estado de SUNAT en el texto", () => {
    const o = observacionesDeSunat({ estado: "BAJA DE OFICIO", condicion: "HABIDO", activo: false });
    expect(o.map((x) => x.codigo)).toEqual(["4013"]);
    expect(o[0].texto).toContain("BAJA DE OFICIO");
  });

  it("4014 solo con «NO HABIDO»: las demás condiciones que no son «HABIDO» no se observan", () => {
    expect(observacionesDeSunat({ estado: "ACTIVO", condicion: "NO HABIDO", activo: true }).map((x) => x.codigo)).toEqual(["4014"]);
    for (const condicion of ["NO HALLADO SE MUDO D", "NO HALLADO", "PENDIENTE", "POR VERIFICAR", "NO APLICABLE", "-"]) {
      expect(observacionesDeSunat({ estado: "ACTIVO", condicion, activo: true }), condicion).toEqual([]);
    }
  });

  it("de baja y no habido: las dos, 4013 primero", () => {
    expect(observacionesDeSunat({ estado: "BAJA DEFINITIVA", condicion: "NO HABIDO", activo: false }).map((x) => x.codigo)).toEqual(["4013", "4014"]);
  });
});
