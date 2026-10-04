import { afterEach, describe, expect, it, vi } from "vitest";
import { apiBaseUrl } from "./client";
import { hrefCuentas, hrefSiFueraDeRango, listarCuentasAdmin, paramsCuentasDesdeUrl, queryCuentas } from "./admin-cuentas";

afterEach(() => vi.unstubAllGlobals());

describe("paramsCuentasDesdeUrl", () => {
  it("sin parámetros: primera página, 10 filas y sin búsqueda", () => {
    expect(paramsCuentasDesdeUrl({})).toEqual({ q: undefined, pagina: 1, porPagina: 10 });
  });

  it("una página que no es un entero positivo vuelve a la primera (el backend la acotaría igual)", () => {
    for (const malo of ["abc", "0", "-3", "2.5", ""]) {
      expect(paramsCuentasDesdeUrl({ pagina: malo }).pagina, malo).toBe(1);
    }
    expect(paramsCuentasDesdeUrl({ pagina: "3" }).pagina).toBe(3);
  });

  it("las filas por página solo pueden ser 10, 20 o 50", () => {
    expect(paramsCuentasDesdeUrl({ por_pagina: "7" }).porPagina).toBe(10);
    expect(paramsCuentasDesdeUrl({ por_pagina: "100" }).porPagina).toBe(10);
    expect(paramsCuentasDesdeUrl({ por_pagina: "20" }).porPagina).toBe(20);
    expect(paramsCuentasDesdeUrl({ por_pagina: "50" }).porPagina).toBe(50);
  });

  it("la búsqueda se recorta y una en blanco cuenta como ninguna", () => {
    expect(paramsCuentasDesdeUrl({ q: "  ana " }).q).toBe("ana");
    expect(paramsCuentasDesdeUrl({ q: "   " }).q).toBeUndefined();
    expect(paramsCuentasDesdeUrl({ q: "" }).q).toBeUndefined();
  });
});

describe("queryCuentas", () => {
  it("manda la búsqueda solo si la hay, y siempre página y tamaño", () => {
    expect(queryCuentas({ q: "ana", pagina: 2, porPagina: 20 }).toString()).toBe("q=ana&pagina=2&por_pagina=20");
    expect(queryCuentas({ pagina: 1, porPagina: 10 }).toString()).toBe("pagina=1&por_pagina=10");
  });
});

describe("hrefCuentas", () => {
  it("con todo por defecto es la ruta limpia", () => {
    expect(hrefCuentas({ pagina: 1, porPagina: 10 })).toBe("/admin/cuentas");
  });

  it("solo incluye lo que se aparta del defecto", () => {
    expect(hrefCuentas({ q: "ana", pagina: 1, porPagina: 10 })).toBe("/admin/cuentas?q=ana");
    expect(hrefCuentas({ pagina: 3, porPagina: 10 })).toBe("/admin/cuentas?pagina=3");
    expect(hrefCuentas({ pagina: 1, porPagina: 50 })).toBe("/admin/cuentas?por_pagina=50");
    expect(hrefCuentas({ q: "ana", pagina: 2, porPagina: 20 })).toBe("/admin/cuentas?q=ana&pagina=2&por_pagina=20");
  });

  it("codifica la búsqueda: un & o un espacio no rompen la URL", () => {
    expect(hrefCuentas({ q: "a&b c", pagina: 1, porPagina: 10 })).toBe("/admin/cuentas?q=a%26b+c");
  });
});

describe("hrefSiFueraDeRango", () => {
  it("una página pasada de la última lleva a la última, no a una tabla vacía que dice «no hay cuentas»", () => {
    expect(hrefSiFueraDeRango({ pagina: 99, porPagina: 10 }, 12)).toBe("/admin/cuentas?pagina=2");
  });

  it("conserva la búsqueda y las filas por página al corregir", () => {
    expect(hrefSiFueraDeRango({ q: "ana", pagina: 9, porPagina: 20 }, 25)).toBe("/admin/cuentas?q=ana&pagina=2&por_pagina=20");
  });

  it("sin resultados la última página es la primera, así que vuelve a la ruta limpia de esa búsqueda", () => {
    expect(hrefSiFueraDeRango({ q: "zzz", pagina: 3, porPagina: 10 }, 0)).toBe("/admin/cuentas?q=zzz");
  });

  it("una página dentro de rango no se toca, incluida la última y la primera de un listado vacío", () => {
    expect(hrefSiFueraDeRango({ pagina: 2, porPagina: 10 }, 12)).toBeNull();
    expect(hrefSiFueraDeRango({ pagina: 1, porPagina: 10 }, 12)).toBeNull();
    expect(hrefSiFueraDeRango({ pagina: 1, porPagina: 10 }, 0)).toBeNull();
  });

  it("el total exacto de una página no abre una página de más", () => {
    expect(hrefSiFueraDeRango({ pagina: 1, porPagina: 10 }, 10)).toBeNull();
    expect(hrefSiFueraDeRango({ pagina: 2, porPagina: 10 }, 10)).toBe("/admin/cuentas");
  });
});

describe("listarCuentasAdmin", () => {
  const CUENTA = { id: "c1", nombre: "Mi negocio", email: "ana@negocio.pe", creada_en: "2026-09-01T10:00:00Z", empresas: 2 };

  function stubFetch(headers: Record<string, string> = {}) {
    const fetch = vi.fn(() =>
      Promise.resolve(
        new Response(JSON.stringify({ estado: "exito", datos: [CUENTA], mensaje: null, codigo: null, errores: null }), { status: 200, headers }),
      ),
    );
    vi.stubGlobal("fetch", fetch);
    return fetch;
  }

  it("llama al backend con el JWT del administrador y los parámetros, y lee el total de la cabecera", async () => {
    const fetch = stubFetch({ "x-total-count": "42" });

    const pagina = await listarCuentasAdmin("tok", { q: "ana", pagina: 2, porPagina: 20 });

    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit & { headers: Headers }];
    expect(url).toBe(`${apiBaseUrl()}/v1/admin/cuentas?q=ana&pagina=2&por_pagina=20`);
    expect(init.headers.get("Authorization")).toBe("Bearer tok");
    expect(pagina.datos).toEqual([CUENTA]);
    expect(pagina.total).toBe(42);
  });

  it("sin cabecera de total, el total es lo recibido", async () => {
    stubFetch();

    const pagina = await listarCuentasAdmin("tok", { pagina: 1, porPagina: 10 });

    expect(pagina.total).toBe(1);
  });
});

/** Las cuentas dadas de baja (#201) no salen salvo que se pida: el filtro viaja por la URL de la pantalla, hasta el backend, y se sanea en el camino. */
describe("bajas en el listado de cuentas (#201)", () => {
  it("por defecto no hay filtro de bajas: el backend las oculta", () => {
    expect(paramsCuentasDesdeUrl({}).bajas).toBeUndefined();
    expect(queryCuentas({ pagina: 1, porPagina: 10 }).toString()).toBe("pagina=1&por_pagina=10");
    expect(hrefCuentas({ pagina: 1, porPagina: 10 })).toBe("/admin/cuentas");
  });

  it("INCLUIDAS y SOLO pasan de la URL a los parámetros", () => {
    expect(paramsCuentasDesdeUrl({ bajas: "INCLUIDAS" }).bajas).toBe("INCLUIDAS");
    expect(paramsCuentasDesdeUrl({ bajas: "SOLO" }).bajas).toBe("SOLO");
  });

  /** Un valor que el backend no conoce respondería 400 y el listado entero se vería roto por un parámetro de más: se descarta. */
  it("un valor desconocido, en minúsculas o el propio OCULTAS (que es el defecto) se descarta", () => {
    for (const malo of ["TODAS", "solo", "incluidas", "OCULTAS", "", "1"]) {
      expect(paramsCuentasDesdeUrl({ bajas: malo }).bajas, malo).toBeUndefined();
    }
  });

  it("el filtro viaja al backend junto con la búsqueda, antes de la página", () => {
    expect(queryCuentas({ q: "sol", bajas: "SOLO", pagina: 2, porPagina: 20 }).toString()).toBe("q=sol&bajas=SOLO&pagina=2&por_pagina=20");
  });

  it("la URL de la pantalla lo conserva, y la ruta base queda limpia sin él", () => {
    expect(hrefCuentas({ bajas: "INCLUIDAS", pagina: 1, porPagina: 10 })).toBe("/admin/cuentas?bajas=INCLUIDAS");
    expect(hrefCuentas({ q: "sol", bajas: "SOLO", pagina: 3, porPagina: 50 })).toBe("/admin/cuentas?q=sol&bajas=SOLO&pagina=3&por_pagina=50");
  });

  it("corregir una página fuera de rango no pierde el filtro de bajas", () => {
    expect(hrefSiFueraDeRango({ bajas: "SOLO", pagina: 9, porPagina: 10 }, 12)).toBe("/admin/cuentas?bajas=SOLO&pagina=2");
  });

  it("llama al backend con el filtro", async () => {
    const fetch = vi.fn(() => Promise.resolve(new Response(JSON.stringify({ estado: "exito", datos: [], mensaje: null, codigo: null, errores: null }), { status: 200 })));
    vi.stubGlobal("fetch", fetch);

    await listarCuentasAdmin("tok", { bajas: "INCLUIDAS", pagina: 1, porPagina: 10 });

    expect((fetch.mock.calls[0] as unknown as [string])[0]).toBe(`${apiBaseUrl()}/v1/admin/cuentas?bajas=INCLUIDAS&pagina=1&por_pagina=10`);
  });
});
