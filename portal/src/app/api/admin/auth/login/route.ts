import { NextRequest, NextResponse } from "next/server";
import { loginAdministrador } from "@/lib/api/admin-auth";
import { errorResponse } from "@/lib/api/http";
import { writeAdminAccess } from "@/lib/admin-session";

export async function POST(req: NextRequest) {
  const { email, password } = await req.json();
  try {
    const sesion = await loginAdministrador(email, password);
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
