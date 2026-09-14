import { NextRequest, NextResponse } from "next/server";
import { logout } from "@/lib/api/auth";
import { clearSession, readSession } from "@/lib/session";

export async function POST(req: NextRequest) {
  const { access, refresh } = readSession(req);
  const res = NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  clearSession(res);
  if (access && refresh) {
    await logout(access, refresh).catch(() => undefined);
  }
  return res;
}
