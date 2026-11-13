import { NextRequest, NextResponse } from "next/server";
import { aceptarInvitacion } from "@/lib/api/equipo";
import { leerCuerpoJson } from "@/lib/api/cuerpo-json";
import { errorResponse } from "@/lib/api/http";

/** #21: el invitado acepta con el token de su enlace y la contraseña que elige. Público: todavía no tiene usuario. */
export async function POST(req: NextRequest) {
  const leido = await leerCuerpoJson<{ token: string; password: string }>(req);
  if ("error" in leido) return leido.error;
  const { token, password } = leido.cuerpo;
  try {
    await aceptarInvitacion(token, password);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return errorResponse(err);
  }
}
