import { describe, expect, it } from "vitest";
import { fechaMinimaDeEmision, soloPorResumen } from "./plazo";

/** #292: espeja `PlazoEnvio` del backend. Factura: 3 días. Boleta: sola hasta el 5.º día y por resumen diario hasta el 7.º. */
describe("plazo de envío", () => {
  it("la factura admite hasta 3 días atrás y la boleta hasta 7", () => {
    expect(fechaMinimaDeEmision("factura", "2026-10-10")).toBe("2026-10-07");
    expect(fechaMinimaDeEmision("boleta", "2026-10-10")).toBe("2026-10-03");
  });

  it("la boleta va sola hasta el 5.º día; el 6.º y el 7.º, en el resumen diario", () => {
    expect(soloPorResumen("boleta", "2026-10-05", "2026-10-10")).toBe(false);
    expect(soloPorResumen("boleta", "2026-10-04", "2026-10-10")).toBe(true);
    expect(soloPorResumen("boleta", "2026-10-03", "2026-10-10")).toBe(true);
  });

  it("una factura nunca va en un resumen", () => {
    expect(soloPorResumen("factura", "2026-10-04", "2026-10-10")).toBe(false);
  });
});
