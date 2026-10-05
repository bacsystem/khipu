import { NextRequest } from "next/server";
import { publicarBanner, retirarBanner } from "@/lib/api/admin-configuracion";
import { sobreConfiguracion } from "../comun";

/** Publicar (o reemplazar) el aviso de mantenimiento que ven todos los clientes (#199). */
export function PUT(req: NextRequest) {
  return sobreConfiguracion(req, { cuerpo: true }, ({ access, cuerpo, origen }) => publicarBanner(access, cuerpo, origen));
}

/** Retirarlo antes de que venza. */
export function DELETE(req: NextRequest) {
  return sobreConfiguracion(req, {}, ({ access, origen }) => retirarBanner(access, origen));
}
