import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

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

const TASAS = [
  { codigo: "51", tasa: 2 },
  { codigo: "52", tasa: 1 },
  { codigo: "53", tasa: 0.5 },
] as const;

/** Los regímenes con su etiqueta en el idioma de `messages` (#25). */
export const regimenes = (messages: Mensajes = es): { codigo: string; tasa: number; etiqueta: string }[] =>
  TASAS.map((r) => ({ ...r, etiqueta: messages.documentos.regimenesPercepcion[r.codigo] }));
export const REGIMENES = regimenes();

/** Por qué la percepción no vale tal como está (con la moneda y la forma de pago del comprobante), o `null`. */
export function errorDePercepcion(p: PercepcionForm, contexto: { moneda: string; alCredito: boolean }, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.percepcion;
  if (!p.activa) return null;
  if (contexto.moneda !== "PEN") return t.soloSoles;
  if (contexto.alCredito) return t.soloContado;
  const base = p.base.trim();
  if (base !== "" && (!/^\d*\.?\d+$/.test(base) || Number(base) <= 0 || (base.includes(".") && base.length - base.indexOf(".") - 1 > 2)))
    return t.base;
  return null;
}

export type PercepcionApi = { regimen: string; base?: number };

export function percepcionAApi(p: PercepcionForm, contexto: { moneda: string; alCredito: boolean }): PercepcionApi | undefined {
  if (!p.activa || errorDePercepcion(p, contexto) !== null) return undefined;
  const base = p.base.trim();
  return { regimen: p.regimen, base: base === "" ? undefined : Number(base) };
}
