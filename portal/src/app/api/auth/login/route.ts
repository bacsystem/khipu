import { NextRequest, NextResponse } from "next/server";
import { login } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { listarEmpresas } from "@/lib/api/empresas";
import { clearEmpresaActiva, readSession, writeEmpresaActiva, writeTokens } from "@/lib/session";

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

    const empresas = await listarEmpresas(tokens.access).catch(() => []);
    if (empresas.length > 0) {
      const { empresa } = readSession(req);
      const activa = empresas.find((e) => e.id === empresa) ?? empresas[0];
      writeEmpresaActiva(res, activa.id);
    } else {
      // Una cookie de empresa activa de OTRA cuenta (sesión anterior en el mismo navegador) se
      // reenvía como X-Empresa en el siguiente request al backend, que la rechaza con 403 porque
      // esa empresa no pertenece a esta cuenta — incluso al crear la primera empresa propia.
      clearEmpresaActiva(res);
    }

    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
