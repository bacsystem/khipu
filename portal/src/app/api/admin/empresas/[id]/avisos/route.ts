import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { avisarAlCliente, type TipoDeAviso } from "@/lib/api/admin-avisos";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";
import { ERROR, leerCuerpoJson, sinCache } from "../../../planes/comun";

const TIPOS: readonly TipoDeAviso[] = ["CERTIFICADO", "CREDENCIALES_SOL"];

/**
 * Avisarle a un cliente por correo (#197): manda un correo de verdad a la cuenta de la empresa, así que el JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS.
 * La sesión se mira antes que el cuerpo y el id antes de pegarlo en la URL del backend; el tipo se comprueba acá porque el backend rechaza lo desconocido con un error de forma
 * (qué se avisa, si hay a quién y si ya se avisó lo decide él, una sola vez). La IP real del administrador (#208) viaja ya resuelta para la bitácora.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de empresa inválido");

  const leido = await leerCuerpoJson(req);
  if ("error" in leido) return leido.error;
  const { tipo } = leido.cuerpo as { tipo?: unknown };
  if (typeof tipo !== "string" || !(TIPOS as readonly string[]).includes(tipo)) return ERROR(422, "TIPO_INVALIDO", "Indica qué se le avisa al cliente");

  try {
    const datos = await avisarAlCliente(access, id, tipo as TipoDeAviso, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
