import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { altaAsistida, type AltaAsistida } from "@/lib/api/admin-alta";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Alta asistida de un cliente (#188). El navegador manda el formulario; el JWT del administrador sale de su cookie `httpOnly` y
 * nunca llega al JS. La respuesta lleva la API key inicial, que se muestra una sola vez: por eso `no-store`.
 */
export async function POST(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  let cuerpo: AltaAsistida;
  try {
    cuerpo = await req.json();
  } catch {
    return ERROR(400, "JSON_INVALIDO", "Petición inválida");
  }

  try {
    // La IP real del administrador (#208), ya resuelta: la bitácora del alta la registra.
    const datos = await altaAsistida(access, cuerpo, cabecerasDeOrigen(req.headers));
    return NextResponse.json(
      { estado: "exito", datos, mensaje: null, codigo: null, errores: null },
      { status: 201, headers: { "Cache-Control": "no-store" } },
    );
  } catch (err) {
    return errorResponse(err);
  }
}
