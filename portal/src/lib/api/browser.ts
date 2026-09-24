import type { ApiEnvelope } from "./types";

function sobre<T>(codigo: string, mensaje: string): ApiEnvelope<T> {
  return { estado: "error", datos: null, mensaje, codigo, errores: null };
}

/**
 * Ninguna de estas funciones lanza: siempre devuelven un sobre. Los formularios hacen
 * `const res = await …` sin try/catch y apagan el «Enviando…» al mirar `res.estado`, así que una
 * excepción acá dejaba el botón deshabilitado para siempre, sin un mensaje que explicara nada.
 * Pasa con el wifi caído, con el túnel cortado a mitad de la subida de un .p12 y con cualquier
 * respuesta que no sea JSON (un 502 con HTML de un proxy, típico).
 */
async function toEnvelope<T>(res: Response): Promise<ApiEnvelope<T>> {
  let texto: string;
  try {
    texto = await res.text();
  } catch {
    return sobre("RED", "Se cortó la conexión antes de recibir la respuesta. Verificá el estado del envío antes de reintentar.");
  }

  if (!texto) {
    return res.ok
      ? { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }
      : sobre("RED", "Error de comunicación con la API");
  }
  // Un 502/504 de un proxy intermedio llega como HTML: `JSON.parse` lanzaba y el llamador quedaba colgado (medido en la
  // auditoría de bajas: diálogo «Enviando a SUNAT…» sin salida). Va con código propio, porque los formularios que emiten
  // lo tratan distinto de un corte: acá la petición llegó, así que es más probable todavía que el documento exista.
  try {
    return JSON.parse(texto) as ApiEnvelope<T>;
  } catch {
    return sobre("RESPUESTA_INVALIDA", `Respuesta inválida del servidor (HTTP ${res.status}). Recargá la página para ver el estado real.`);
  }
}

async function pedir<T>(path: string, init: RequestInit): Promise<ApiEnvelope<T>> {
  let res: Response;
  try {
    res = await fetch(path, init);
  } catch {
    return sobre("RED", "No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo.");
  }
  return toEnvelope<T>(res);
}

export async function postJson<T>(path: string, body: unknown): Promise<ApiEnvelope<T>> {
  return pedir<T>(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function apiRequest<T>(
  path: string,
  init: { method: string; body?: unknown },
): Promise<ApiEnvelope<T>> {
  const isFormData = init.body instanceof FormData;
  return pedir<T>(path, {
    method: init.method,
    headers: isFormData || init.body === undefined ? undefined : { "content-type": "application/json" },
    body: isFormData ? (init.body as FormData) : init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
}
