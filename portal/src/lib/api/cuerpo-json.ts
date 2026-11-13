import type { NextResponse } from "next/server";
import { errorSinMensaje } from "./http";
import type { ApiEnvelope } from "./types";

const CODIGO_JSON_INVALIDO = "JSON_INVALIDO";

type CuerpoLeido<T> = { cuerpo: T } | { error: NextResponse<ApiEnvelope<null>> };

/**
 * El cuerpo de una ruta del BFF como un objeto JSON, o un 400 `JSON_INVALIDO` listo para devolver (sin texto: el navegador lo traduce por el código).
 * `T` no se verifica: asegura que el cuerpo es un objeto, no qué campos trae; sus tipos los valida el backend, una sola vez.
 * Con `opcional`, un cuerpo vacío es `{}`; lo que no es un objeto JSON sigue siendo inválido.
 */
export async function leerCuerpoJson<T extends object = Record<string, unknown>>(req: Request, opciones: { opcional?: boolean } = {}): Promise<CuerpoLeido<T>> {
  const texto = await req.text().catch(() => null);
  if (texto === null) return invalido();
  if (opciones.opcional && texto.trim() === "") return { cuerpo: {} as T };
  try {
    const cuerpo: unknown = JSON.parse(texto);
    return esObjetoPlano(cuerpo) ? { cuerpo: cuerpo as T } : invalido();
  } catch {
    return invalido();
  }
}

function esObjetoPlano(valor: unknown): valor is object {
  return valor !== null && typeof valor === "object" && !Array.isArray(valor);
}

function invalido(): { error: NextResponse<ApiEnvelope<null>> } {
  return { error: errorSinMensaje(400, CODIGO_JSON_INVALIDO) };
}
