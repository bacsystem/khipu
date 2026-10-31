import type { ApiEnvelope } from "./types";

/**
 * Cuándo un error de transporte deja dudas sobre si la petición llegó (#25). Viven aparte de `browser.ts` para que los componentes
 * que solo necesitan reconocer el corte no dependan de `apiRequest`: un test que reemplaza `browser` no tiene que volver a definirlas.
 */

/** Los tres cortes de red: no se pudo conectar, se cortó antes de la respuesta o venció la espera. */
const CODIGOS_DE_CORTE = new Set(["RED", "RED_CORTADA", "ESPERA_VENCIDA"]);

/** ¿La petición falló por la red (sin conexión, corte a mitad o espera vencida)? No dice si llegó al servidor. */
export function seCortoLaConexion(res: ApiEnvelope<unknown>): boolean {
  return res.codigo !== null && CODIGOS_DE_CORTE.has(res.codigo);
}

/**
 * El error no deja saber si la petición llegó al servidor o no. En un GET da igual, pero en el POST que emite un
 * comprobante es la diferencia entre «no pasó nada» y «se consumió un correlativo»: reintentar a ciegas duplica el
 * documento. Los formularios que emiten usan esto para avisar que hay que mirar antes de volver a intentar.
 */
export function noSeSabeSiLlego(res: ApiEnvelope<unknown>): boolean {
  return seCortoLaConexion(res) || res.codigo === "RESPUESTA_INVALIDA";
}

/** ¿El pedido falló porque venció la espera (y no porque se cortó la conexión)? Los formularios que emiten dicen «tardó» y no «se cortó». Igual que un corte, no se sabe si llegó. */
export function seVencioLaEspera(res: ApiEnvelope<unknown>): boolean {
  return res.codigo === "ESPERA_VENCIDA";
}
