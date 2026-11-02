import { NextResponse } from "next/server";
import { clearSession } from "@/lib/session";

/**
 * Salir de una sesión de soporte (#184): borra las cookies de cliente de este navegador y nada más. No llama al backend a propósito: una sesión de soporte es de
 * solo lectura (el `logout` del backend, que escribe, se lo negaría) y no tiene refresh que invalidar; el token vence solo. La sesión del administrador es otra
 * cookie y no se toca: es a donde se vuelve.
 */
export async function POST() {
  const res = NextResponse.json({ estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  clearSession(res);
  return res;
}
