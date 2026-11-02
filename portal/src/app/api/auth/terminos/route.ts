import { NextRequest, NextResponse } from "next/server";
import { aceptarTerminos, refrescar, type AceptacionDeTerminos } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { ApiError } from "@/lib/api/types";
import { readSession, writeTokens } from "@/lib/session";

function exito(aceptacion: AceptacionDeTerminos) {
  return NextResponse.json({ estado: "exito", datos: aceptacion, mensaje: null, codigo: null, errores: null });
}

/**
 * El usuario de la sesión acepta la versión vigente de los términos y la política de privacidad (#342). Si el access venció mientras leía los
 * textos (vive 15 minutos), se renueva una vez con el refresh y se reintenta, como hace el proxy: si no, aceptar fallaba con 401 y el usuario
 * no entendía por qué.
 */
export async function POST(req: NextRequest) {
  const { access, refresh } = readSession(req);
  if (!access && !refresh)
    return NextResponse.json({ estado: "error", datos: null, mensaje: "Sesión requerida", codigo: "NO_AUTORIZADO", errores: null }, { status: 401 });
  try {
    if (access) {
      try {
        return exito(await aceptarTerminos(access));
      } catch (err) {
        if (!(err instanceof ApiError && err.status === 401) || !refresh) throw err;
      }
    }
    const tokens = await refrescar(refresh!);
    const res = exito(await aceptarTerminos(tokens.access));
    writeTokens(res, tokens);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
