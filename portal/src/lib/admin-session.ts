import type { NextRequest, NextResponse } from "next/server";
import { baseCookie } from "./session";

export const COOKIE_ADMIN_ACCESS = "khipu_admin_access";

/** Igual a la vida del JWT del backoffice (JwtAdministradorTokenEmisor). Sin refresh: al vencer, se vuelve a iniciar sesión. */
export const ADMIN_ACCESS_MAX_AGE = 30 * 60;

export function readAdminSession(req: NextRequest): { access?: string } {
  return { access: req.cookies.get(COOKIE_ADMIN_ACCESS)?.value };
}

export function writeAdminAccess(res: NextResponse, access: string): void {
  res.cookies.set(COOKIE_ADMIN_ACCESS, access, { ...baseCookie, maxAge: ADMIN_ACCESS_MAX_AGE });
}

export function clearAdminSession(res: NextResponse): void {
  res.cookies.set(COOKIE_ADMIN_ACCESS, "", { ...baseCookie, maxAge: 0 });
}
