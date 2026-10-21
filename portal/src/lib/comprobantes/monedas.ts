import { interpolar, type Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #316: las monedas del catálogo 02 que khipu emite (`Monedas` en el dominio: las 17 con las que comercia el Perú, cada una probada en e-beta) y el tipo de cambio opcional del
 * comprobante. SUNAT no recibe el tipo de cambio en el XML: khipu lo guarda para el registro de ventas (SIRE) y el PDF, que muestra el equivalente en soles.
 */
const CODIGOS = ["PEN", "USD", "EUR", "ARS", "AUD", "BOB", "BRL", "CAD", "CHF", "CLP", "CNY", "COP", "GBP", "JPY", "MXN", "PYG", "UYU"] as const;

/** Las monedas con su etiqueta en el idioma de `messages` (#25). */
export const monedas = (messages: Mensajes = es): { codigo: string; etiqueta: string }[] =>
  CODIGOS.map((codigo) => ({ codigo, etiqueta: messages.documentos.monedas[codigo] }));

export const MONEDAS: { codigo: string; etiqueta: string }[] = monedas();

/** Tope de un comprobante a un comprador sin documento, en soles (`Comprobante.TOPE_BOLETA_SIN_DOCUMENTO`). */
export const TOPE_SIN_DOCUMENTO_SOLES = 700;

/** Por qué el tipo de cambio tecleado no vale (soles por unidad de la moneda: positivo, hasta 3 enteros y 3 decimales), o `null`; vacío vale (es opcional). */
export function errorDeTipoCambio(texto: string, moneda: string, messages: Mensajes = es): string | null {
  const t = texto.trim();
  if (t === "" || moneda === "PEN") return null;
  const [enteros, decimales = ""] = t.split(".");
  if (!/^\d*\.?\d+$/.test(t) || Number(t) <= 0 || enteros.replace(/^0+(?=\d)/, "").length > 3 || decimales.length > 3)
    return interpolar(messages.comprobantes.validacion.tipoCambio, { moneda });
  return null;
}

/** El tipo de cambio como número, o `undefined` si está vacío, no vale o la moneda es soles. */
export function tipoCambioAApi(texto: string, moneda: string): number | undefined {
  if (moneda === "PEN" || texto.trim() === "" || errorDeTipoCambio(texto, moneda) !== null) return undefined;
  return Number(texto.trim());
}

/** ¿Pasa el total del tope de «sin documento»? En soles, directo; en otra moneda solo se puede medir con el tipo de cambio, y sin él se pide el documento. */
export function superaElTopeSinDocumento(total: number, moneda: string, tipoCambio: number | undefined): boolean {
  if (moneda === "PEN") return total > TOPE_SIN_DOCUMENTO_SOLES;
  return tipoCambio === undefined || Math.round(total * tipoCambio * 100) / 100 > TOPE_SIN_DOCUMENTO_SOLES;
}
