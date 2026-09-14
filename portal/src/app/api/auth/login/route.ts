import { NextRequest, NextResponse } from "next/server";
import { login } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { writeTokens } from "@/lib/session";

export async function POST(req: NextRequest) {
  const { email, password } = await req.json();
  try {
    const tokens = await login(email, password);
    const res = NextResponse.json({
      estado: "exito",
      datos: { usuario: tokens.usuario },
      mensaje: null,
      codigo: null,
      errores: null,
    });
    writeTokens(res, tokens);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
