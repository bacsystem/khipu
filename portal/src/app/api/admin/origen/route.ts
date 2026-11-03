import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { origenDelBackend } from "@/lib/api/admin-origen";
import { errorResponse } from "@/lib/api/http";
import { confiarEnCloudflare } from "@/lib/cloudflare";
import { ipDelCliente, saltosDeConfianza } from "@/lib/ip-cliente";
import { cabecerasDeOrigen, origenDe } from "@/lib/origen";

/**
 * Calibración de la IP del administrador (#208, #594). Detrás de Railway no se puede deducir cuántos saltos son de confianza, y
 * foros y documentación no coinciden: aquí se ve. Visítese con la sesión de administrador abierta y compárese `ip_backend` con la
 * IP pública propia; si no coincide, se ajustan `TRUSTED_PROXY_HOPS` y `TRUSTED_CLOUDFLARE` (y `TRUSTED_PROXIES` en el backend).
 * Solo administrador. Muestra las cabeceras tal como llegaron, que es lo que el navegador y los proxies escribieron: sirven a quien
 * calibra, no se reenvían.
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
    const cloudflare = confiarEnCloudflare();
    const origen = origenDe(req.headers, saltos, cloudflare);
    const backend = await origenDelBackend(access, cabecerasDeOrigen(req.headers, saltos, cloudflare));
    return NextResponse.json({
      estado: "exito",
      datos: {
        cadena_recibida: cadena,
        saltos,
        // Quien se conectó al proxy de confianza según la cadena; con Cloudflare delante es una IP de Cloudflare.
        ip_de_la_conexion: ipDelCliente(cadena, saltos) ?? null,
        cf_connecting_ip_recibida: req.headers.get("cf-connecting-ip"),
        cloudflare_activado: cloudflare,
        cloudflare_confiada: origen.deCloudflare,
        ip_resuelta: origen.ip ?? null,
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
