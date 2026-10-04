import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { revocarApiKeyEmpresa } from "@/lib/api/admin-acciones-empresa";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Revocar una API key de una empresa (#187). Misma protección que el resto: el JWT del administrador nunca llega al JS y los dos ids se validan antes de pegarlos
 * en la URL del backend. La respuesta dice cuál key y cuándo, nunca la clave.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string; apiKeyId: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id, apiKeyId } = await params;
  if (!esUuid(id) || !esUuid(apiKeyId)) return ERROR(400, "ID_INVALIDO", "Identificador inválido");

  try {
    const datos = await revocarApiKeyEmpresa(access, id, apiKeyId, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    return errorResponse(err);
  }
}
