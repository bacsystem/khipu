import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import {
  CLASES_DE_ERROR,
  descartarComprobante,
  hrefErrores,
  hrefSiFueraDeRango,
  listarErrores,
  MAX_BUSQUEDA,
  paramsErroresDesdeUrl,
  queryErrores,
  reintentarEnvio,
  type ParamsErrores,
} from "./admin-errores";

afterEach(() => vi.unstubAllGlobals());

const EMPRESA = "00000000-0000-4000-9000-000000000001";
const COMPROBANTE = "00000000-0000-4000-d000-000000000101";
const base: ParamsErrores = { pagina: 1, porPagina: 10 };

const sobre = (datos: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status, headers });

describe("CLASES_DE_ERROR", () => {
  it("son las tres que informa el backend", () => {
    expect([...CLASES_DE_ERROR]).toEqual(["ERROR_DE_ENVIO", "ERROR_DE_FORMATO", "FUERA_DE_PLAZO"]);
  });
});

describe("paramsErroresDesdeUrl", () => {
  it("sin nada trae todas las clases, sin empresa ni búsqueda, de la página 1 con el tamaño por defecto", () => {
    expect(paramsErroresDesdeUrl({})).toEqual({ clase: undefined, empresa: undefined, q: undefined, pagina: 1, porPagina: 10 });
  });

  it("toma una clase, una empresa y un texto válidos", () => {
    expect(paramsErroresDesdeUrl({ clase: "ERROR_DE_FORMATO", empresa_id: EMPRESA, q: "andina", pagina: "3", por_pagina: "50" })).toEqual({
      clase: "ERROR_DE_FORMATO",
      empresa: EMPRESA,
      q: "andina",
      pagina: 3,
      porPagina: 50,
    });
  });

  it("una clase que no existe se ignora, con cualquier caja", () => {
    expect(paramsErroresDesdeUrl({ clase: "OTRA" }).clase).toBeUndefined();
    expect(paramsErroresDesdeUrl({ clase: "error_de_envio" }).clase).toBeUndefined();
    expect(paramsErroresDesdeUrl({ clase: "" }).clase).toBeUndefined();
  });

  it("una empresa que no es un UUID se ignora: no se pega en la URL del backend", () => {
    for (const malo of ["", "abc", "../auth/me", `${EMPRESA}&x=1`, ` ${EMPRESA}`]) expect(paramsErroresDesdeUrl({ empresa_id: malo }).empresa, malo).toBeUndefined();
  });

  it("el texto se recorta, uno en blanco no busca y uno largo se acota", () => {
    expect(paramsErroresDesdeUrl({ q: "  andina  " }).q).toBe("andina");
    expect(paramsErroresDesdeUrl({ q: "   " }).q).toBeUndefined();
    expect(paramsErroresDesdeUrl({ q: "" }).q).toBeUndefined();
    expect(paramsErroresDesdeUrl({ q: "x".repeat(MAX_BUSQUEDA + 50) })?.q).toHaveLength(MAX_BUSQUEDA);
  });

  it("una página que no es un entero positivo es la primera", () => {
    for (const mala of ["0", "-2", "1.5", "abc", ""]) expect(paramsErroresDesdeUrl({ pagina: mala }).pagina, mala).toBe(1);
    expect(paramsErroresDesdeUrl({ pagina: "7" }).pagina).toBe(7);
  });

  it("un tamaño de página fuera de las opciones es el de siempre", () => {
    expect(paramsErroresDesdeUrl({ por_pagina: "999" }).porPagina).toBe(10);
    expect(paramsErroresDesdeUrl({ por_pagina: "20" }).porPagina).toBe(20);
  });
});

describe("queryErrores", () => {
  it("lleva siempre la página y el tamaño, y los filtros solo si se pidieron", () => {
    expect(queryErrores(base).toString()).toBe("pagina=1&por_pagina=10");
    expect(queryErrores({ clase: "FUERA_DE_PLAZO", empresa: EMPRESA, q: "sol & luna", pagina: 2, porPagina: 20 }).toString()).toBe(
      `clase=FUERA_DE_PLAZO&empresa_id=${EMPRESA}&q=sol+%26+luna&pagina=2&por_pagina=20`,
    );
  });
});

describe("hrefErrores", () => {
  it("la ruta base queda limpia cuando nada se aparta del defecto", () => {
    expect(hrefErrores(base)).toBe("/admin/errores");
  });

  it("solo lleva lo que se aparta del defecto", () => {
    expect(hrefErrores({ ...base, clase: "ERROR_DE_ENVIO" })).toBe("/admin/errores?clase=ERROR_DE_ENVIO");
    expect(hrefErrores({ ...base, empresa: EMPRESA })).toBe(`/admin/errores?empresa_id=${EMPRESA}`);
    expect(hrefErrores({ ...base, q: "andina" })).toBe("/admin/errores?q=andina");
    expect(hrefErrores({ ...base, pagina: 2 })).toBe("/admin/errores?pagina=2");
    expect(hrefErrores({ ...base, porPagina: 50 })).toBe("/admin/errores?por_pagina=50");
  });

  it("combina todo en un orden estable", () => {
    expect(hrefErrores({ clase: "ERROR_DE_FORMATO", empresa: EMPRESA, q: "x", pagina: 3, porPagina: 20 })).toBe(`/admin/errores?clase=ERROR_DE_FORMATO&empresa_id=${EMPRESA}&q=x&pagina=3&por_pagina=20`);
  });
});

describe("hrefSiFueraDeRango", () => {
  it("dentro de rango no corrige nada", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 2 }, 20)).toBeNull();
    expect(hrefSiFueraDeRango(base, 0)).toBeNull();
  });

  it("pasada la última lleva a la última, conservando los filtros", () => {
    expect(hrefSiFueraDeRango({ ...base, clase: "ERROR_DE_ENVIO", pagina: 9 }, 25)).toBe("/admin/errores?clase=ERROR_DE_ENVIO&pagina=3");
  });

  it("si ya no queda nada, lleva a la primera página (la ruta limpia)", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 4 }, 0)).toBe("/admin/errores");
  });
});

describe("listarErrores", () => {
  it("pide el listado con el JWT del administrador y los parámetros, y trae el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre([{ comprobante_id: COMPROBANTE }], 200, { "x-total-count": "57" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await listarErrores("jwt-admin", { clase: "ERROR_DE_ENVIO", pagina: 2, porPagina: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/errores?clase=ERROR_DE_ENVIO&pagina=2&por_pagina=20`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r.errores).toHaveLength(1);
    expect(r.total).toBe(57);
  });

  it("sin la cabecera del total, el total es lo que llegó", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre([{}, {}])));

    expect((await listarErrores("jwt", base)).total).toBe(2);
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "no", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 })));

    await expect(listarErrores("jwt-vencido", base)).rejects.toMatchObject({ status: 401, codigo: "NO_AUTORIZADO" });
  });
});

describe("reintentarEnvio", () => {
  it("manda un POST al reintento del comprobante con el JWT y el origen del administrador", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({ comprobante_id: COMPROBANTE, estado: "ERROR_ENVIO", intentos: 4 }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await reintentarEnvio("jwt-admin", COMPROBANTE, { "x-forwarded-for": "203.0.113.7" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/comprobantes/${COMPROBANTE}/reintento`);
    expect(init.method).toBe("POST");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(new Headers(init.headers).get("x-forwarded-for")).toBe("203.0.113.7");
    expect(r).toEqual({ comprobante_id: COMPROBANTE, estado: "ERROR_ENVIO", intentos: 4 });
  });

  it("un 409 del dominio sube con su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "ESTADO_NO_ENVIABLE", errores: null }), { status: 409 })));

    await expect(reintentarEnvio("jwt", COMPROBANTE)).rejects.toMatchObject({ status: 409, codigo: "ESTADO_NO_ENVIABLE" });
  });
});

describe("descartarComprobante", () => {
  it("manda un POST al descarte con el motivo en el cuerpo, el JWT y el origen", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({ comprobante_id: COMPROBANTE, estado: "DESCARTADO" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await descartarComprobante("jwt-admin", COMPROBANTE, "El cliente lo reemitió", { "x-forwarded-for": "203.0.113.7" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/comprobantes/${COMPROBANTE}/descarte`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ motivo: "El cliente lo reemitió" });
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(new Headers(init.headers).get("x-forwarded-for")).toBe("203.0.113.7");
    expect(r.estado).toBe("DESCARTADO");
  });

  it("un 422 por el motivo sube con su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "MOTIVO_REQUERIDO", errores: null }), { status: 422 })));

    await expect(descartarComprobante("jwt", COMPROBANTE, "")).rejects.toMatchObject({ status: 422, codigo: "MOTIVO_REQUERIDO" });
  });
});
