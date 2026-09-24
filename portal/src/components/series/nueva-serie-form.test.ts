import { describe, expect, it } from "vitest";
import { CORRELATIVO_MAXIMO, correlativoValido } from "./nueva-serie-form";

describe("correlativoValido", () => {
  it("acepta un correlativo de hasta 8 dígitos", () => {
    expect(correlativoValido("0")).toBe(0);
    expect(correlativoValido("1")).toBe(1);
    expect(correlativoValido(" 1234 ")).toBe(1234);
    expect(correlativoValido("99999999")).toBe(CORRELATIVO_MAXIMO);
  });

  // Regla 1001: el ID del comprobante es [FB][A-Z0-9]{3}-[0-9]{1,8}. Con nueve dígitos SUNAT rechaza el
  // comprobante con el correlativo ya consumido, y la serie no tiene endpoint para corregirse.
  it("rechaza lo que pasa los 8 dígitos", () => {
    expect(correlativoValido("100000000")).toBeNull();
    expect(correlativoValido("999999999999")).toBeNull();
  });

  // `type=number` deja el valor como texto: estas formas llegan al submit si el navegador no las filtra.
  it.each([
    ["vacío", ""],
    ["notación científica", "1e30"],
    ["decimal", "3.5"],
    ["negativo", "-1"],
    ["texto", "abc"],
    ["signo más", "+5"],
  ])("rechaza %s", (_caso, valor) => {
    expect(correlativoValido(valor)).toBeNull();
  });

  it("el tope es exactamente 8 dígitos", () => {
    expect(CORRELATIVO_MAXIMO).toBe(99_999_999);
    expect(String(CORRELATIVO_MAXIMO)).toHaveLength(8);
  });
});
