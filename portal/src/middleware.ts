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

export const config = {
  matcher: ["/onboarding/:path*", "/comprobantes/:path*", "/empresa/:path*", "/series/:path*", "/api-keys/:path*"],
};
