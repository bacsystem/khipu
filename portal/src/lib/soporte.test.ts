import { describe, expect, it } from "vitest";
import { correoDeSoporte, leerSoporte } from "./soporte";

describe("leerSoporte (#250)", () => {
  it("sin configurar no hay soporte: el portal no muestra nada", () => {
    expect(leerSoporte({})).toEqual({ url: null, email: null });
    expect(leerSoporte({ SUPPORT_URL: "  ", SUPPORT_EMAIL: "" })).toEqual({ url: null, email: null });
  });

  it("lee el enlace y el correo, sin espacios alrededor", () => {
    expect(leerSoporte({ SUPPORT_URL: " https://ayuda.khipu.pe/ ", SUPPORT_EMAIL: " soporte@khipu.pe " })).toEqual({
      url: "https://ayuda.khipu.pe/",
      email: "soporte@khipu.pe",
    });
  });

  it("cada uno es opcional por separado", () => {
    expect(leerSoporte({ SUPPORT_EMAIL: "soporte@khipu.pe" })).toEqual({ url: null, email: "soporte@khipu.pe" });
    expect(leerSoporte({ SUPPORT_URL: "https://ayuda.khipu.pe" })).toEqual({ url: "https://ayuda.khipu.pe", email: null });
  });

  it("un enlace que no es https se rechaza diciendo qué variable está mal", () => {
    expect(() => leerSoporte({ SUPPORT_URL: "http://ayuda.khipu.pe" })).toThrow(/SUPPORT_URL.*https/);
    expect(() => leerSoporte({ SUPPORT_URL: "ayuda.khipu.pe" })).toThrow(/SUPPORT_URL/);
    expect(() => leerSoporte({ SUPPORT_URL: "javascript:alert(1)" })).toThrow(/SUPPORT_URL/);
  });

  it("un correo mal formado se rechaza diciendo qué variable está mal", () => {
    expect(() => leerSoporte({ SUPPORT_EMAIL: "soporte" })).toThrow(/SUPPORT_EMAIL/);
    expect(() => leerSoporte({ SUPPORT_EMAIL: "soporte@khipu" })).toThrow(/SUPPORT_EMAIL/);
    expect(() => leerSoporte({ SUPPORT_EMAIL: "a b@khipu.pe" })).toThrow(/SUPPORT_EMAIL/);
    expect(() => leerSoporte({ SUPPORT_EMAIL: "soporte@khipu.pe?cc=otro@x.pe" })).toThrow(/SUPPORT_EMAIL/);
  });
});

describe("correoDeSoporte", () => {
  it("arma el mailto con el asunto codificado", () => {
    expect(correoDeSoporte("soporte@khipu.pe")).toBe("mailto:soporte@khipu.pe");
    expect(correoDeSoporte("soporte@khipu.pe", "Cuenta Bodega & Cía (c-1)")).toBe("mailto:soporte@khipu.pe?subject=Cuenta%20Bodega%20%26%20C%C3%ADa%20(c-1)");
  });
});
