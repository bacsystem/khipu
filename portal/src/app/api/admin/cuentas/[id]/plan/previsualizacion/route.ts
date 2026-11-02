import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { previsualizarCambioDePlan } from "@/lib/api/admin-plan-de-cuenta";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import { ERROR, sinCache } from "../../../../planes/comun";

/**
 * Lo que pasaría con un cambio de plan, sin hacerlo (#191): cuándo entra y qué pasa con el consumo del mes. Es de solo lectura, pero igual pide la sesión del
 * administrador y valida los dos ids antes de pegarlos en la URL del backend.
 */
export async function GET(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de cuenta inválido");
  const planId = req.nextUrl.searchParams.get("plan_id");
  if (!planId || !esUuid(planId)) return ERROR(400, "PLAN_INVALIDO", "Identificador de plan inválido");

  try {
    const datos = await previsualizarCambioDePlan(access, id, planId, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
