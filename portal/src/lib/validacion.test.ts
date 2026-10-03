import { describe, expect, it } from "vitest";
import { serieCoincideConTipo, telefonoSchema } from "./validacion";

/** SUNAT 1001: la serie de una factura es `F` + 3 alfanuméricos y la de una boleta, `B` + 3. */
describe("serieCoincideConTipo", () => {
  it("una factura lleva F y una boleta lleva B, más 3 letras o dígitos", () => {
    expect(serieCoincideConTipo("01", "F001")).toBe(true);
    expect(serieCoincideConTipo("01", "FAB1")).toBe(true);
    expect(serieCoincideConTipo("03", "B001")).toBe(true);
  });

  it("rechaza la serie del otro tipo", () => {
    expect(serieCoincideConTipo("01", "B001")).toBe(false);
    expect(serieCoincideConTipo("03", "F001")).toBe(false);
  });

  it("rechaza lo que no tiene exactamente 4 caracteres", () => {
    expect(serieCoincideConTipo("01", "F01")).toBe(false);
    expect(serieCoincideConTipo("01", "F0001")).toBe(false);
    expect(serieCoincideConTipo("01", "")).toBe(false);
  });

  it("la compara en mayúsculas y sin espacios alrededor, como la normaliza el formulario", () => {
    expect(serieCoincideConTipo("01", " f001 ")).toBe(true);
  });

  it("rechaza símbolos y un tipo que no sea factura ni boleta", () => {
    expect(serieCoincideConTipo("01", "F0-1")).toBe(false);
    expect(serieCoincideConTipo("07", "FC01")).toBe(false);
  });
});

describe("telefonoSchema", () => {
  it("acepta 9 dígitos que empiezan con 9", () => {
    expect(telefonoSchema.parse("987654321")).toBe("987654321");
  });

  it("admite el prefijo +51/51 y espacios o guiones, y los normaliza", () => {
    expect(telefonoSchema.parse("+51 987 654 321")).toBe("+51987654321");
    expect(telefonoSchema.parse("51987654321")).toBe("51987654321");
    expect(telefonoSchema.parse("987-654-321")).toBe("987654321");
  });

  it("rechaza lo que no sea un celular peruano de 9 dígitos", () => {
    expect(() => telefonoSchema.parse("")).toThrow();
    expect(() => telefonoSchema.parse("123456789")).toThrow();  // no empieza con 9
    expect(() => telefonoSchema.parse("98765432")).toThrow();   // 8 dígitos
    expect(() => telefonoSchema.parse("9876543210")).toThrow(); // 10 dígitos
  });
});
