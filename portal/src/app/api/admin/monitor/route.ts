import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { obtenerMonitor } from "@/lib/api/admin-monitor";
import { errorResponse } from "@/lib/api/http";
import { ERROR, sinCache } from "../planes/comun";

/**
 * La lectura del monitor de emisión (#195), que la pantalla pide sola cada 30 segundos. El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS.
 * Solo lee, así que no deja bitácora ni reenvía la IP; la respuesta no se guarda en caché (una lectura vieja del monitor es peor que ninguna).
 */
export async function GET(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  try {
    const datos = await obtenerMonitor(access);
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
