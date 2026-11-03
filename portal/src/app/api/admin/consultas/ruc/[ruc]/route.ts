import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { consultarRucComoAdmin } from "@/lib/api/admin-consulta-ruc";
import { errorResponse } from "@/lib/api/http";
import { ERROR, sinCache } from "../../../planes/comun";

/**
 * Consultar un RUC en el padrón de SUNAT desde el backoffice (#575): el alta asistida autocompleta la razón social. El JWT del administrador sale de su cookie
 * `httpOnly` y nunca llega al JS. El RUC se valida acá (11 dígitos) antes de pegarlo en la URL del backend; el dígito verificador lo revisa el backend
 * (`400 PARAMETRO_INVALIDO`). Solo lee: no deja bitácora; la respuesta no se guarda en caché.
 */
export async function GET(req: NextRequest, { params }: { params: Promise<{ ruc: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { ruc } = await params;
  if (!/^\d{11}$/.test(ruc)) return ERROR(400, "PARAMETRO_INVALIDO", "Consulta de RUC: el RUC tiene 11 dígitos");

  try {
    const datos = await consultarRucComoAdmin(access, ruc);
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
