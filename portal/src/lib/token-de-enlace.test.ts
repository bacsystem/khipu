import { describe, expect, it } from "vitest";
import { tokenDeEnlace } from "./token-de-enlace";

describe("tokenDeEnlace", () => {
  it("decodifica un token reescrito por el cliente de correo", () => {
    expect(tokenDeEnlace("abc%2Ddef")).toBe("abc-def");
    expect(tokenDeEnlace("q8Zf3kV1x_")).toBe("q8Zf3kV1x_");
  });

  it("un % suelto no rompe la página: es un enlace inválido", () => {
    expect(tokenDeEnlace("%E0%A4%A")).toBeNull();
    expect(tokenDeEnlace("abc%")).toBeNull();
  });
});
