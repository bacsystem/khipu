import { describe, expect, it } from "vitest";
import { errorDeRetencion, RETENCION_VACIA, retencionAApi, type RetencionForm } from "./retencion";

const ret = (p: Partial<RetencionForm>): RetencionForm => ({ ...RETENCION_VACIA, activa: true, ...p });

describe("retención del IGV (#313)", () => {
  it("sin activar no hay nada que decir ni que mandar", () => {
    expect(errorDeRetencion(RETENCION_VACIA)).toBeNull();
    expect(retencionAApi(RETENCION_VACIA)).toBeUndefined();
  });

  it("arranca en la tasa legal de 3 %", () => {
    expect(RETENCION_VACIA.porcentaje).toBe("3");
    expect(retencionAApi(ret({}))).toEqual({ porcentaje: 3 });
  });

  it("el porcentaje es mayor que 0 y menor que 100, con hasta 3 decimales", () => {
    for (const bueno of ["3", "0.5", "99.999", " 6 "]) expect(errorDeRetencion(ret({ porcentaje: bueno })), bueno).toBeNull();
    for (const malo of ["", "0", "100", "-3", "abc", "3.1234"]) expect(errorDeRetencion(ret({ porcentaje: malo })), malo).toMatch(/mayor que 0 y menor que 100/);
    expect(retencionAApi(ret({ porcentaje: "0" }))).toBeUndefined();
  });
});
