import { NextRequest, NextResponse } from "next/server";
import { reenviarVerificacion } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { readSession } from "@/lib/session";

/** Otro enlace de verificación al correo del usuario de la sesión (#22). */
export async function POST(req: NextRequest) {
  const { access } = readSession(req);
  if (!access)
    return NextResponse.json({ estado: "error", datos: null, mensaje: "Sesión requerida", codigo: "NO_AUTORIZADO", errores: null }, { status: 401 });
  try {
    await reenviarVerificacion(access);
    return NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }, { status: 202 });
  } catch (err) {
    return errorResponse(err);
  }
}
