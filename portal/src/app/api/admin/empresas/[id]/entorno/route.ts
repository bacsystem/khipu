import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { cambiarEntornoEmpresa, ENTORNOS_DE_EMPRESA } from "@/lib/api/admin-acciones-empresa";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import type { EntornoAdmin } from "@/lib/api/admin-empresas";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Cambiar el entorno de una empresa (#187). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS; el id se valida antes de pegarlo en la URL
 * del backend; el entorno solo puede ser uno de los dos que existen; y la IP real del administrador (#208) viaja ya resuelta para la bitácora.
 * El cuerpo es `{ "entorno": "BETA" | "PRODUCCION" }`.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de empresa inválido");

  let entorno: EntornoAdmin;
  try {
    const cuerpo = JSON.parse(await req.text()) as { entorno?: unknown };
    if (typeof cuerpo.entorno !== "string" || !ENTORNOS_DE_EMPRESA.includes(cuerpo.entorno as EntornoAdmin)) return ERROR(422, "ENTORNO_INVALIDO", "El entorno debe ser BETA o PRODUCCION");
    entorno = cuerpo.entorno as EntornoAdmin;
  } catch {
    return ERROR(400, "JSON_INVALIDO", "Petición inválida");
  }

  try {
    const datos = await cambiarEntornoEmpresa(access, id, entorno, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    return errorResponse(err);
  }
}
