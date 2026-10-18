/**
 * #316: las monedas del catálogo 02 que khipu emite (`Monedas` en el dominio: las 17 con las que comercia el Perú, cada una probada en e-beta) y el tipo de cambio opcional del
 * comprobante. SUNAT no recibe el tipo de cambio en el XML: khipu lo guarda para el registro de ventas (SIRE) y el PDF, que muestra el equivalente en soles.
 */
export const MONEDAS: { codigo: string; etiqueta: string }[] = [
  { codigo: "PEN", etiqueta: "S/ Soles (PEN)" },
  { codigo: "USD", etiqueta: "$ Dólares americanos (USD)" },
  { codigo: "EUR", etiqueta: "€ Euros (EUR)" },
  { codigo: "ARS", etiqueta: "Pesos argentinos (ARS)" },
  { codigo: "AUD", etiqueta: "Dólares australianos (AUD)" },
  { codigo: "BOB", etiqueta: "Bolivianos (BOB)" },
  { codigo: "BRL", etiqueta: "Reales (BRL)" },
  { codigo: "CAD", etiqueta: "Dólares canadienses (CAD)" },
  { codigo: "CHF", etiqueta: "Francos suizos (CHF)" },
  { codigo: "CLP", etiqueta: "Pesos chilenos (CLP)" },
  { codigo: "CNY", etiqueta: "Yuanes (CNY)" },
  { codigo: "COP", etiqueta: "Pesos colombianos (COP)" },
  { codigo: "GBP", etiqueta: "Libras esterlinas (GBP)" },
  { codigo: "JPY", etiqueta: "Yenes (JPY)" },
  { codigo: "MXN", etiqueta: "Pesos mexicanos (MXN)" },
  { codigo: "PYG", etiqueta: "Guaraníes (PYG)" },
  { codigo: "UYU", etiqueta: "Pesos uruguayos (UYU)" },
];

/** Tope de un comprobante a un comprador sin documento, en soles (`Comprobante.TOPE_BOLETA_SIN_DOCUMENTO`). */
export const TOPE_SIN_DOCUMENTO_SOLES = 700;

/** Por qué el tipo de cambio tecleado no vale (soles por unidad de la moneda: positivo, hasta 3 enteros y 3 decimales), o `null`; vacío vale (es opcional). */
export function errorDeTipoCambio(texto: string, moneda: string): string | null {
  const t = texto.trim();
  if (t === "" || moneda === "PEN") return null;
  const [enteros, decimales = ""] = t.split(".");
  if (!/^\d*\.?\d+$/.test(t) || Number(t) <= 0 || enteros.replace(/^0+(?=\d)/, "").length > 3 || decimales.length > 3)
    return `El tipo de cambio son los soles por 1 ${moneda}: un número mayor que cero, de hasta 3 enteros y 3 decimales (por ejemplo 3.756).`;
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
