import { describe, expect, it } from "vitest";
import { CREDITO_VACIO, errorDeCredito, formaPagoAApi, montoValido, repartirEnCuotas, sumaDeCuotas, type CreditoForm } from "./credito";

const EMISION = "2026-10-01";
const credito = (p: Partial<CreditoForm>): CreditoForm => ({ ...CREDITO_VACIO, ...p });

describe("repartirEnCuotas (#309)", () => {
  it("reparte en cuotas iguales cada 30 días, con los céntimos sobrantes en la última", () => {
    expect(repartirEnCuotas(1180, 2, EMISION)).toEqual([
      { monto: "590.00", vencimiento: "2026-10-31" },
      { monto: "590.00", vencimiento: "2026-11-30" },
    ]);
    const tres = repartirEnCuotas(100, 3, EMISION);
    expect(tres.map((c) => c.monto)).toEqual(["33.33", "33.33", "33.34"]);
    expect(sumaDeCuotas(tres)).toBe(100);
  });

  it("sin neto o sin cuotas no reparte nada", () => {
    expect(repartirEnCuotas(0, 3, EMISION)).toEqual([]);
    expect(repartirEnCuotas(100, 0, EMISION)).toEqual([]);
  });

  it("la suma exacta se conserva con importes de céntimos", () => {
    for (const [neto, n] of [[0.1, 3], [1180.07, 7], [99.99, 12]] as const) expect(sumaDeCuotas(repartirEnCuotas(neto, n, EMISION))).toBeCloseTo(neto, 2);
  });
});

describe("errorDeCredito (#309, 3250–3267)", () => {
  const buenas = [{ monto: "590.00", vencimiento: "2026-10-31" }, { monto: "590.00", vencimiento: "2026-11-30" }];

  it("un crédito bien armado no tiene error, con el neto pendiente tecleado o el total por defecto", () => {
    expect(errorDeCredito(credito({ cuotas: buenas }), 1180, EMISION)).toBeNull();
    expect(errorDeCredito(credito({ montoPendiente: "1180.00", cuotas: buenas }), 999, EMISION)).toBeNull();
  });

  it("pide al menos una cuota", () => {
    expect(errorDeCredito(credito({}), 1180, EMISION)).toMatch(/al menos una cuota/);
  });

  it("cada cuota es un importe positivo con 2 decimales y lleva fecha", () => {
    expect(errorDeCredito(credito({ cuotas: [{ monto: "", vencimiento: "2026-10-31" }] }), 100, EMISION)).toMatch(/cuota 1.*importe/);
    expect(errorDeCredito(credito({ cuotas: [{ monto: "10.555", vencimiento: "2026-10-31" }] }), 100, EMISION)).toMatch(/cuota 1.*2 decimales/);
    expect(errorDeCredito(credito({ cuotas: [{ monto: "100", vencimiento: "" }] }), 100, EMISION)).toMatch(/fecha de vencimiento/);
  });

  it("los vencimientos son posteriores a la emisión y crecientes", () => {
    expect(errorDeCredito(credito({ cuotas: [{ monto: "100", vencimiento: EMISION }] }), 100, EMISION)).toMatch(/posterior a la fecha de emisión/);
    expect(errorDeCredito(credito({ cuotas: [{ monto: "50", vencimiento: "2026-11-15" }, { monto: "50", vencimiento: "2026-11-15" }] }), 100, EMISION)).toMatch(/posterior al de la cuota 1/);
  });

  it("las cuotas suman el neto pendiente", () => {
    expect(errorDeCredito(credito({ cuotas: [{ monto: "500", vencimiento: "2026-10-31" }] }), 1180, EMISION)).toMatch(/suman 500\.00 y el neto pendiente es 1180\.00/);
    expect(errorDeCredito(credito({ montoPendiente: "abc", cuotas: buenas }), 1180, EMISION)).toMatch(/neto pendiente/);
  });
});

describe("formaPagoAApi", () => {
  it("al contado solo manda el tipo", () => {
    expect(formaPagoAApi(null, 100)).toEqual({ tipo: "contado" });
  });

  it("al crédito manda el neto pendiente (o el total) y las cuotas como números", () => {
    const c = credito({ cuotas: [{ monto: "590.00", vencimiento: "2026-10-31" }, { monto: "590.00", vencimiento: "2026-11-30" }] });
    expect(formaPagoAApi(c, 1180)).toEqual({ tipo: "credito", monto_pendiente: 1180, cuotas: [{ monto: 590, vencimiento: "2026-10-31" }, { monto: 590, vencimiento: "2026-11-30" }] });
    expect(formaPagoAApi({ ...c, montoPendiente: "1000" }, 1180).tipo === "credito" && (formaPagoAApi({ ...c, montoPendiente: "1000" }, 1180) as { monto_pendiente: number }).monto_pendiente).toBe(1000);
  });
});

describe("montoValido", () => {
  it("positivo y hasta 2 decimales", () => {
    expect(montoValido("10")).toBe(true);
    expect(montoValido("0.50")).toBe(true);
    expect(montoValido(".5")).toBe(true);
    expect(montoValido("0")).toBe(false);
    expect(montoValido("-1")).toBe(false);
    expect(montoValido("1.005")).toBe(false);
    expect(montoValido("")).toBe(false);
  });
});
