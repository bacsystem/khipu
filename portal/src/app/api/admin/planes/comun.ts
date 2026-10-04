import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";

export const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

export const sinCache = { headers: { "Cache-Control": "no-store" } };

/**
 * Lo común de las rutas del BFF de planes (#190). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS; el id se valida antes de pegarlo en
 * la URL del backend; y la IP real del administrador (#208) viaja ya resuelta para que la bitácora la registre. Ninguna respuesta queda en caché.
 */
export async function sobrePlan(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
  hacer: (access: string, id: string, origen: Record<string, string>) => Promise<unknown>,
) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de plan inválido");

  try {
    const datos = await hacer(access, id, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}

/** El cuerpo de crear o editar: un objeto JSON, que se reenvía sin tocarlo (las reglas de cada dato las pone el backend, una sola vez). */
export async function leerCuerpoJson(req: NextRequest): Promise<{ cuerpo: object } | { error: NextResponse }> {
  try {
    const cuerpo: unknown = JSON.parse(await req.text());
    if (cuerpo === null || typeof cuerpo !== "object" || Array.isArray(cuerpo)) return { error: ERROR(400, "JSON_INVALIDO", "Petición inválida") };
    return { cuerpo };
  } catch {
    return { error: ERROR(400, "JSON_INVALIDO", "Petición inválida") };
  }
}
