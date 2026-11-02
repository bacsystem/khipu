import { NextRequest } from "next/server";
import { reintentarEnvio } from "@/lib/api/admin-errores";
import { sobreComprobante } from "../comun";

/**
 * Reintentar el envío de un comprobante de la cola de errores (#196). Reenvía a SUNAT con las credenciales de la empresa dueña: es una acción de verdad, no una lectura.
 * Que SUNAT vuelva a fallar no es un error: el backend responde 200 con el estado en que quedó.
 */
export async function POST(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  return sobreComprobante(req, ctx, (access, id, origen) => reintentarEnvio(access, id, origen));
}
