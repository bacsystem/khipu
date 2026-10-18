import { describe, expect, it } from "vitest";
import { errorDePercepcion, OPERACION_PERCEPCION, PERCEPCION_VACIA, percepcionAApi, REGIMENES, type PercepcionForm } from "./percepcion";

const per = (p: Partial<PercepcionForm>): PercepcionForm => ({ ...PERCEPCION_VACIA, activa: true, ...p });
const SOLES_CONTADO = { moneda: "PEN", alCredito: false };

describe("percepción (#297)", () => {
  it("sin activar no hay nada que decir ni que mandar", () => {
    expect(errorDePercepcion(PERCEPCION_VACIA, { moneda: "USD", alCredito: true })).toBeNull();
    expect(percepcionAApi(PERCEPCION_VACIA, SOLES_CONTADO)).toBeUndefined();
  });

  it("solo vale en soles y al contado (2788, 3330)", () => {
    expect(errorDePercepcion(per({}), SOLES_CONTADO)).toBeNull();
    expect(errorDePercepcion(per({}), { moneda: "USD", alCredito: false })).toMatch(/2788/);
    expect(errorDePercepcion(per({}), { moneda: "PEN", alCredito: true })).toMatch(/3330/);
  });

  it("la base, si se teclea, es un monto positivo con hasta 2 decimales (3233)", () => {
    expect(errorDePercepcion(per({ base: "500.50" }), SOLES_CONTADO)).toBeNull();
    for (const mala of ["0", "-5", "abc", "10.123"]) expect(errorDePercepcion(per({ base: mala }), SOLES_CONTADO)).toMatch(/3233/);
  });

  it("manda el régimen y la base solo si se tecleó; el monto lo calcula el backend", () => {
    expect(percepcionAApi(per({}), SOLES_CONTADO)).toEqual({ regimen: "51", base: undefined });
    expect(percepcionAApi(per({ regimen: "52", base: " 500.5 " }), SOLES_CONTADO)).toEqual({ regimen: "52", base: 500.5 });
    expect(percepcionAApi(per({ base: "0" }), SOLES_CONTADO)).toBeUndefined();
  });

  it("ofrece los tres regímenes del catálogo 53 con las tasas del catálogo 22 y fija la operación 2001", () => {
    expect(REGIMENES.map((r) => [r.codigo, r.tasa])).toEqual([["51", 2], ["52", 1], ["53", 0.5]]);
    expect(OPERACION_PERCEPCION).toBe("2001");
  });
});
