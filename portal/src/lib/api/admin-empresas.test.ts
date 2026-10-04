import { describe, expect, it } from "vitest";
import {
  estadoCertificadoDeEmpresa,
  hrefEmpresas,
  hrefEmpresasSiFueraDeRango,
  paramsEmpresasDesdeUrl,
  queryEmpresas,
  type EmpresaAdmin,
} from "./admin-empresas";

describe("paramsEmpresasDesdeUrl (#185)", () => {
  it("sin nada en la URL, los valores por defecto", () => {
    expect(paramsEmpresasDesdeUrl({})).toEqual({ entorno: undefined, certificado: undefined, pagina: 1, porPagina: 10 });
  });

  it("toma los filtros válidos de la URL", () => {
    expect(paramsEmpresasDesdeUrl({ entorno: "PRODUCCION", certificado: "POR_VENCER", pagina: "3", por_pagina: "50" })).toEqual({
      entorno: "PRODUCCION",
      certificado: "POR_VENCER",
      pagina: 3,
      porPagina: 50,
    });
  });

  /** Lo que llega por la URL no se manda al backend sin mirarlo: un filtro inventado daría 400, y el listado se vería roto. */
  it("ignora un filtro que no existe, en vez de mandarlo al backend", () => {
    const p = paramsEmpresasDesdeUrl({ entorno: "STAGING", certificado: "CADUCADO" });
    expect(p.entorno).toBeUndefined();
    expect(p.certificado).toBeUndefined();
  });

  it("distingue mayúsculas: el backend rechaza «beta»", () => {
    expect(paramsEmpresasDesdeUrl({ entorno: "beta", certificado: "vencido" })).toMatchObject({ entorno: undefined, certificado: undefined });
  });

  it("sanea la página y el tamaño", () => {
    expect(paramsEmpresasDesdeUrl({ pagina: "0" }).pagina).toBe(1);
    expect(paramsEmpresasDesdeUrl({ pagina: "-2" }).pagina).toBe(1);
    expect(paramsEmpresasDesdeUrl({ pagina: "2.5" }).pagina).toBe(1);
    expect(paramsEmpresasDesdeUrl({ pagina: "x" }).pagina).toBe(1);
    expect(paramsEmpresasDesdeUrl({ por_pagina: "7" }).porPagina).toBe(10);
  });
});

describe("queryEmpresas", () => {
  it("manda solo los filtros que hay; página y tamaño siempre", () => {
    expect(queryEmpresas({ pagina: 1, porPagina: 10 }).toString()).toBe("pagina=1&por_pagina=10");
    expect(queryEmpresas({ entorno: "BETA", certificado: "VENCIDO", pagina: 2, porPagina: 20 }).toString()).toBe(
      "entorno=BETA&certificado=VENCIDO&pagina=2&por_pagina=20",
    );
  });
});

describe("hrefEmpresas", () => {
  it("la ruta base queda limpia cuando todo es el defecto", () => {
    expect(hrefEmpresas({ pagina: 1, porPagina: 10 })).toBe("/admin/empresas");
  });

  it("lleva solo lo que se aparta del defecto", () => {
    expect(hrefEmpresas({ entorno: "PRODUCCION", certificado: "POR_VENCER", pagina: 2, porPagina: 20 })).toBe(
      "/admin/empresas?entorno=PRODUCCION&certificado=POR_VENCER&pagina=2&por_pagina=20",
    );
    expect(hrefEmpresas({ certificado: "VENCIDO", pagina: 1, porPagina: 10 })).toBe("/admin/empresas?certificado=VENCIDO");
  });
});

describe("hrefEmpresasSiFueraDeRango", () => {
  it("una página que pasa de la última lleva a la última, conservando los filtros", () => {
    expect(hrefEmpresasSiFueraDeRango({ entorno: "BETA", pagina: 9, porPagina: 10 }, 25)).toBe("/admin/empresas?entorno=BETA&pagina=3");
  });

  it("dentro del rango no hace falta corregir nada", () => {
    expect(hrefEmpresasSiFueraDeRango({ pagina: 3, porPagina: 10 }, 25)).toBeNull();
    expect(hrefEmpresasSiFueraDeRango({ pagina: 1, porPagina: 10 }, 0)).toBeNull();
  });

  it("sin resultados, una página mayor que 1 vuelve a la primera", () => {
    expect(hrefEmpresasSiFueraDeRango({ certificado: "VENCIDO", pagina: 4, porPagina: 10 }, 0)).toBe("/admin/empresas?certificado=VENCIDO");
  });
});

describe("estadoCertificadoDeEmpresa", () => {
  const base: EmpresaAdmin = {
    id: "e1",
    ruc: "20100066603",
    razon_social: "ANDINA SAC",
    entorno: "BETA",
    certificado: "VIGENTE",
    tiene_credenciales_sol: false,
    series: 0,
    comprobantes_del_mes: 0,
  };

  it("sin certificado y sin fecha", () => {
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "SIN_CERTIFICADO" })).toEqual({ tipo: "ninguno" });
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "SIN_FECHA" })).toEqual({ tipo: "sin_fecha" });
  });

  it("vigente, por vencer y vencido llevan los días y la fecha que dice el backend", () => {
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "VIGENTE", certificado_vigente_hasta: "2027-03-01", certificado_dias_restantes: 149 })).toEqual({
      tipo: "vigente",
      dias: 149,
      hasta: "2027-03-01",
    });
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "POR_VENCER", certificado_vigente_hasta: "2026-10-13", certificado_dias_restantes: 10 })).toEqual({
      tipo: "por_vencer",
      dias: 10,
      hasta: "2026-10-13",
    });
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "VENCIDO", certificado_vigente_hasta: "2026-09-28", certificado_dias_restantes: -5 })).toEqual({
      tipo: "vencido",
      dias: -5,
      hasta: "2026-09-28",
    });
  });

  /** Si el backend se olvidara de la fecha, no se pinta «Vence el undefined»: se dice que no hay fecha. */
  it("un estado con fecha sin la fecha se muestra como «sin fecha», no roto", () => {
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "POR_VENCER" })).toEqual({ tipo: "sin_fecha" });
    expect(estadoCertificadoDeEmpresa({ ...base, certificado: "VENCIDO", certificado_vigente_hasta: "2026-09-28" })).toEqual({ tipo: "sin_fecha" });
  });
});
