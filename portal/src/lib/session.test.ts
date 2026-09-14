import { NextRequest, NextResponse } from "next/server";
import { describe, expect, it } from "vitest";
import { clearSession, COOKIE_ACCESS, COOKIE_EMPRESA, COOKIE_REFRESH, readSession, writeTokens } from "./session";

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
