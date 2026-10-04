import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { impersonarUsuario } from "@/lib/api/admin-impersonacion";
import { listarEmpresas } from "@/lib/api/empresas";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { writeAccesoDeSoporte } from "@/lib/session";
import { esUuid } from "@/lib/uuid";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Entrar al portal como un usuario del cliente (#184). El JWT del administrador sale de su cookie `httpOnly`; los dos ids se validan antes de pegarlos en la URL
 * del backend; y la IP real del administrador (#208) viaja ya resuelta para la bitácora. **El token de soporte nunca llega al navegador como dato**: se guarda en
 * la cookie `httpOnly` de acceso del cliente, con la vida que le queda y sin refresh, y la respuesta solo dice a quién se mira y hasta cuándo.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string; usuarioId: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id, usuarioId } = await params;
  if (!esUuid(id) || !esUuid(usuarioId)) return ERROR(400, "ID_INVALIDO", "Identificador inválido");

  try {
    const sesion = await impersonarUsuario(access, id, usuarioId, cabecerasDeOrigen(req.headers));
    // Las páginas del portal leen la empresa activa de su cookie (el login la fija): sin ella, «Empresa» lleva al onboarding. Se lista con el propio token de
    // soporte, que lee, y se fija la primera. Si no se puede listar, la sesión se abre igual: el portal elige la primera por su cuenta.
    const empresas = await listarEmpresas(sesion.access_token).catch(() => []);
    const res = NextResponse.json(
      { estado: "exito", datos: { expira_en: sesion.expira_en, usuario: { email: sesion.usuario.email } }, mensaje: null, codigo: null, errores: null },
      { headers: { "Cache-Control": "no-store" } },
    );
    writeAccesoDeSoporte(res, sesion.access_token, (Date.parse(sesion.expira_en) - Date.now()) / 1000, empresas[0]?.id);
    return res;
  } catch (err) {
    return errorResponse(err);
  }
}
