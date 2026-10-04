import { describe, expect, it } from "vitest";
import { validarCambioDePlan, type ValoresDeCambioDePlan } from "./cambio-de-plan";

const HOY = "2026-10-04";
const PLAN = "1c2d3e4f-0f1c-4b53-9a1e-2f6f6d0c7a22";

const DE_PAGO: ValoresDeCambioDePlan = { planId: PLAN, precioDelPlan: 29, pagadoHasta: "2026-11-30", gracia: "5" };

function errores(cambios: Partial<ValoresDeCambioDePlan>) {
  const r = validarCambioDePlan({ ...DE_PAGO, ...cambios }, HOY);
  return "errores" in r ? r.errores : {};
}

/** El formulario de cambio de plan (#191): valida lo evidente antes de enviar; el backend sigue siendo la autoridad. */
describe("validarCambioDePlan", () => {
  it("con datos válidos arma el cuerpo: el vencimiento es la medianoche de Lima del día siguiente al último día pagado", () => {
    const r = validarCambioDePlan(DE_PAGO, HOY);

    expect(r).toEqual({ cuerpo: { plan_id: PLAN, vence_en: "2026-12-01T05:00:00.000Z", dias_de_gracia: 5 } });
  });

  it("sin días de gracia se manda cero", () => {
    expect(validarCambioDePlan({ ...DE_PAGO, gracia: "" }, HOY)).toMatchObject({ cuerpo: { dias_de_gracia: 0 } });
    expect(validarCambioDePlan({ ...DE_PAGO, gracia: "  " }, HOY)).toMatchObject({ cuerpo: { dias_de_gracia: 0 } });
  });

  it("hay que elegir un plan", () => {
    expect(errores({ planId: "" }).planId).toMatch(/Elige el plan/);
  });

  it("un plan de pago exige la fecha hasta la que está pagado", () => {
    expect(errores({ pagadoHasta: "" }).pagadoHasta).toMatch(/necesita la fecha/);
    expect(errores({ pagadoHasta: "  " }).pagadoHasta).toMatch(/necesita la fecha/);
  });

  it("un plan gratis no exige fecha y entonces no manda vencimiento", () => {
    const r = validarCambioDePlan({ ...DE_PAGO, precioDelPlan: 0, pagadoHasta: "" }, HOY);

    expect(r).toEqual({ cuerpo: { plan_id: PLAN, dias_de_gracia: 5 } });
    expect(r).not.toHaveProperty("cuerpo.vence_en");
  });

  it("un plan gratis que trae fecha la manda, y esa fecha también tiene que ser futura", () => {
    expect(validarCambioDePlan({ ...DE_PAGO, precioDelPlan: 0, pagadoHasta: "2026-11-30" }, HOY)).toMatchObject({ cuerpo: { vence_en: "2026-12-01T05:00:00.000Z" } });
    expect(errores({ precioDelPlan: 0, pagadoHasta: "2026-10-03" }).pagadoHasta).toMatch(/de hoy en adelante/);
  });

  it("la fecha tiene que ser de hoy en adelante: hoy mismo vale, ayer no", () => {
    expect(errores({ pagadoHasta: "2026-10-04" })).toEqual({});
    expect(errores({ pagadoHasta: "2026-10-03" }).pagadoHasta).toMatch(/de hoy en adelante/);
    expect(errores({ pagadoHasta: "2025-12-31" }).pagadoHasta).toMatch(/de hoy en adelante/);
  });

  it("una fecha mal escrita o que no existe se rechaza", () => {
    for (const mala of ["31/10/2026", "2026-13-01", "2026-02-30", "2026-10-00", "2026-04-31", "2026-1-5", "mañana", "2026-10-04T00:00", "2026-10-04-5", "x2026-10-04"]) expect(errores({ pagadoHasta: mala }).pagadoHasta, mala).toBeDefined();
  });

  it("los días de gracia son un entero de 0 a 90", () => {
    for (const bueno of ["0", "1", "90"]) expect(errores({ gracia: bueno }).gracia, bueno).toBeUndefined();
    for (const malo of ["-1", "91", "1.5", "abc", "1e1", "5 días"]) expect(errores({ gracia: malo }).gracia, malo).toMatch(/0 a 90/);
  });

  it("junta todos los errores a la vez", () => {
    expect(Object.keys(errores({ pagadoHasta: "", gracia: "99" })).sort()).toEqual(["gracia", "pagadoHasta"]);
    expect(Object.keys(errores({ planId: "", precioDelPlan: undefined, gracia: "99" })).sort()).toEqual(["gracia", "planId"]);
  });

  it("con errores no devuelve cuerpo", () => {
    expect(validarCambioDePlan({ ...DE_PAGO, planId: "" }, HOY)).not.toHaveProperty("cuerpo");
  });

  /** Antes de elegir el plan no se sabe su precio: la fecha no se exige hasta que se sepa que es de pago. */
  it("sin plan elegido solo se pide el plan", () => {
    expect(errores({ planId: "", precioDelPlan: undefined, pagadoHasta: "" })).toEqual({ planId: expect.any(String) });
  });
});
