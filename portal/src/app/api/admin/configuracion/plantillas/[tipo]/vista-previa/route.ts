import { NextRequest } from "next/server";
import { vistaPreviaDePlantilla } from "@/lib/api/admin-configuracion";
import { sobreConfiguracion } from "../../../comun";

/** Cómo se vería un texto con valores de ejemplo (#199): no guarda nada ni deja registro. */
export function POST(req: NextRequest, { params }: { params: Promise<{ tipo: string }> }) {
  return sobreConfiguracion(req, { tipo: params, cuerpo: true }, ({ access, tipo, cuerpo }) => vistaPreviaDePlantilla(access, tipo, cuerpo));
}
