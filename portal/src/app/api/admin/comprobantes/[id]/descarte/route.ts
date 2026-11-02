import { NextRequest } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { descartarComprobante } from "@/lib/api/admin-errores";
import { ERROR, leerCuerpoJson } from "../../../planes/comun";
import { sobreComprobante } from "../comun";

/**
 * Descartar un comprobante en error de envío (#196): deja de intentarse y queda terminal. El motivo es obligatorio y viaja a la bitácora; acá solo se comprueba que sea un
 * texto (su longitud y que no esté en blanco las decide el backend, una sola vez). La sesión se mira antes que el cuerpo: sin ella, nada más importa.
 */
export async function POST(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  if (!readAdminSession(req).access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");
  const leido = await leerCuerpoJson(req);
  if ("error" in leido) return leido.error;
  const { motivo } = leido.cuerpo as { motivo?: unknown };
  if (motivo !== undefined && motivo !== null && typeof motivo !== "string") return ERROR(422, "MOTIVO_INVALIDO", "El motivo debe ser un texto");
  return sobreComprobante(req, ctx, (access, id, origen) => descartarComprobante(access, id, typeof motivo === "string" ? motivo : "", origen));
}
