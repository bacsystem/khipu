import { NextRequest, NextResponse } from "next/server";
import { describe, expect, it } from "vitest";
import { baseCookie, clearSession, COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH, cookieSegura, readSession, writeTokens } from "./session";

function requestWithCookies(cookies: Record<string, string>) {
  const header = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
  return new NextRequest("http://localhost/api/proxy/empresas", {
    headers: header ? { cookie: header } : undefined,
  });
}

describe("session", () => {
  it("lee access, refresh y empresa desde las cookies", () => {
    const req = requestWithCookies({
      [COOKIE_ACCESS]: "a1",
      [COOKIE_REFRESH]: "r1",
      [COOKIE_EMPRESA]: "e1",
    });

    expect(readSession(req)).toEqual({ access: "a1", refresh: "r1", empresa: "e1" });
  });

  it("no revienta cuando no hay cookies", () => {
    const req = requestWithCookies({});

    expect(readSession(req)).toEqual({ access: undefined, refresh: undefined, empresa: undefined });
  });

  it("writeTokens fija access y refresh como httpOnly", () => {
    const res = NextResponse.json({ ok: true });

    writeTokens(res, { access: "a1", refresh: "r1" });

    const access = res.cookies.get(COOKIE_ACCESS);
    const refresh = res.cookies.get(COOKIE_REFRESH);
    expect(access?.value).toBe("a1");
    expect(access?.httpOnly).toBe(true);
    expect(refresh?.value).toBe("r1");
  });

  it("clearSession vacía las tres cookies con maxAge 0", () => {
    const res = NextResponse.json({ ok: true });

    clearSession(res);

    expect(res.cookies.get(COOKIE_ACCESS)?.value).toBe("");
    expect(res.cookies.get(COOKIE_REFRESH)?.value).toBe("");
    expect(res.cookies.get(COOKIE_EMPRESA)?.value).toBe("");
  });
});

/**
 * `Secure` hace que el navegador solo guarde la cookie por HTTPS (con la excepción de `localhost`). El despliegue de develop en Docker
 * es HTTP local y se abre desde el celular por la IP de la red: ahí la sesión nunca se guardaba. `COOKIE_SECURE=false` lo apaga solo
 * donde se pide; en producción el default sigue siendo `Secure`.
 */
describe("cookieSegura", () => {
  it("en producción es Secure por defecto", () => {
    expect(cookieSegura({ NODE_ENV: "production" })).toBe(true);
  });

  it("fuera de producción no es Secure por defecto", () => {
    expect(cookieSegura({ NODE_ENV: "development" })).toBe(false);
    expect(cookieSegura({})).toBe(false);
  });

  it("COOKIE_SECURE=false la apaga aun en producción (despliegue HTTP local)", () => {
    expect(cookieSegura({ NODE_ENV: "production", COOKIE_SECURE: "false" })).toBe(false);
  });

  it("COOKIE_SECURE=true la fuerza aun fuera de producción", () => {
    expect(cookieSegura({ NODE_ENV: "development", COOKIE_SECURE: "true" })).toBe(true);
  });

  it("cualquier otro valor se ignora y vale el default: un typo no debilita producción", () => {
    for (const valor of ["", "0", "no", "off", "False", "FALSE", " false", "falso"]) {
      expect(cookieSegura({ NODE_ENV: "production", COOKIE_SECURE: valor }), `«${valor}»`).toBe(true);
    }
  });

  it("las cookies de sesión usan el valor calculado", () => {
    expect(baseCookie.secure).toBe(cookieSegura());
  });
});
