import { NextRequest, NextResponse } from "next/server";
import { recuperar } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";

export async function POST(req: NextRequest) {
  const { email } = await req.json();
  try {
    await recuperar(email);
    return NextResponse.json(
      { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null },
      { status: 202 },
    );
  } catch (err) {
    return errorResponse(err);
  }
}
