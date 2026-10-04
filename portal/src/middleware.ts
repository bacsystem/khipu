import { NextRequest, NextResponse } from "next/server";
import { refrescar } from "@/lib/api/auth";
import { esCuentaSuspendida, RUTA_CUENTA_SUSPENDIDA } from "@/lib/api/cuenta-suspendida";
import { accesoExpirado, esSesionDeSoporte } from "@/lib/jwt";
import { clearSession, readSession, writeTokens } from "@/lib/session";

function redirigirALogin(req: NextRequest) {
  const login = new URL("/login", req.url);
  login.searchParams.set("next", req.nextUrl.pathname);
  return NextResponse.redirect(login);
}

export async function middleware(req: NextRequest) {
  const { access, refresh } = readSession(req);
  if (!refresh) {
    // Una sesión de soporte (#184) no tiene refresh a propósito: vale mientras su access no venza y no se renueva. Cualquier otro access sin refresh no es
    // una sesión. Lo que valga el token lo decide el backend: aquí solo se decide si se intenta.
    if (access && esSesionDeSoporte(access) && !accesoExpirado(access)) return NextResponse.next();
    return redirigirALogin(req);
  }
  if (access && !accesoExpirado(access)) return NextResponse.next();

  try {
    const tokens = await refrescar(refresh);
    const res = NextResponse.next();
    writeTokens(res, tokens);
    return res;
  } catch (err) {
    // Una cuenta suspendida (#182) no es una sesión muerta: el backend no la revocó, y al reactivar la cuenta la misma sesión vuelve a servir.
    // Por eso se explica en una página pública y NO se limpian las cookies; cerrar la sesión es una acción explícita de esa página.
    if (esCuentaSuspendida(err)) return NextResponse.redirect(new URL(RUTA_CUENTA_SUSPENDIDA, req.url));
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
    "/cuenta/:path*",
  ],
};
