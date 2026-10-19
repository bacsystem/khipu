import type { ApiEnvelope } from "./types";

function sobre<T>(codigo: string, mensaje: string): ApiEnvelope<T> {
  return { estado: "error", datos: null, mensaje, codigo, errores: null };
}

/**
 * El error no deja saber si la petición llegó al servidor o no. En un GET da igual, pero en el POST que emite un
 * comprobante es la diferencia entre «no pasó nada» y «se consumió un correlativo»: reintentar a ciegas duplica el
 * documento. Los formularios que emiten usan esto para avisar que hay que mirar antes de volver a intentar.
 */
export function noSeSabeSiLlego(res: ApiEnvelope<unknown>): boolean {
  return res.codigo === "RED" || res.codigo === "RESPUESTA_INVALIDA";
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
    return sobre("RED", "Se cortó la conexión antes de recibir la respuesta. Verifica el estado del envío antes de reintentar.");
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
    return sobre("RESPUESTA_INVALIDA", `Respuesta inválida del servidor (HTTP ${res.status}). Recarga la página para ver el estado real.`);
  }
}

/**
 * Cuánto se espera una respuesta antes de rendirse (#117). Sin tope, un POST que nunca responde dejaba «Emitiendo…» para siempre. La lectura es rápida; lo que escribe puede
 * esperar a SUNAT dentro de la misma llamada (la emisión envía y espera el CDR), así que tiene un margen mucho mayor. Al vencer cae en la rama de corte de red, que ya avisa
 * que el comprobante pudo haberse emitido.
 */
export const ESPERA_MAXIMA_LECTURA_MS = 30_000;
export const ESPERA_MAXIMA_ESCRITURA_MS = 120_000;

/** El texto de un pedido que venció; los formularios que emiten lo reconocen para decir «tardó» y no «se cortó». */
export const MENSAJE_ESPERA_VENCIDA = "El servidor tardó demasiado en responder. Verifica el estado antes de reintentar: lo que enviaste pudo haberse procesado.";

/** ¿El pedido falló porque venció la espera (y no porque se cortó la conexión)? Igual que un corte, no se sabe si llegó. */
export function seVencioLaEspera(res: ApiEnvelope<unknown>): boolean {
  return res.codigo === "RED" && res.mensaje === MENSAJE_ESPERA_VENCIDA;
}

async function pedir<T>(path: string, init: RequestInit, esperaMs?: number): Promise<ApiEnvelope<T>> {
  let res: Response;
  const espera = esperaMs ?? (init.method === undefined || init.method === "GET" ? ESPERA_MAXIMA_LECTURA_MS : ESPERA_MAXIMA_ESCRITURA_MS);
  try {
    res = await fetch(path, { signal: AbortSignal.timeout(espera), ...init });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") return sobre("RED", MENSAJE_ESPERA_VENCIDA);
    return sobre("RED", "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.");
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

/** `headers`: cabeceras propias del pedido, como la clave de idempotencia de la emisión (#115). */
export async function apiRequest<T>(
  path: string,
  init: { method: string; body?: unknown; headers?: Record<string, string>; /** Espera propia, en ms, para lo que tarda más que lo normal (p. ej. un barrido de integridad). */ esperaMaximaMs?: number },
): Promise<ApiEnvelope<T>> {
  const isFormData = init.body instanceof FormData;
  const tipo: Record<string, string> = isFormData || init.body === undefined ? {} : { "content-type": "application/json" };
  const headers: Record<string, string> = { ...tipo, ...init.headers };
  return pedir<T>(
    path,
    {
      method: init.method,
      headers: Object.keys(headers).length === 0 ? undefined : headers,
      body: isFormData ? (init.body as FormData) : init.body !== undefined ? JSON.stringify(init.body) : undefined,
    },
    init.esperaMaximaMs,
  );
}
