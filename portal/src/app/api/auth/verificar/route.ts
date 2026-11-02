import { NextRequest, NextResponse } from "next/server";
import { verificarCorreo } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";

/** Verificación del correo con el token del enlace (#22). No pide sesión: el enlace puede abrirse en el teléfono. */
export async function POST(req: NextRequest) {
  const { token } = await req.json();
  try {
    await verificarCorreo(token);
    return NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  } catch (err) {
    return errorResponse(err);
  }
}
