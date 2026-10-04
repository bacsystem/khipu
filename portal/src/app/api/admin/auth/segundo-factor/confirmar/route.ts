import { NextRequest, NextResponse } from "next/server";
import { confirmarSegundoFactor } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { clearAdminDesafio, readAdminDesafio, writeAdminAccess } from "@/lib/admin-session";
import { cabecerasDeOrigen } from "@/lib/origen";
import { sinDesafio } from "../desafio";

/**
 * Paso 2b (#177): el primer código confirma el segundo factor y abre la sesión. Los códigos de recuperación pasan al navegador una
 * sola vez, para que el administrador los guarde; el token de sesión, nunca.
 */
export async function POST(req: NextRequest) {
  const desafio = readAdminDesafio(req);
  if (!desafio) return sinDesafio();
  const { codigo } = await req.json();
  try {
    const sesion = await confirmarSegundoFactor(desafio, codigo, cabecerasDeOrigen(req.headers));
    const res = NextResponse.json({
      estado: "exito",
      datos: { administrador: sesion.administrador, codigos_recuperacion: sesion.codigos_recuperacion },
      mensaje: null,
      codigo: null,
      errores: null,
    });
    writeAdminAccess(res, sesion.access_token, sesion.expira_en);
    clearAdminDesafio(res);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
