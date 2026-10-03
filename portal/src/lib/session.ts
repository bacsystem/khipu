import type { NextRequest, NextResponse } from "next/server";

export const COOKIE_ACCESS = "factura_access";
export const COOKIE_REFRESH = "factura_refresh";
export const COOKIE_EMPRESA = "factura_empresa";

export const ACCESS_MAX_AGE = 15 * 60;
export const REFRESH_MAX_AGE = 30 * 24 * 60 * 60;

/**
 * `Secure`: el navegador solo guarda la cookie por HTTPS (salvo en `localhost`). Por defecto lo es en producción. `COOKIE_SECURE`
 * lo fuerza (`true`) o lo apaga (`false`): el despliegue de develop en Docker es HTTP local y se abre desde el celular por la IP de la
 * red, donde una cookie `Secure` nunca se guarda y el login no deja sesión. Solo valen los valores exactos `true` y `false`; con
 * cualquier otro (un typo, `False`, vacío) manda el default, para que un error de escritura no debilite producción.
 *
 * Este módulo también corre en el middleware (runtime edge, `writeTokens` al refrescar): verificado con la imagen que ahí respeta
 * `NODE_ENV` y `COOKIE_SECURE` igual que en Node. El parámetro es para los tests.
 */
export function cookieSegura(env: Record<string, string | undefined> = process.env): boolean {
  if (env.COOKIE_SECURE === "true") return true;
  if (env.COOKIE_SECURE === "false") return false;
  return env.NODE_ENV === "production";
}

export const baseCookie = {
  httpOnly: true,
  secure: cookieSegura(),
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

export function clearEmpresaActiva(res: NextResponse): void {
  res.cookies.set(COOKIE_EMPRESA, "", { ...baseCookie, maxAge: 0 });
}

export function clearSession(res: NextResponse): void {
  res.cookies.set(COOKIE_ACCESS, "", { ...baseCookie, maxAge: 0 });
  res.cookies.set(COOKIE_REFRESH, "", { ...baseCookie, maxAge: 0 });
  res.cookies.set(COOKIE_EMPRESA, "", { ...baseCookie, maxAge: 0 });
}
