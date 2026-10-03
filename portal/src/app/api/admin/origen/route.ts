import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { origenDelBackend } from "@/lib/api/admin-origen";
import { errorResponse } from "@/lib/api/http";
import { ipDelCliente, saltosDeConfianza } from "@/lib/ip-cliente";
import { cabecerasDeOrigen } from "@/lib/origen";

/**
 * Calibración de la IP del administrador (#208). Detrás de Railway no se puede deducir cuántos saltos son de confianza, y
 * foros y documentación no coinciden: aquí se ve. Visítese con la sesión de administrador abierta y compárese `ip_backend` con la
 * IP pública propia; si no coincide, se ajusta `TRUSTED_PROXY_HOPS` (y `TRUSTED_PROXIES` en el backend). Solo administrador.
 * Muestra la cadena tal como llegó, que es lo que el navegador y los proxies escribieron: sirve a quien calibra, no se reenvía.
 */
export async function GET(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) {
    return NextResponse.json(
      { estado: "error", datos: null, mensaje: "Sesión de administrador requerida", codigo: "NO_AUTORIZADO", errores: null },
      { status: 401 },
    );
  }

  try {
    const cadena = req.headers.get("x-forwarded-for");
    const saltos = saltosDeConfianza();
    const backend = await origenDelBackend(access, cabecerasDeOrigen(req.headers, saltos));
    return NextResponse.json({
      estado: "exito",
      datos: {
        cadena_recibida: cadena,
        saltos,
        ip_resuelta: ipDelCliente(cadena, saltos) ?? null,
        ip_backend: backend.ip,
      },
      mensaje: null,
      codigo: null,
      errores: null,
    });
  } catch (err) {
    return errorResponse(err);
  }
}
