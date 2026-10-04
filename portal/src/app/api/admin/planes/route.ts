import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { crearPlan } from "@/lib/api/admin-planes";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { ERROR, leerCuerpoDePlan, sinCache } from "./comun";

/** Crear un plan (#190). Las reglas de cada dato (nombre único, precio, límites mayores que cero) las pone el backend; acá solo se cuida la sesión y la forma. */
export async function POST(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const leido = await leerCuerpoDePlan(req);
  if ("error" in leido) return leido.error;

  try {
    const datos = await crearPlan(access, leido.cuerpo, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { status: 201, ...sinCache });
  } catch (err) {
    return errorResponse(err);
  }
}
