import { NextRequest, NextResponse } from "next/server";
import { verificarSegundoFactor } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { clearAdminDesafio, readAdminDesafio, writeAdminAccess } from "@/lib/admin-session";
import { cabecerasDeOrigen } from "@/lib/origen";
import { sinDesafio } from "../desafio";

/**
 * Paso 2 (#177): el código de la app o uno de recuperación abre la sesión. Un código equivocado no consume el desafío: se puede
 * reintentar hasta que venza o el backend bloquee por demasiados intentos.
 */
export async function POST(req: NextRequest) {
  const desafio = readAdminDesafio(req);
  if (!desafio) return sinDesafio();
  const { codigo } = await req.json();
  try {
    const sesion = await verificarSegundoFactor(desafio, codigo, cabecerasDeOrigen(req.headers));
    const res = NextResponse.json({ estado: "exito", datos: { administrador: sesion.administrador }, mensaje: null, codigo: null, errores: null });
    writeAdminAccess(res, sesion.access_token, sesion.expira_en);
    clearAdminDesafio(res);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
