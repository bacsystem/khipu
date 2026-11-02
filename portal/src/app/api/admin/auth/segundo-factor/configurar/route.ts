import { NextRequest, NextResponse } from "next/server";
import { configurarSegundoFactor } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { readAdminDesafio } from "@/lib/admin-session";
import { sinDesafio } from "../desafio";

/** Paso 2a (#177): el QR y el secreto para la app de autenticación. El secreto viaja al navegador porque el administrador lo necesita. */
export async function POST(req: NextRequest) {
  const desafio = readAdminDesafio(req);
  if (!desafio) return sinDesafio();
  try {
    const configuracion = await configurarSegundoFactor(desafio);
    return NextResponse.json({ estado: "exito", datos: configuracion, mensaje: null, codigo: null, errores: null });
  } catch (err) {
    return errorResponse(err);
  }
}
