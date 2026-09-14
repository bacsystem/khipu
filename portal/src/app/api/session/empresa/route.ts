import { NextRequest, NextResponse } from "next/server";
import { writeEmpresaActiva } from "@/lib/session";

export async function POST(req: NextRequest) {
  const { empresaId } = await req.json();
  const res = NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  writeEmpresaActiva(res, empresaId);
  return res;
}
