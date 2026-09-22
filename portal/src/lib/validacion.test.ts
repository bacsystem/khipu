import { describe, expect, it } from "vitest";
import { telefonoSchema } from "./validacion";

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
