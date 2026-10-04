/** Cabecera de idempotencia de `POST /v1/facturas` (#115). */
export const CABECERA_IDEMPOTENCIA = "Idempotency-Key";

/** Un intento de emisión: el contenido enviado y la clave que lo identifica. */
export type Intento = { contenido: string; clave: string };

/**
 * La clave de la emisión que se va a enviar. Con el mismo contenido que el intento anterior es un reintento (un corte de red, un
 * doble clic) y reusa la clave: si la factura ya se había emitido, el backend devuelve la misma. Si el contenido cambió, es otra
 * factura y estrena clave; reusar la vieja haría que el backend la rechace.
 */
export function intentoPara(previo: Intento | null, contenido: string, nuevaClave: () => string = () => crypto.randomUUID()): Intento {
  if (previo && previo.contenido === contenido) return previo;
  return { contenido, clave: nuevaClave() };
}
