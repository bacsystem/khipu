import { NextRequest } from "next/server";
import { guardarPlantilla, restaurarPlantilla } from "@/lib/api/admin-configuracion";
import { sobreConfiguracion } from "../../comun";

type Contexto = { params: Promise<{ tipo: string }> };

/** Reemplazar el texto de un correo de la plataforma (#199). */
export function PUT(req: NextRequest, { params }: Contexto) {
  return sobreConfiguracion(req, { tipo: params, cuerpo: true }, ({ access, tipo, cuerpo, origen }) => guardarPlantilla(access, tipo, cuerpo, origen));
}

/** Volver al texto de fábrica de un correo. */
export function DELETE(req: NextRequest, { params }: Contexto) {
  return sobreConfiguracion(req, { tipo: params }, ({ access, tipo, origen }) => restaurarPlantilla(access, tipo, origen));
}
