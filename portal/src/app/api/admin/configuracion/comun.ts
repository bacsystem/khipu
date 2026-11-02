import { NextRequest, NextResponse } from "next/server";
import { readAdminSession } from "@/lib/admin-session";
import { esTipoDeCorreo } from "@/lib/api/admin-configuracion";
import { errorResponse } from "@/lib/api/http";
import { cabecerasDeOrigen } from "@/lib/origen";
import { ERROR, leerCuerpoJson, sinCache } from "../planes/comun";

type Entrada = { access: string; origen: Record<string, string>; tipo: string; cuerpo: object };

/**
 * Lo común de las rutas del BFF de la configuración de la plataforma (#199). Cambiar el remitente, el texto de un correo o el aviso afecta a todos los clientes: el JWT del
 * administrador sale de su cookie `httpOnly` y nunca llega al JS; la sesión se mira antes que nada, el nombre del correo antes de pegarlo en la URL del backend y el cuerpo
 * recién después; y la IP real del administrador (#208) viaja ya resuelta para la bitácora. Ninguna respuesta queda en caché. Las reglas de cada dato las pone el backend, una vez.
 */
export async function sobreConfiguracion(
  req: NextRequest,
  entrada: { tipo?: Promise<{ tipo: string }>; cuerpo?: boolean },
  hacer: (e: Entrada) => Promise<unknown>,
) {
  const { access } = readAdminSession(req);
  if (!access) return ERROR(401, "NO_AUTORIZADO", "Sesión de administrador requerida");

  let tipo = "";
  if (entrada.tipo) {
    tipo = (await entrada.tipo).tipo;
    if (!esTipoDeCorreo(tipo)) return ERROR(400, "TIPO_INVALIDO", "Correo inválido");
  }

  let cuerpo: object = {};
  if (entrada.cuerpo) {
    const leido = await leerCuerpoJson(req);
    if ("error" in leido) return leido.error;
    cuerpo = leido.cuerpo;
  }

  try {
    const datos = await hacer({ access, origen: cabecerasDeOrigen(req.headers), tipo, cuerpo });
    return NextResponse.json({ estado: "exito", datos: datos ?? null, mensaje: null, codigo: null, errores: null }, sinCache);
  } catch (err) {
    return errorResponse(err);
  }
}
