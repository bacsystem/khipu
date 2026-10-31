import { afterEach, describe, expect, it, vi } from "vitest";
import {
  estaEnviandoGuia,
  esGuiaResuelta,
  filtrosDeGuiasDesdeParams,
  listarGuias,
  numeroDeGuia,
  paramsDeFiltrosDeGuias,
  puedeConsultarTicketDeGuia,
  puedeEnviarGuia,
  type EstadoDeGuia,
} from "./guias";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("filtros de las guías", () => {
  it("conserva los valores que la API acepta", () => {
    expect(filtrosDeGuiasDesdeParams({ estado: "ACEPTADO", tipo: "31", serie: "V001", desde: "2026-10-01", hasta: "2026-10-31" })).toEqual({
      estado: "ACEPTADO",
      tipo: "31",
      serie: "V001",
      desde: "2026-10-01",
      hasta: "2026-10-31",
    });
  });

  it("descarta un estado, un tipo, una serie o una fecha que la API rechazaría con 400, en vez de dejar la lista vacía", () => {
    expect(filtrosDeGuiasDesdeParams({ estado: "ANULADO", tipo: "01", serie: "F001", desde: "ayer", hasta: "2026-13-1" })).toEqual({});
    // RECIBIDO solo existe en /validar: ninguna guía guardada lo tiene.
    expect(filtrosDeGuiasDesdeParams({ estado: "RECIBIDO" })).toEqual({});
  });

  it("pasa la serie a mayúsculas y recorta los espacios", () => {
    expect(filtrosDeGuiasDesdeParams({ serie: " t001 " })).toEqual({ serie: "T001" });
  });

  it("intercambia un rango invertido (desde posterior a hasta es 400 RANGO_INVALIDO)", () => {
    expect(filtrosDeGuiasDesdeParams({ desde: "2026-10-31", hasta: "2026-10-01" })).toEqual({ desde: "2026-10-01", hasta: "2026-10-31" });
  });

  it("arma la query solo con lo que tiene valor", () => {
    expect(paramsDeFiltrosDeGuias({ estado: "FIRMADO", serie: "" }).toString()).toBe("estado=FIRMADO");
  });
});

describe("qué se puede hacer con una guía según su estado", () => {
  const porEstado = (estado: EstadoDeGuia) => ({ estado });
  const TODOS: EstadoDeGuia[] = ["FIRMADO", "ERROR_ENVIO", "ENVIANDO", "ENVIADO_PENDIENTE_TICKET", "ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO"];

  it("envía solo una firmada o una con error de envío", () => {
    expect(TODOS.filter((e) => puedeEnviarGuia(porEstado(e)))).toEqual(["FIRMADO", "ERROR_ENVIO"]);
  });

  it("consulta el ticket solo cuando SUNAT ya la recibió y la está procesando", () => {
    expect(TODOS.filter((e) => puedeConsultarTicketDeGuia(porEstado(e)))).toEqual(["ENVIADO_PENDIENTE_TICKET"]);
  });

  it("distingue el envío en curso y la respuesta definitiva", () => {
    expect(TODOS.filter((e) => estaEnviandoGuia(porEstado(e)))).toEqual(["ENVIANDO"]);
    expect(TODOS.filter((e) => esGuiaResuelta(porEstado(e)))).toEqual(["ACEPTADO", "ACEPTADO_CON_OBS", "RECHAZADO"]);
  });
});

describe("numeroDeGuia", () => {
  it("escribe el correlativo con ocho dígitos, como SUNAT", () => {
    expect(numeroDeGuia({ serie: "T001", numero: 15 })).toBe("T001-00000015");
  });
});

describe("listarGuias", () => {
  it("pide la página con sus filtros y toma el total de la cabecera", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ estado: "exito", datos: [{ id: "g-1" }], mensaje: null, codigo: null, errores: null }), { status: 200, headers: { "x-total-count": "37" } }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const pagina = await listarGuias("jwt", "e-1", { estado: "ACEPTADO", pagina: 2, porPagina: 10 });

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain("/v1/guias-remision?");
    expect(url).toContain("estado=ACEPTADO");
    expect(url).toContain("pagina=2");
    expect(url).toContain("por_pagina=10");
    expect(pagina.total).toBe(37);
    expect(pagina.datos).toHaveLength(1);
  });
});
