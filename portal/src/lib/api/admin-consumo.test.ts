import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import {
  exportarConsumoDeCuentas,
  hrefConsumo,
  hrefExportacionConsumo,
  hrefSiFueraDeRango,
  listarConsumoDeCuentas,
  paramsConsumoDesdeUrl,
  queryConsumo,
  queryExportacion,
} from "./admin-consumo";

afterEach(() => vi.unstubAllGlobals());

const base = { filtro: "TODAS", orden: "PORCENTAJE", pagina: 1, porPagina: 10 } as const;

describe("paramsConsumoDesdeUrl", () => {
  it("sin parámetros: el mes en curso (sin mes), todas, por porcentaje, primera página y 10 filas", () => {
    expect(paramsConsumoDesdeUrl({})).toEqual({ mes: undefined, filtro: "TODAS", orden: "PORCENTAJE", pagina: 1, porPagina: 10 });
  });

  it("el mes solo vale como AAAA-MM; cualquier otra cosa es el mes en curso", () => {
    expect(paramsConsumoDesdeUrl({ mes: "2026-10" }).mes).toBe("2026-10");
    for (const malo of ["2026-13", "2026-00", "26-10", "2026-1", "octubre", "2026-10-15", "2026-10 ", "", "+12026-10"]) {
      expect(paramsConsumoDesdeUrl({ mes: malo }).mes, malo).toBeUndefined();
    }
  });

  it("el filtro y el orden solo valen si son de los conocidos; el resto es el valor por defecto", () => {
    expect(paramsConsumoDesdeUrl({ filtro: "CERCA_DEL_LIMITE" }).filtro).toBe("CERCA_DEL_LIMITE");
    expect(paramsConsumoDesdeUrl({ filtro: "PLAN_VENCIDO" }).filtro).toBe("PLAN_VENCIDO");
    expect(paramsConsumoDesdeUrl({ orden: "DOCUMENTOS" }).orden).toBe("DOCUMENTOS");
    for (const malo of ["cerca_del_limite", "TODOS", "", "x"]) expect(paramsConsumoDesdeUrl({ filtro: malo }).filtro, malo).toBe("TODAS");
    for (const malo of ["documentos", "NOMBRE", ""]) expect(paramsConsumoDesdeUrl({ orden: malo }).orden, malo).toBe("PORCENTAJE");
  });

  it("una página que no es un entero positivo vuelve a la primera", () => {
    for (const malo of ["abc", "0", "-3", "2.5", ""]) expect(paramsConsumoDesdeUrl({ pagina: malo }).pagina, malo).toBe(1);
    expect(paramsConsumoDesdeUrl({ pagina: "3" }).pagina).toBe(3);
  });

  it("las filas por página solo pueden ser 10, 20 o 50", () => {
    expect(paramsConsumoDesdeUrl({ por_pagina: "7" }).porPagina).toBe(10);
    expect(paramsConsumoDesdeUrl({ por_pagina: "20" }).porPagina).toBe(20);
    expect(paramsConsumoDesdeUrl({ por_pagina: "50" }).porPagina).toBe(50);
  });
});

describe("queryConsumo", () => {
  it("manda siempre filtro, orden, página y tamaño, y el mes solo si se pidió", () => {
    expect(queryConsumo(base).toString()).toBe("filtro=TODAS&orden=PORCENTAJE&pagina=1&por_pagina=10");
    expect(queryConsumo({ ...base, mes: "2026-08", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 3, porPagina: 50 }).toString()).toBe(
      "mes=2026-08&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&pagina=3&por_pagina=50",
    );
  });
});

describe("queryExportacion", () => {
  it("lleva el mes, el filtro y el orden y no la página: se exporta todo", () => {
    expect(queryExportacion({ ...base, mes: "2026-08", filtro: "CERCA_DEL_LIMITE", pagina: 4, porPagina: 20 }).toString()).toBe("mes=2026-08&filtro=CERCA_DEL_LIMITE&orden=PORCENTAJE");
    expect(queryExportacion(base).toString()).toBe("filtro=TODAS&orden=PORCENTAJE");
  });
});

describe("hrefConsumo", () => {
  it("con todo por defecto es la ruta limpia", () => {
    expect(hrefConsumo(base)).toBe("/admin/consumo");
  });

  it("solo incluye lo que se aparta del defecto", () => {
    expect(hrefConsumo({ ...base, mes: "2026-09" })).toBe("/admin/consumo?mes=2026-09");
    expect(hrefConsumo({ ...base, filtro: "CERCA_DEL_LIMITE" })).toBe("/admin/consumo?filtro=CERCA_DEL_LIMITE");
    expect(hrefConsumo({ ...base, orden: "DOCUMENTOS" })).toBe("/admin/consumo?orden=DOCUMENTOS");
    expect(hrefConsumo({ ...base, pagina: 3 })).toBe("/admin/consumo?pagina=3");
    expect(hrefConsumo({ ...base, porPagina: 50 })).toBe("/admin/consumo?por_pagina=50");
    expect(hrefConsumo({ mes: "2026-09", filtro: "PLAN_VENCIDO", orden: "DOCUMENTOS", pagina: 2, porPagina: 20 })).toBe(
      "/admin/consumo?mes=2026-09&filtro=PLAN_VENCIDO&orden=DOCUMENTOS&pagina=2&por_pagina=20",
    );
  });
});

describe("hrefExportacionConsumo", () => {
  it("apunta al BFF del portal y lleva siempre lo que se está viendo, sin página", () => {
    expect(hrefExportacionConsumo({ ...base, mes: "2026-09", filtro: "PLAN_VENCIDO", pagina: 5 })).toBe(
      "/api/admin/consumo/exportacion?mes=2026-09&filtro=PLAN_VENCIDO&orden=PORCENTAJE",
    );
    expect(hrefExportacionConsumo(base)).toBe("/api/admin/consumo/exportacion?filtro=TODAS&orden=PORCENTAJE");
  });
});

describe("hrefSiFueraDeRango", () => {
  it("una página pasada de la última lleva a la última y conserva mes, filtro y orden", () => {
    expect(hrefSiFueraDeRango({ ...base, mes: "2026-09", filtro: "CERCA_DEL_LIMITE", pagina: 99 }, 12)).toBe("/admin/consumo?mes=2026-09&filtro=CERCA_DEL_LIMITE&pagina=2");
  });

  it("sin resultados vuelve a la primera; dentro de rango no se toca", () => {
    expect(hrefSiFueraDeRango({ ...base, filtro: "PLAN_VENCIDO", pagina: 3 }, 0)).toBe("/admin/consumo?filtro=PLAN_VENCIDO");
    expect(hrefSiFueraDeRango({ ...base, pagina: 2 }, 12)).toBeNull();
    expect(hrefSiFueraDeRango({ ...base, pagina: 1 }, 0)).toBeNull();
  });

  it("el total exacto de una página no abre una página de más", () => {
    expect(hrefSiFueraDeRango({ ...base, pagina: 1 }, 10)).toBeNull();
    expect(hrefSiFueraDeRango({ ...base, pagina: 2 }, 10)).toBe("/admin/consumo");
  });
});

describe("listarConsumoDeCuentas", () => {
  const cuerpo = { mes: "2026-10", umbral_de_alerta: 80, cuentas: [{ cuenta_id: "c1", nombre: "Ana" }] };

  it("llama al backend con el JWT del administrador y la consulta, y devuelve los datos con el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ estado: "exito", datos: cuerpo, mensaje: null, codigo: null, errores: null }), { status: 200, headers: { "X-Total-Count": "57" } }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const r = await listarConsumoDeCuentas("jwt-admin", { ...base, mes: "2026-10", filtro: "CERCA_DEL_LIMITE", pagina: 2 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/consumo?mes=2026-10&filtro=CERCA_DEL_LIMITE&orden=PORCENTAJE&pagina=2&por_pagina=10`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(r).toEqual({ datos: cuerpo, total: 57 });
  });

  it("sin la cabecera del total cuenta las cuentas que llegaron", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "exito", datos: cuerpo, mensaje: null, codigo: null, errores: null }), { status: 200 })));

    expect((await listarConsumoDeCuentas("jwt-admin", base)).total).toBe(1);
  });

  it("si el backend falla, el error sube para que la página lo muestre", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "x", codigo: "NO_AUTORIZADO", errores: null }), { status: 401 })));

    await expect(listarConsumoDeCuentas("jwt-admin", base)).rejects.toMatchObject({ status: 401 });
  });
});

describe("exportarConsumoDeCuentas", () => {
  it("pide el CSV con el JWT, el mes, el filtro y el orden, sin página, y devuelve los bytes con el nombre de archivo del backend", async () => {
    const csv = new Uint8Array([0xef, 0xbb, 0xbf, 0x63, 0x0d, 0x0a]);
    const fetchMock = vi.fn().mockResolvedValue(new Response(csv, { status: 200, headers: { "Content-Disposition": 'attachment; filename="consumo-2026-10.csv"' } }));
    vi.stubGlobal("fetch", fetchMock);

    const r = await exportarConsumoDeCuentas("jwt-admin", { ...base, mes: "2026-10", filtro: "PLAN_VENCIDO", pagina: 4 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/consumo/exportacion?mes=2026-10&filtro=PLAN_VENCIDO&orden=PORCENTAJE`);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer jwt-admin");
    expect(init.cache).toBe("no-store");
    expect(r.disposicion).toBe('attachment; filename="consumo-2026-10.csv"');
    expect(new Uint8Array(r.cuerpo)).toEqual(csv);
  });

  it("sin cabecera de nombre de archivo, la disposición es nula", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("x", { status: 200 })));

    expect((await exportarConsumoDeCuentas("jwt-admin", base)).disposicion).toBeNull();
  });

  it("un fallo del backend sube como ApiError con su status y su código", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ estado: "error", datos: null, mensaje: "Mes inválido", codigo: "PARAMETRO_INVALIDO", errores: null }), { status: 400 })));

    await expect(exportarConsumoDeCuentas("jwt-admin", base)).rejects.toMatchObject({ status: 400, codigo: "PARAMETRO_INVALIDO", message: "Mes inválido" });
  });

  it("un fallo que no es JSON (un proxy caído) también sube como ApiError, con un mensaje genérico", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>Bad gateway</html>", { status: 502 })));

    await expect(exportarConsumoDeCuentas("jwt-admin", base)).rejects.toMatchObject({ status: 502, codigo: null, message: "Error de comunicación con la API" });
  });
});
