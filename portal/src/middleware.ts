import { NextRequest, NextResponse } from "next/server";
import { refrescar } from "@/lib/api/auth";
import { accesoExpirado } from "@/lib/jwt";
import { clearSession, readSession, writeTokens } from "@/lib/session";

function redirigirALogin(req: NextRequest) {
  const login = new URL("/login", req.url);
  login.searchParams.set("next", req.nextUrl.pathname);
  return NextResponse.redirect(login);
}

export async function middleware(req: NextRequest) {
  const { access, refresh } = readSession(req);
  if (!refresh) return redirigirALogin(req);
  if (access && !accesoExpirado(access)) return NextResponse.next();

  try {
    const tokens = await refrescar(refresh);
    const res = NextResponse.next();
    writeTokens(res, tokens);
    return res;
  } catch {
    const res = redirigirALogin(req);
    clearSession(res);
    return res;
  }
}

/**
 * Una ruta privada que falte acá no queda abierta —el backend sigue exigiendo el JWT—, pero nadie
 * le refresca el access token antes de que rendericen los Server Components, así que al usuario lo
 * expulsa al login cuando vence (a los 15 minutos) aunque su sesión siga viva. Next exige que el
 * matcher sea un literal analizable en build, así que no se puede derivar del árbol de `app/`:
 * `middleware.test.ts` compara esta lista contra los directorios de `app/(privado)/` para que
 * agregar una página nueva y olvidarse de esta línea rompa los tests.
 */
export const config = {
  matcher: [
    "/onboarding/:path*",
    "/comprobantes/:path*",
    "/empresa/:path*",
    "/establecimientos/:path*",
    "/series/:path*",
    "/api-keys/:path*",
  ],
};
