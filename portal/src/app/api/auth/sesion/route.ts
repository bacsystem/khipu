import { NextRequest, NextResponse } from "next/server";
import { refrescar } from "@/lib/api/auth";
import { errorResponse } from "@/lib/api/http";
import { ApiError } from "@/lib/api/types";
import { accesoExpirado } from "@/lib/jwt";
import { clearSession, readSession, writeTokens } from "@/lib/session";

const sinSesion = () =>
  NextResponse.json({ estado: "error", datos: null, mensaje: null, codigo: "NO_AUTORIZADO", errores: null }, { status: 401 });

/**
 * El keep-alive del corte por inactividad (#637): el navegador lo llama, a lo sumo cada 5 minutos, mientras la persona interactúa. El proxy no sirve para esto (`/api/proxy/auth/*`
 * es 404 a propósito, #174) y sondear un endpoint de negocio solo para refrescar sería un efecto de lado. Lo que hace es lo que ya hace el middleware ante un access vencido:
 * con el access vigente no toca el backend; vencido, lo renueva con el refresh y escribe las cookies, que es lo que el servidor cuenta como actividad (#636).
 *
 * No comparte el `refrescarUnaVez` del proxy: una carrera con un refresh del mismo proceso la cubre la gracia de 30 s del backend (S6), igual que las del middleware, que vive en otro runtime.
 * Un fallo que no es «la sesión murió» (red, 5xx) no limpia las cookies: el navegador reintenta en el próximo keep-alive.
 */
export async function POST(req: NextRequest) {
  const { access, refresh } = readSession(req);
  if (access && !accesoExpirado(access)) return new NextResponse(null, { status: 204 });
  if (!refresh) return sinSesion();

  try {
    const res = new NextResponse(null, { status: 204 });
    writeTokens(res, await refrescar(refresh));
    return res;
  } catch (err) {
    const res = errorResponse(err);
    // Solo un 401 es una sesión muerta. Una cuenta suspendida (403 `CUENTA_SUSPENDIDA`, #182) conserva su sesión —como en el middleware—: se responde con su código y el
    // navegador va a la página que se lo explica. Un 429, un 5xx o un corte de red tampoco dicen nada de la sesión.
    if (err instanceof ApiError && err.status === 401) clearSession(res);
    return res;
  }
}
