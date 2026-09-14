import { describe, expect, it } from "vitest";
import { accesoExpirado } from "./jwt";

function tokenCon(exp: number): string {
  const payload = btoa(JSON.stringify({ exp })).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `header.${payload}.firma`;
}

describe("accesoExpirado", () => {
  it("es true cuando exp ya pasó", () => {
    expect(accesoExpirado(tokenCon(Math.floor(Date.now() / 1000) - 60))).toBe(true);
  });

  it("es false cuando exp está lejos en el futuro", () => {
    expect(accesoExpirado(tokenCon(Math.floor(Date.now() / 1000) + 900))).toBe(false);
  });

  it("es true cuando exp está dentro del margen de seguridad", () => {
    expect(accesoExpirado(tokenCon(Math.floor(Date.now() / 1000) + 2), 5_000)).toBe(true);
  });

  it("es true ante un token malformado", () => {
    expect(accesoExpirado("no-es-un-jwt")).toBe(true);
  });
});
