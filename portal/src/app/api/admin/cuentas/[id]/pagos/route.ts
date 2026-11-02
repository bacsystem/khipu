import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { registrarPago, type CuerpoDePago } from "@/lib/api/admin-pagos";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import { ERROR, leerCuerpoJson, sinCache } from "../../../planes/comun";

/**
 * Registrar a mano el pago de una cuenta (#194). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS; el id se valida antes de pegarlo en la URL
 * del backend, la IP real del administrador (#208) viaja ya resuelta para que la bitácora la registre, y el cuerpo se reenvía sin tocarlo: el periodo, el monto, los
 * duplicados y si el pago puede extender el vencimiento los decide el backend, una sola vez.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de cuenta inválido");

  const leido = await leerCuerpoJson(req);
  if ("error" in leido) return leido.error;

  try {
    const datos = await registrarPago(access, id, leido.cuerpo as CuerpoDePago, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { status: 201, ...sinCache });
  } catch (err) {
    return errorResponse(err);
  }
}
