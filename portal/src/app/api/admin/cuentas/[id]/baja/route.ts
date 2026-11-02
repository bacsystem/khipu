import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { darDeBajaCuenta } from "@/lib/api/admin-baja";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { esUuid } from "@/lib/uuid";

const ERROR = (status: number, codigo: string, mensaje: string) =>
  NextResponse.json({ estado: "error", datos: null, mensaje, codigo, errores: null }, { status });

/**
 * Dar de baja una cuenta (#201). El JWT del administrador sale de su cookie `httpOnly` y nunca llega al JS. El id se valida antes de
 * pegarlo en la URL del backend, y la IP real del administrador (#208) viaja ya resuelta para que la bitácora la registre.
 * El cuerpo es opcional: `{ "motivo": "…" }`.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const { id } = await params;
  if (!esUuid(id)) return ERROR(400, "ID_INVALIDO", "Identificador de cuenta inválido");

  let motivo: string | undefined;
  try {
    const texto = await req.text();
    if (texto.trim()) {
      const cuerpo = JSON.parse(texto) as { motivo?: unknown };
      if (cuerpo.motivo !== undefined && cuerpo.motivo !== null && typeof cuerpo.motivo !== "string")
        return ERROR(422, "MOTIVO_INVALIDO", "El motivo debe ser un texto");
      motivo = typeof cuerpo.motivo === "string" && cuerpo.motivo.trim() ? cuerpo.motivo : undefined;
    }
  } catch {
    return ERROR(400, "JSON_INVALIDO", "Petición inválida");
  }

  try {
    const datos = await darDeBajaCuenta(access, id, motivo, cabecerasDeOrigen(req.headers));
    return NextResponse.json({ estado: "exito", datos, mensaje: null, codigo: null, errores: null }, { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    return errorResponse(err);
  }
}
