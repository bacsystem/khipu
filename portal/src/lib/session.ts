import type { NextRequest, NextResponse } from "next/server";

export const COOKIE_ACCESS = "factura_access";
export const COOKIE_REFRESH = "factura_refresh";
export const COOKIE_EMPRESA = "factura_empresa";

const ACCESS_MAX_AGE = 15 * 60;
const REFRESH_MAX_AGE = 30 * 24 * 60 * 60;

const baseCookie = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
};

export type Session = {
  access?: string;
  refresh?: string;
  empresa?: string;
};

export function readSession(req: NextRequest): Session {
  return {
    access: req.cookies.get(COOKIE_ACCESS)?.value,
    refresh: req.cookies.get(COOKIE_REFRESH)?.value,
    empresa: req.cookies.get(COOKIE_EMPRESA)?.value,
  };
}

export function writeTokens(res: NextResponse, tokens: { access: string; refresh: string }): void {
  res.cookies.set(COOKIE_ACCESS, tokens.access, { ...baseCookie, maxAge: ACCESS_MAX_AGE });
  res.cookies.set(COOKIE_REFRESH, tokens.refresh, { ...baseCookie, maxAge: REFRESH_MAX_AGE });
}

export function writeEmpresaActiva(res: NextResponse, empresaId: string): void {
  res.cookies.set(COOKIE_EMPRESA, empresaId, { ...baseCookie, maxAge: REFRESH_MAX_AGE });
}

export function clearSession(res: NextResponse): void {
  res.cookies.set(COOKIE_ACCESS, "", { ...baseCookie, maxAge: 0 });
  res.cookies.set(COOKIE_REFRESH, "", { ...baseCookie, maxAge: 0 });
  res.cookies.set(COOKIE_EMPRESA, "", { ...baseCookie, maxAge: 0 });
}
