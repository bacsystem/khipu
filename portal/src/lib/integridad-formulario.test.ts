import { describe, expect, it } from "vitest";
import { MAX_DIAS_DE_INTEGRIDAD, validarRango } from "./integridad-formulario";

const errores = (desde: string, hasta: string) => {
  const r = validarRango(desde, hasta);
  if (!("errores" in r)) throw new Error("se esperaban errores");
  return r.errores;
};

describe("validarRango", () => {
  it("un rango válido devuelve las dos fechas recortadas", () => {
    expect(validarRango("2026-09-01", "2026-09-30")).toEqual({ desde: "2026-09-01", hasta: "2026-09-30" });
    expect(validarRango(" 2026-09-01 ", " 2026-09-30 ")).toEqual({ desde: "2026-09-01", hasta: "2026-09-30" });
  });

  it("un solo día es un rango válido", () => {
    expect(validarRango("2026-09-15", "2026-09-15")).toEqual({ desde: "2026-09-15", hasta: "2026-09-15" });
  });

  it("pide las dos fechas", () => {
    expect(errores("", "2026-09-30").desde).toBe("Indica desde cuándo verificar.");
    expect(errores("2026-09-01", "").hasta).toBe("Indica hasta cuándo verificar.");
    expect(errores("   ", "   ")).toEqual({ desde: "Indica desde cuándo verificar.", hasta: "Indica hasta cuándo verificar." });
  });

  it("las fechas tienen que existir y venir como AAAA-MM-DD", () => {
    for (const mala of ["2026-02-30", "2026-13-01", "2026-00-10", "01/09/2026", "ayer", "2026-9-1", "2026-09-01x"]) {
      expect(errores(mala, "2026-12-31").desde, mala).toBe("Esa fecha no existe.");
      expect(errores("2026-01-01", mala).hasta, mala).toBe("Esa fecha no existe.");
    }
  });

  it("no puede terminar antes de empezar", () => {
    expect(errores("2026-09-30", "2026-09-01").hasta).toBe("El rango no puede terminar antes de empezar.");
    expect(errores("2026-09-02", "2026-09-01").hasta).toBe("El rango no puede terminar antes de empezar.");
  });

  it("no pasa de 92 días contando los dos extremos: justo 92 vale, 93 no", () => {
    expect(MAX_DIAS_DE_INTEGRIDAD).toBe(92);
    // Del 1 de agosto al 31 de octubre son 92 días.
    expect(validarRango("2026-08-01", "2026-10-31")).toEqual({ desde: "2026-08-01", hasta: "2026-10-31" });
    expect(errores("2026-08-01", "2026-11-01").hasta).toBe("El rango no puede pasar de 92 días.");
  });

  it("un rango de años es demasiado largo", () => {
    expect(errores("2020-01-01", "2026-12-31").hasta).toBe("El rango no puede pasar de 92 días.");
  });

  it("si una fecha no existe no se compara con la otra", () => {
    expect(errores("2026-02-30", "2026-01-01")).toEqual({ desde: "Esa fecha no existe." });
  });
});
