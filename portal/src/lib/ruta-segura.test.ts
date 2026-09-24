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

  // El navegador descarta estos caracteres al parsear la URL: lo que queda es "//evil.com".
  it.each([
    ["salto de línea", "/\n/evil.com"],
    ["retorno de carro", "/\r/evil.com"],
    ["tab", "/\t/evil.com"],
    ["nulo", "/\0/evil.com"],
  ])("rechaza un next con %s intercalado", (_caso, next) => {
    expect(rutaSegura(next)).toBe("/comprobantes");
  });

  it("rechaza una barra invertida en cualquier posición, no solo al inicio", () => {
    expect(rutaSegura("/comprobantes\\@evil.com")).toBe("/comprobantes");
  });

  it("acepta una ruta interna con query y fragmento", () => {
    expect(rutaSegura("/comprobantes?serie=F001#detalle")).toBe("/comprobantes?serie=F001#detalle");
  });
});
