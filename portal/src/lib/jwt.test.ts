import { describe, expect, it } from "vitest";
import { accesoExpirado, esSesionDeSoporte } from "./jwt";

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

function tokenConPayload(payload: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(payload)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}.firma`;
}

/** Solo para decidir si el middleware deja pasar una sesión sin refresh: lo que valga el token lo decide el backend (#184). */
describe("esSesionDeSoporte", () => {
  it("es true cuando el token trae el claim de soporte con el administrador", () => {
    expect(esSesionDeSoporte(tokenConPayload({ exp: 9999999999, imp: "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11" }))).toBe(true);
  });

  it("es false en una sesión normal, sin ese claim", () => {
    expect(esSesionDeSoporte(tokenConPayload({ exp: 9999999999, sub: "u1", cuenta: "c1" }))).toBe(false);
  });

  it("es false si el claim no es un texto con contenido", () => {
    for (const imp of [null, "", 0, 1, true, {}, []]) expect(esSesionDeSoporte(tokenConPayload({ imp })), JSON.stringify(imp)).toBe(false);
  });

  it("es false ante un token malformado o vacío", () => {
    for (const malo of ["no-es-un-jwt", "", "a.b.c", "a..c"]) expect(esSesionDeSoporte(malo), malo).toBe(false);
  });
});
