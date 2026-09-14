import { describe, expect, it } from "vitest";
import { rutaSegura } from "./ruta-segura";

describe("rutaSegura", () => {
  it("acepta una ruta interna relativa", () => {
    expect(rutaSegura("/empresa")).toBe("/empresa");
  });

  it("usa el destino por defecto cuando no hay next", () => {
    expect(rutaSegura(null)).toBe("/comprobantes");
  });

  it("rechaza una URL absoluta a otro origen", () => {
    expect(rutaSegura("https://evil.com")).toBe("/comprobantes");
  });

  it("rechaza un protocolo-relativo //evil.com", () => {
    expect(rutaSegura("//evil.com")).toBe("/comprobantes");
  });

  it("rechaza /\\evil.com (algunos navegadores lo tratan como protocolo-relativo)", () => {
    expect(rutaSegura("/\\evil.com")).toBe("/comprobantes");
  });
});
