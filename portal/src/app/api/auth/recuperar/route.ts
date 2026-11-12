import { NextRequest, NextResponse } from "next/server";
import { recuperar } from "@/lib/api/auth";
import { leerCuerpoJson } from "@/lib/api/cuerpo-json";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";

export async function POST(req: NextRequest) {
  const leido = await leerCuerpoJson<{ email: string }>(req);
  if ("error" in leido) return leido.error;
  const { email } = leido.cuerpo;
  try {
    await recuperar(email, cabecerasDeOrigen(req.headers));
    return NextResponse.json(
      { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null },
      { status: 202 },
    );
  } catch (err) {
    return errorResponse(err);
  }
}
