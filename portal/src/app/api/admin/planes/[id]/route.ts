import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { editarPlan, eliminarPlan } from "@/lib/api/admin-planes";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import { ERROR, leerCuerpoJson, sinCache, sobrePlan } from "../comun";

type Contexto = { params: Promise<{ id: string }> };

/** Editar un plan (#190): nombre y precio al instante, los límites desde el ciclo siguiente (lo decide el backend). */
export async function PUT(req: NextRequest, { params }: Contexto) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de plan inválido");

  const leido = await leerCuerpoJson(req);
  if ("error" in leido) return leido.error;

  try {
    const datos = await editarPlan(access, id, leido.cuerpo, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}

/** Borrar un plan que nadie usó nunca (#190). Con cuentas, o con historial, el backend responde 409 PLAN_EN_USO. */
export function DELETE(req: NextRequest, ctx: Contexto) {
  return sobrePlan(req, ctx, eliminarPlan);
}
