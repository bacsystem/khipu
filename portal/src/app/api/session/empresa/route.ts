import { NextRequest, NextResponse } from "next/server";
import { leerCuerpoJson } from "@/lib/api/cuerpo-json";
import { errorSinMensaje } from "@/lib/api/http";
import { writeEmpresaActiva } from "@/lib/session";

/** Elige la empresa activa. No hay backend detrás: el id solo se guarda en la cookie, así que aquí se pide que sea un texto con contenido (no que sea un UUID). */
export async function POST(req: NextRequest) {
  const leido = await leerCuerpoJson<{ empresaId?: unknown }>(req);
  if ("error" in leido) return leido.error;
  const { empresaId } = leido.cuerpo;
  if (typeof empresaId !== "string" || empresaId.trim() === "") return errorSinMensaje(400, "ID_INVALIDO");

  const res = NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null });
  writeEmpresaActiva(res, empresaId);
  return res;
}
