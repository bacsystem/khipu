import type { NextRequest, NextResponse } from "next/server";
import { baseCookie } from "./session";

export const COOKIE_ADMIN_ACCESS = "khipu_admin_access";
/** El desafío del login (#177): la contraseña ya se comprobó y falta el segundo factor. No abre el backoffice. */
export const COOKIE_ADMIN_DESAFIO = "khipu_admin_desafio";

/** Respaldo si el backend no informa `expira_en`: la vida por defecto del JWT del backoffice. Sin refresh: al vencer, se vuelve a entrar. */
export const ADMIN_ACCESS_MAX_AGE = 30 * 60;
/** Igual que el desafío del backend (JwtAdministradorTokenEmisor). */
export const ADMIN_DESAFIO_MAX_AGE = 5 * 60;
/** El desafío solo lo leen las rutas del login: no viaja con el resto de las peticiones. */
const RUTA_DESAFIO = "/api/admin/auth";

export function readAdminSession(req: NextRequest): { access?: string } {
  return { access: req.cookies.get(COOKIE_ADMIN_ACCESS)?.value };
}

export function writeAdminAccess(res: NextResponse, access: string, maxAge: number = ADMIN_ACCESS_MAX_AGE): void {
  res.cookies.set(COOKIE_ADMIN_ACCESS, access, { ...baseCookie, maxAge });
}

export function clearAdminSession(res: NextResponse): void {
  res.cookies.set(COOKIE_ADMIN_ACCESS, "", { ...baseCookie, maxAge: 0 });
}

export function readAdminDesafio(req: NextRequest): string | undefined {
  return req.cookies.get(COOKIE_ADMIN_DESAFIO)?.value;
}

export function writeAdminDesafio(res: NextResponse, desafio: string): void {
  res.cookies.set(COOKIE_ADMIN_DESAFIO, desafio, { ...baseCookie, path: RUTA_DESAFIO, maxAge: ADMIN_DESAFIO_MAX_AGE });
}

export function clearAdminDesafio(res: NextResponse): void {
  res.cookies.set(COOKIE_ADMIN_DESAFIO, "", { ...baseCookie, path: RUTA_DESAFIO, maxAge: 0 });
}
