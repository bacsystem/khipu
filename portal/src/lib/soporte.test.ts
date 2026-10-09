import { describe, expect, it, vi } from "vitest";
import { correoDeSoporte, leerSoporte, SIN_SOPORTE, soporteParaMostrar } from "./soporte";

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

  /** 272-H2: una coma o un punto y coma en el correo son dos destinatarios en el mailto. */
  it("si el correo trae coma, punto y coma o % lo rechaza: sería más de un destinatario", () => {
    for (const malo of ["a,b@khipu.pe", "soporte@khipu.pe;otro@x.pe", "a%40b@khipu.pe"]) expect(() => leerSoporte({ SUPPORT_EMAIL: malo }), malo).toThrow(/SUPPORT_EMAIL/);
    expect(leerSoporte({ SUPPORT_EMAIL: "soporte+ayuda@mi-empresa.com.pe" }).email).toBe("soporte+ayuda@mi-empresa.com.pe");
  });
});

describe("correoDeSoporte", () => {
  /**
   * 272-H1: un valor mal escrito no tumba el portal. En producción el error de `register()` cerraba el proceso (Next 15), y el login caía por una
   * variable opcional. Lo que se muestra degrada a «sin soporte» y el error queda en el log, con el nombre de la variable.
   */
  it("para mostrar, un valor inválido se registra en el log y no muestra ayuda, sin lanzar", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(soporteParaMostrar({ SUPPORT_URL: "http://ayuda.khipu.pe", SUPPORT_EMAIL: "soporte@khipu.pe" })).toEqual(SIN_SOPORTE);
    expect(error).toHaveBeenCalledWith(expect.stringContaining("SUPPORT_URL"));
    expect(soporteParaMostrar({ SUPPORT_EMAIL: "soporte@khipu.pe" })).toEqual({ url: null, email: "soporte@khipu.pe" });
    error.mockRestore();
  });

  it("arma el mailto con el asunto codificado", () => {
    expect(correoDeSoporte("soporte@khipu.pe")).toBe("mailto:soporte@khipu.pe");
    expect(correoDeSoporte("soporte@khipu.pe", "Cuenta Bodega & Cía (c-1)")).toBe("mailto:soporte@khipu.pe?subject=Cuenta%20Bodega%20%26%20C%C3%ADa%20(c-1)");
  });
});
