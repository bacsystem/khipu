import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { enviarRestablecimiento } from "@/lib/api/admin-acceso";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Mandarle a un usuario el correo para restablecer su contraseña (#183). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al
 * JS; los dos ids se validan antes de pegarlos en la URL del backend; y la IP real del administrador (#208) viaja ya resuelta para la bitácora.
 * La respuesta dice a quién se le mandó, nada más: ni el enlace ni su token.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string; usuarioId: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id, usuarioId } = await params;
  if (!esUuid(id) || !esUuid(usuarioId)) return ERROR(400, "ID_INVALIDO", "Identificador inválido");

  try {
    const datos = await enviarRestablecimiento(access, id, usuarioId, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    return errorResponse(err);
  }
}
