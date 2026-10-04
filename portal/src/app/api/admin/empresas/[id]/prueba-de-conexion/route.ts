import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { probarConexionEmpresa } from "@/lib/api/admin-acciones-empresa";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Probar la conexión de una empresa con SUNAT (#187). Misma protección que el resto. La respuesta dice cómo contestó SUNAT, nunca las credenciales SOL.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de empresa inválido");

  try {
    const datos = await probarConexionEmpresa(access, id, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    return errorResponse(err);
  }
}
