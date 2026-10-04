import { NextRequest, NextResponse } from "next/server";
import { loginAdministrador } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { clearAdminSession, writeAdminDesafio } from "@/lib/admin-session";
import { cabecerasDeOrigen } from "@/lib/origen";

/**
 * Paso 1 del login del backoffice (#177): la contraseña. No da sesión: deja el desafío en una cookie httpOnly (el navegador nunca lo
 * ve) y responde qué paso sigue. Una sesión de administrador previa se cierra: entrar de nuevo empieza de cero.
 */
export async function POST(req: NextRequest) {
  const { email, password } = await req.json();
  try {
    const { desafio, paso } = await loginAdministrador(email, password, cabecerasDeOrigen(req.headers));
    const res = NextResponse.json({ estado: "exito", datos: { paso }, mensaje: null, codigo: null, errores: null });
    writeAdminDesafio(res, desafio);
    clearAdminSession(res);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
