import { NextRequest, NextResponse } from "next/server";
import { loginAdministrador } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { writeAdminAccess } from "@/lib/admin-session";
import { cabecerasDeOrigen } from "@/lib/origen";

export async function POST(req: NextRequest) {
  const { email, password } = await req.json();
  try {
    // La IP real del administrador (#208), ya resuelta: la bitácora del login (#177) la necesitará.
    const sesion = await loginAdministrador(email, password, cabecerasDeOrigen(req.headers));
    const res = NextResponse.json({
      estado: "exito",
      datos: { administrador: sesion.administrador },
      mensaje: null,
      codigo: null,
      errores: null,
    });
    writeAdminAccess(res, sesion.access_token);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
