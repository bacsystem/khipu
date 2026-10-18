import { describe, expect, it } from "vitest";
import { AJUSTE_VACIO, ajusteAApi, errorDeAjuste, type AjusteForm } from "./ajustes";

const aj = (p: Partial<AjusteForm>): AjusteForm => ({ ...AJUSTE_VACIO, ...p });

describe("ajusteAApi (#310)", () => {
  it("un ajuste vacío no viaja", () => {
    expect(ajusteAApi(AJUSTE_VACIO, "descuento")).toBeUndefined();
    expect(ajusteAApi(aj({ valor: "  " }), "cargo")).toBeUndefined();
  });

  it("porcentaje o monto, nunca ambos, con afecta_base_igv", () => {
    expect(ajusteAApi(aj({ modo: "porcentaje", valor: "10" }), "descuento")).toEqual({ porcentaje: 10, afecta_base_igv: true });
    expect(ajusteAApi(aj({ modo: "monto", valor: "25.5", afectaBase: false }), "cargo")).toEqual({ monto: 25.5, afecta_base_igv: false });
  });

  it("el recargo al consumo manda su motivo y nunca afecta la base (código 46, Ley 25988)", () => {
    expect(ajusteAApi(aj({ modo: "porcentaje", valor: "10", recargoConsumo: true, afectaBase: true }), "cargo")).toEqual({ porcentaje: 10, afecta_base_igv: false, motivo: "recargo_consumo" });
  });
});

describe("errorDeAjuste (#310)", () => {
  it("lo vacío no es un error", () => {
    expect(errorDeAjuste(AJUSTE_VACIO, "descuento")).toBeNull();
  });

  it("exige un número positivo", () => {
    expect(errorDeAjuste(aj({ valor: "abc" }), "descuento")).toMatch(/número/);
    expect(errorDeAjuste(aj({ valor: "0" }), "descuento")).toMatch(/mayor que cero/);
    expect(errorDeAjuste(aj({ valor: "-3" }), "cargo")).toMatch(/número/);
  });

  it("el porcentaje de un descuento es menor que 100 y el de un cargo menor que 1000, con hasta 5 decimales", () => {
    expect(errorDeAjuste(aj({ modo: "porcentaje", valor: "100" }), "descuento")).toMatch(/menor que 100/);
    expect(errorDeAjuste(aj({ modo: "porcentaje", valor: "99.99999" }), "descuento")).toBeNull();
    expect(errorDeAjuste(aj({ modo: "porcentaje", valor: "1.000001" }), "descuento")).toMatch(/5 decimales/);
    expect(errorDeAjuste(aj({ modo: "porcentaje", valor: "999" }), "cargo")).toBeNull();
    expect(errorDeAjuste(aj({ modo: "porcentaje", valor: "1000" }), "cargo")).toMatch(/menor que 1000/);
  });

  it("el monto lleva hasta 2 decimales", () => {
    expect(errorDeAjuste(aj({ modo: "monto", valor: "10.5" }), "descuento")).toBeNull();
    expect(errorDeAjuste(aj({ modo: "monto", valor: "10.555" }), "descuento")).toMatch(/2 decimales/);
  });
});
