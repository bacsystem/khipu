import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import {
  avisarAlCliente,
  hrefAvisos,
  hrefSiFueraDeRango,
  listarCertificadosEnRiesgo,
  listarCredencialesSolFallando,
  paramsAvisosDesdeUrl,
  queryAvisos,
  VISTAS_DE_AVISOS,
  type ParamsAvisos,
} from "./admin-avisos";

afterEach(() => vi.unstubAllGlobals());

const EMPRESA = "00000000-0000-4000-9000-000000000001";
const base: ParamsAvisos = { vista: "CERTIFICADOS", pagina: 1, porPagina: 10 };

const sobre = (datos: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify({ estado: status < 400 ? "exito" : "error", datos, mensaje: null, codigo: null, errores: null }), { status, headers });

describe("VISTAS_DE_AVISOS", () => {
  it("son las dos listas del backoffice", () => {
    expect([...VISTAS_DE_AVISOS]).toEqual(["CERTIFICADOS", "CREDENCIALES_SOL"]);
  });
});

describe("paramsAvisosDesdeUrl", () => {
  it("sin nada es la lista de certificados, la página 1 y el tamaño por defecto", () => {
    expect(paramsAvisosDesdeUrl({})).toEqual(base);
  });

  it("toma una vista, una página y un tamaño válidos", () => {
    expect(paramsAvisosDesdeUrl({ vista: "CREDENCIALES_SOL", pagina: "3", por_pagina: "50" })).toEqual({ vista: "CREDENCIALES_SOL", pagina: 3, porPagina: 50 });
  });

  it("una vista que no existe, en cualquier caja, es la de certificados", () => {
    for (const mala of ["OTRA", "certificados", "credenciales_sol", ""]) expect(paramsAvisosDesdeUrl({ vista: mala }).vista, mala).toBe("CERTIFICADOS");
  });

  it("una página que no es un entero positivo es la primera", () => {
    for (const mala of ["0", "-2", "1.5", "abc", ""]) expect(paramsAvisosDesdeUrl({ pagina: mala }).pagina, mala).toBe(1);
    expect(paramsAvisosDesdeUrl({ pagina: "7" }).pagina).toBe(7);
  });

  it("un tamaño de página fuera de las opciones es el de siempre", () => {
    expect(paramsAvisosDesdeUrl({ por_pagina: "999" }).porPagina).toBe(10);
    expect(paramsAvisosDesdeUrl({ por_pagina: "20" }).porPagina).toBe(20);
  });
});

describe("queryAvisos y hrefAvisos", () => {
  it("la llamada al backend lleva siempre la página y el tamaño", () => {
    expect(queryAvisos(base).toString()).toBe("pagina=1&por_pagina=10");
    expect(queryAvisos({ ...base, pagina: 3, porPagina: 50 }).toString()).toBe("pagina=3&por_pagina=50");
  });

  it("la ruta base queda limpia cuando nada se aparta del defecto", () => {
    expect(hrefAvisos(base)).toBe("/admin/avisos");
  });

  it("solo lleva lo que se aparta del defecto", () => {
    expect(hrefAvisos({ ...base, vista: "CREDENCIALES_SOL" })).toBe("/admin/avisos?vista=CREDENCIALES_SOL");
    expect(hrefAvisos({ ...base, pagina: 2 })).toBe("/admin/avisos?pagina=2");
    expect(hrefAvisos({ ...base, porPagina: 50 })).toBe("/admin/avisos?por_pagina=50");
    expect(hrefAvisos({ vista: "CREDENCIALES_SOL", pagina: 3, porPagina: 20 })).toBe("/admin/avisos?vista=CREDENCIALES_SOL&pagina=3&por_pagina=20");
  });

  it("la primera página y el tamaño por defecto no van a la URL", () => {
    expect(hrefAvisos({ ...base, pagina: 1 })).not.toContain("pagina");
    expect(hrefAvisos({ ...base, porPagina: 10 })).not.toContain("por_pagina");
  });
});

describe("hrefSiFueraDeRango", () => {
  it("dentro de rango no corrige nada", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 2 }, 20)).toBeNull();
    expect(hrefSiFueraDeRango(base, 0)).toBeNull();
  });

  it("pasada la última lleva a la última, conservando la vista", () => {
    expect(hrefSiFueraDeRango({ ...base, vista: "CREDENCIALES_SOL", pagina: 9 }, 25)).toBe("/admin/avisos?vista=CREDENCIALES_SOL&pagina=3");
  });

  it("si ya no queda nada, lleva a la primera página (la ruta limpia)", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 4 }, 0)).toBe("/admin/avisos");
  });

  it("la última página es justo la que contiene el último elemento", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 3 }, 20)).toBe("/admin/avisos?pagina=2");
    expect(hrefSiFueraDeRango({ ...base, pagina: 2 }, 20)).toBeNull();
  });
});

describe("listarCertificadosEnRiesgo", () => {
  it("pide la lista con el JWT del administrador y trae el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre([{ empresa_id: EMPRESA }], 200, { "x-total-count": "37" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await listarCertificadosEnRiesgo("jwt-admin", { ...base, pagina: 2, porPagina: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/avisos/certificados?pagina=2&por_pagina=20`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r.filas).toHaveLength(1);
    expect(r.total).toBe(37);
  });

  it("sin la cabecera del total, el total es lo que llegó", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre([{}, {}])));

    expect((await listarCertificadosEnRiesgo("jwt", base)).total).toBe(2);
  });

  it("un rechazo del backend sube con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "no", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 })));

    await expect(listarCertificadosEnRiesgo("jwt-vencido", base)).rejects.toMatchObject({ status: 401, codigo: "NO_AUTORIZADO" });
  });
});

describe("listarCredencialesSolFallando", () => {
  it("pide la lista de credenciales SOL con el JWT y trae el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre([{ empresa_id: EMPRESA }], 200, { "x-total-count": "5" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await listarCredencialesSolFallando("jwt-admin", { ...base, vista: "CREDENCIALES_SOL", pagina: 2, porPagina: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/avisos/credenciales-sol?pagina=2&por_pagina=20`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r.total).toBe(5);
  });

  it("sin la cabecera, el total es lo que llegó", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sobre([{}])));

    expect((await listarCredencialesSolFallando("jwt", base)).total).toBe(1);
  });
});

describe("avisarAlCliente", () => {
  it("manda un POST con el tipo en el cuerpo, el JWT y el origen del administrador", async () => {
    const fetchMock = vi.fn().mockResolvedValue(sobre({ empresa_id: EMPRESA, motivo: "CERTIFICADO_POR_VENCER", destinatario: "ana@negocio.pe", enviado_en: "x", avisar_desde: "y" }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await avisarAlCliente("jwt-admin", EMPRESA, "CERTIFICADO", { "x-forwarded-for": "203.0.113.7" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/empresas/${EMPRESA}/avisos`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ tipo: "CERTIFICADO" });
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(new Headers(init.headers).get("x-forwarded-for")).toBe("203.0.113.7");
    expect(r.destinatario).toBe("ana@negocio.pe");
  });

  it("un 409 del dominio sube con su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "AVISO_RECIENTE", errores: null }), { status: 409 })));

    await expect(avisarAlCliente("jwt", EMPRESA, "CREDENCIALES_SOL")).rejects.toMatchObject({ status: 409, codigo: "AVISO_RECIENTE" });
  });
});
