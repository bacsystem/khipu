import { NextRequest, NextResponse } from "next/server";
import { registrar } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { clearEmpresaActiva, writeTokens } from "@/lib/session";

export async function POST(req: NextRequest) {
  const { nombre, email, password, telefono } = await req.json();
  try {
    const tokens = await registrar(nombre, email, password, telefono);
    const res = NextResponse.json(
      { estado: "exito", datos: { usuario: tokens.usuario }, mensaje: null, codigo: null, errores: null },
      { status: 201 },
    );
    writeTokens(res, tokens);
    // Una cuenta recién creada no tiene empresas propias: si el navegador ya traía una cookie de
    // empresa activa de OTRA cuenta, limpiarla evita que se reenvíe como X-Empresa (403 al crear
    // la primera empresa, porque esa empresa no le pertenece a esta cuenta nueva).
    clearEmpresaActiva(res);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
