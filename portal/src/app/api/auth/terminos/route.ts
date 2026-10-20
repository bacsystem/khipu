import { NextRequest, NextResponse } from "next/server";
import { aceptarTerminos } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { readSession } from "@/lib/session";

/** El usuario de la sesión acepta la versión vigente de los términos y la política de privacidad (#342). */
export async function POST(req: NextRequest) {
  const { access } = readSession(req);
  if (!access)
    return NextResponse.json({ estado: "error", datos: null, mensaje: "Sesión requerida", codigo: "NO_AUTORIZADO", errores: null }, { status: 401 });
  try {
    const aceptacion = await aceptarTerminos(access);
    return NextResponse.json({ estado: "exito", datos: aceptacion, mensaje: null, codigo: null, errores: null });
  } catch (err) {
    return errorResponse(err);
  }
}
