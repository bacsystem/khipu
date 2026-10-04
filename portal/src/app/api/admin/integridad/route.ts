import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { verificarIntegridad } from "@/lib/api/admin-integridad";
import { errorResponse } from "@/lib/api/http";
import { validarRango } from "@/lib/integridad-formulario";
import { ERROR, leerCuerpoJson, sinCache } from "../planes/comun";

/**
 * Lanzar la verificación de integridad del almacenamiento (#198). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS. Las dos fechas se validan acá
 * antes de pegarlas en la URL del backend (existen, `desde` ≤ `hasta` y no pasan del tope de días que se verifica por vez); lo que el barrido encuentra lo dice el backend.
 * Solo lee, así que no deja bitácora ni reenvía la IP; la respuesta no se guarda en caché.
 */
export async function POST(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const leido = await leerCuerpoJson(req);
  if ("error" in leido) return leido.error;
  const { desde, hasta } = leido.cuerpo as { desde?: unknown; hasta?: unknown };
  if (typeof desde !== "string" || typeof hasta !== "string") return ERROR(400, "PARAMETRO_INVALIDO", "Faltan las fechas del rango");
  const rango = validarRango(desde, hasta);
  if ("errores" in rango) return ERROR(400, "PARAMETRO_INVALIDO", Object.values(rango.errores).join(" "));

  try {
    const datos = await verificarIntegridad(access, rango.desde, rango.hasta);
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
