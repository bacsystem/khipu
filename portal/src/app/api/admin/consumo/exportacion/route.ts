import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { exportarConsumoDeCuentas, FILTROS_DE_CONSUMO, mesValido, ORDENES_DE_CONSUMO, paramsConsumoDesdeUrl } from "@/lib/api/admin-consumo";
import { errorResponse } from "@/lib/api/http";
import { ERROR } from "../../planes/comun";

/** Un parámetro que se pidió y no es de los conocidos: se rechaza en vez de exportar, sin avisar, otra cosa que lo que el administrador está mirando. */
function invalido(p: URLSearchParams): boolean {
  const mes = p.get("mes");
  const filtro = p.get("filtro");
  const orden = p.get("orden");
  return (
    (mes !== null && mesValido(mes) === undefined) ||
    (filtro !== null && !(FILTROS_DE_CONSUMO as readonly string[]).includes(filtro)) ||
    (orden !== null && !(ORDENES_DE_CONSUMO as readonly string[]).includes(orden))
  );
}

/**
 * Descarga el consumo de todas las cuentas en CSV (#193): lo que se ve en pantalla (mes, filtro y orden), completo y sin paginar. El JWT del administrador sale de
 * su cookie `httpOnly` y nunca llega al JS. El CSV pasa como bytes, sin decodificar: la marca UTF-8 del principio es lo que hace que Excel lea bien las tildes.
 */
export async function GET(req: NextRequest) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  const query = req.nextUrl.searchParams;
  if (invalido(query)) return ERROR(400, "PARAMETRO_INVALIDO", "El mes, el filtro o el orden no son válidos");

  try {
    const { cuerpo, disposicion } = await exportarConsumoDeCuentas(
      access,
      paramsConsumoDesdeUrl({ mes: query.get("mes") ?? undefined, filtro: query.get("filtro") ?? undefined, orden: query.get("orden") ?? undefined }),
    );
    return new NextResponse(cuerpo, {
      status: 200,
      headers: {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": disposicion ?? 'attachment; filename="consumo.csv"',
        "Cache-Control": "no-store",
      },
    });
  } catch (err) {
    return errorResponse(err);
  }
}
