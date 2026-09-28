import { NextResponse } from "next/server";
import { clearAdminSession } from "@/lib/admin-session";

/** Sesión sin refresh (JWT stateless de 30 min): cerrarla es solo borrar la cookie, no hay nada que revocar en el backend. */
export async function POST() {
  const res = NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  clearAdminSession(res);
  return res;
}
