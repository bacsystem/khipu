import { NextRequest, NextResponse } from "next/server";
import { readSession } from "@/lib/session";

export function middleware(req: NextRequest) {
  const { refresh } = readSession(req);
  if (!refresh) {
    const login = new URL("/login", req.url);
    login.searchParams.set("next", req.nextUrl.pathname);
    return NextResponse.redirect(login);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/onboarding/:path*", "/comprobantes/:path*", "/empresa/:path*", "/series/:path*", "/api-keys/:path*"],
};
