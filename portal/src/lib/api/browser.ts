import { avisarSesionTerminada, esSesionTerminada } from "@/lib/sesion/sesion-terminada";
import { seCortoLaConexion, noSeSabeSiLlego, seVencioLaEspera } from "./corte-de-red";
import type { ApiEnvelope } from "./types";

export { seCortoLaConexion, noSeSabeSiLlego, seVencioLaEspera };

/**
 * Un error de transporte viaja solo con su código y sin `mensaje`: los formularios muestran `res.mensaje ?? mensajeError(res.codigo)`,
 * y el texto en el idioma de la sesión sale del catálogo (`errores.<CODIGO>` en messages), no de un literal acá (#25).
 */
function sobre<T>(codigo: string): ApiEnvelope<T> {
  return { estado: "error", datos: null, mensaje: null, codigo, errores: null };
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
    return sobre("RED_CORTADA");
  }

  if (!texto) {
    return res.ok
      ? { estado: "exito", datos: null, mensaje: null, codigo: null, errores: null }
      : sobre("RED");
  }
  // Un 502/504 de un proxy intermedio llega como HTML: `JSON.parse` lanzaba y el llamador quedaba colgado (medido en la
  // auditoría de bajas: diálogo «Enviando a SUNAT…» sin salida). Va con código propio, porque los formularios que emiten
  // lo tratan distinto de un corte: acá la petición llegó, así que es más probable todavía que el documento exista.
  try {
    return JSON.parse(texto) as ApiEnvelope<T>;
  } catch {
    return sobre("RESPUESTA_INVALIDA");
  }
}

/**
 * Cuánto se espera una respuesta antes de rendirse (#117). Sin tope, un POST que nunca responde dejaba «Emitiendo…» para siempre. La lectura es rápida; lo que escribe puede
 * esperar a SUNAT dentro de la misma llamada (la emisión envía y espera el CDR), así que tiene un margen mucho mayor. Al vencer cae en la rama de corte de red, que ya avisa
 * que el comprobante pudo haberse emitido.
 */
export const ESPERA_MAXIMA_LECTURA_MS = 30_000;
export const ESPERA_MAXIMA_ESCRITURA_MS = 120_000;

/**
 * El único `fetch` del navegador hacia el proxy (#654): si la respuesta es el 401 definitivo (la sesión murió y el refresh ya no sirve), avisa para que se abra el diálogo «Tu sesión
 * terminó», y devuelve la `Response` intacta para que quien llama la lea como siempre. `fetch-del-portal.test.ts` impide que aparezca otro `fetch` suelto.
 */
export async function fetchDelPortal(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const res = await fetch(input, init);
  if (esSesionTerminada(res)) avisarSesionTerminada();
  return res;
}

async function pedir<T>(path: string, init: RequestInit, esperaMs?: number): Promise<ApiEnvelope<T>> {
  let res: Response;
  const espera = esperaMs ?? (init.method === undefined || init.method === "GET" ? ESPERA_MAXIMA_LECTURA_MS : ESPERA_MAXIMA_ESCRITURA_MS);
  try {
    res = await fetchDelPortal(path, { signal: AbortSignal.timeout(espera), ...init });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") return sobre("ESPERA_VENCIDA");
    return sobre("RED");
  }
  // Detrás del diálogo no queda un «No autorizado.»: el código de sesión inválida ya tiene su texto.
  if (esSesionTerminada(res)) return sobre("SESION_INVALIDA");
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
