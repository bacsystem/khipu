/**
 * #297: percepción del IGV de un agente de percepción (catálogo 53: régimen 51, 52 o 53; tasas del catálogo 22). Solo con el tipo de operación 2001, en soles y al contado
 * (2788, 3330). El monto lo calcula el backend (base × tasa); aquí se arman los datos de `PercepcionDto` y se avisa lo que ya se sabe que no vale.
 */
export type PercepcionForm = {
  activa: boolean;
  /** Régimen del catálogo 53. */
  regimen: string;
  /** Base de la percepción tecleada; vacía = el importe total. */
  base: string;
};

export const PERCEPCION_VACIA: PercepcionForm = { activa: false, regimen: "51", base: "" };

/** Tipo de operación que fija la percepción. */
export const OPERACION_PERCEPCION = "2001";

export const REGIMENES: { codigo: string; tasa: number; etiqueta: string }[] = [
  { codigo: "51", tasa: 2, etiqueta: "Percepción venta interna" },
  { codigo: "52", tasa: 1, etiqueta: "Percepción a la adquisición de combustible" },
  { codigo: "53", tasa: 0.5, etiqueta: "Percepción realizada al agente de percepción con tasa especial" },
];

/** Por qué la percepción no vale tal como está (con la moneda y la forma de pago del comprobante), o `null`. */
export function errorDePercepcion(p: PercepcionForm, contexto: { moneda: string; alCredito: boolean }): string | null {
  if (!p.activa) return null;
  if (contexto.moneda !== "PEN") return "La percepción solo se informa en comprobantes en soles (2788).";
  if (contexto.alCredito) return "La percepción solo se informa en comprobantes al contado (3330).";
  const base = p.base.trim();
  if (base !== "" && (!/^\d*\.?\d+$/.test(base) || Number(base) <= 0 || (base.includes(".") && base.length - base.indexOf(".") - 1 > 2)))
    return "La base de la percepción es un monto mayor que cero con hasta 2 decimales (3233).";
  return null;
}

export type PercepcionApi = { regimen: string; base?: number };

export function percepcionAApi(p: PercepcionForm, contexto: { moneda: string; alCredito: boolean }): PercepcionApi | undefined {
  if (!p.activa || errorDePercepcion(p, contexto) !== null) return undefined;
  const base = p.base.trim();
  return { regimen: p.regimen, base: base === "" ? undefined : Number(base) };
}
