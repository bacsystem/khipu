import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import { ERROR, sinCache } from "../../planes/comun";

/**
 * Lo común de las rutas del BFF sobre un comprobante de la cola de errores (#196). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS; el id se valida
 * antes de pegarlo en la URL del backend; y la IP real del administrador (#208) viaja ya resuelta para que la bitácora la registre. Ninguna respuesta queda en caché.
 */
export async function sobreComprobante(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
  hacer: (access: string, id: string, origen: Record<string, string>) => Promise<unknown>,
) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de comprobante inválido");

  try {
    const datos = await hacer(access, id, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
