import { NextRequest, NextResponse } from "next/server";
import { registrar } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { writeTokens } from "@/lib/session";

export async function POST(req: NextRequest) {
  const { nombre, email, password } = await req.json();
  try {
    const tokens = await registrar(nombre, email, password);
    const res = NextResponse.json(
      { estado: "exito", datos: { usuario: tokens.usuario }, mensaje: null, codigo: null, errores: null },
      { status: 201 },
    );
    writeTokens(res, tokens);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
