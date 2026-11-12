import { NextRequest, NextResponse } from "next/server";
import { registroAbierto } from "@/lib/acceso";
import { registrar, type DatosDeRegistro } from "@/lib/api/auth";
import { leerCuerpoJson } from "@/lib/api/cuerpo-json";
import { errorResponse } from "@/lib/api/http";
import { clearEmpresaActiva, writeTokens } from "@/lib/session";

export async function POST(req: NextRequest) {
  // Cerrar solo la página dejaría el endpoint abierto: cualquiera podría crear una cuenta con un POST.
  if (!registroAbierto())
    return NextResponse.json(
      { estado: "error", datos: null, mensaje: "El registro está por invitación: escríbenos para pedir acceso.", codigo: "REGISTRO_CERRADO", errores: null },
      { status: 403 },
    );
  const leido = await leerCuerpoJson<Omit<DatosDeRegistro, "acepta_terminos" | "plan"> & { acepta_terminos?: unknown; plan?: unknown }>(req);
  if ("error" in leido) return leido.error;
  const { nombre, email, password, telefono, acepta_terminos, plan } = leido.cuerpo;
  // #581: el plan elegido es opcional y solo un nombre corto; cualquier otra cosa no se reenvía (el backend igual ignora uno que no sea un plan publicado).
  const planElegido = typeof plan === "string" && plan.trim() !== "" && plan.trim().length <= 40 ? plan.trim() : undefined;
  try {
    // #342: solo un `true` cuenta como aceptación; cualquier otra cosa llega como `false` y el backend responde 400 TERMINOS_NO_ACEPTADOS.
    const tokens = await registrar({ nombre, email, password, telefono, acepta_terminos: acepta_terminos === true, plan: planElegido });
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
