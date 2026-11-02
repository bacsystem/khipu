import { NextRequest } from "next/server";
import { cambiarRemitente, restablecerRemitente } from "@/lib/api/admin-configuracion";
import { sobreConfiguracion } from "../comun";

/** Fijar el remitente de los correos de la plataforma (#199). */
export function PUT(req: NextRequest) {
  return sobreConfiguracion(req, { cuerpo: true }, ({ access, cuerpo, origen }) => cambiarRemitente(access, cuerpo, origen));
}

/** Volver al remitente de la configuración del servidor. */
export function DELETE(req: NextRequest) {
  return sobreConfiguracion(req, {}, ({ access, origen }) => restablecerRemitente(access, origen));
}
