import { describe, expect, it } from "vitest";
import { faltaElDistrito } from "./datos-fiscales-form";

describe("faltaElDistrito", () => {
  it("con el distrito elegido nunca falta", () => {
    expect(faltaElDistrito("150122", "Av. Larco 345", "")).toBe(false);
    expect(faltaElDistrito("150122", "", "")).toBe(false);
  });

  // El caso que rompía: la dirección escrita se descartaba, el domicilio guardado se borraba, y el portal
  // informaba «Datos fiscales actualizados».
  it("falta cuando hay dirección sin distrito", () => {
    expect(faltaElDistrito("", "Av. Larco 345", "")).toBe(true);
  });

  it("falta también cuando solo se cargó la urbanización", () => {
    expect(faltaElDistrito("", "", "Santa Cruz")).toBe(true);
  });

  it("no falta con el formulario de domicilio vacío: eso es borrarlo a propósito", () => {
    expect(faltaElDistrito("", "", "")).toBe(false);
    expect(faltaElDistrito("", "   ", "  ")).toBe(false);
  });
});
