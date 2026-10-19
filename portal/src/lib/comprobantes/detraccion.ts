/**
 * #288, #297: detracción (SPOT) de una factura o de una boleta. El portal pregunta y avisa; decidir si el servicio está sujeto es del emisor y su contador. El monto lo calcula el backend (en
 * soles: total × porcentaje, redondeado al sol); aquí solo se arman los datos que pide `FacturaRequest.DetraccionDto` (códigos 3127–3129, 3208).
 */
export type DetraccionForm = {
  activa: boolean;
  /** Código del catálogo 54 (bien o servicio sujeto). */
  codigo: string;
  /** Porcentaje tecleado (hasta 5 decimales). */
  porcentaje: string;
  /** Catálogo 59, `001` depósito en cuenta por defecto. */
  medioPago: string;
  /** Cuenta del Banco de la Nación; precargada con la de la empresa. */
  cuenta: string;
  /** Monto a depositar **en soles**: solo se pide si la factura no es en soles (3208). */
  montoEnSoles: string;
};

export const DETRACCION_VACIA: DetraccionForm = { activa: false, codigo: "", porcentaje: "", medioPago: "001", cuenta: "", montoEnSoles: "" };

/**
 * Porcentajes vigentes del Anexo 3 y los demás anexos del SPOT, por código del catálogo 54: lo que SUNAT publica en «Apéndices del Sistema de Detracciones»
 * (orientacion.sunat.gob.pe/apendices-del-sistema-de-detracciones, leído el 2026-10-18; los servicios al 12 % desde el 01.04.2018, RS 071-2018). Es una **sugerencia
 * editable**: la tasa de un código puede cambiar por resolución, y el backend no la conoce, así que el emisor la confirma.
 */
export const PORCENTAJE_SUGERIDO: Record<string, number> = {
  "001": 10, "003": 10, "007": 10, "008": 4, "009": 10, "010": 15, "011": 10, "012": 12, "014": 4, "016": 10, "017": 4, "019": 10, "020": 12, "021": 10,
  "022": 12, "023": 4, "024": 10, "025": 10, "026": 10, "030": 4, "031": 10, "032": 10, "034": 10, "035": 1.5, "036": 1.5, "037": 12, "039": 10, "041": 15,
};

/**
 * Los códigos con tipo de operación propio piden datos que el formulario no captura: 004 (recursos hidrobiológicos, 1002) y 027 (transporte de carga, 1004)
 * llevan datos por ítem; se emiten por la API. El 028 (transporte de pasajeros) va con 1003 y no pide nada más.
 */
export const CODIGOS_SIN_FORMULARIO = new Set(["004", "027"]);

/** El tipo de operación que fija la detracción (3127–3129): 1003 para el transporte de pasajeros, 1001 para el resto. */
export function operacionDeDetraccion(codigo: string): string {
  return codigo === "028" ? "1003" : "1001";
}

/** Umbral del SPOT para servicios: por encima de S/ 700 el emisor debe preguntarse si la operación está sujeta. */
export const UMBRAL_SPOT = 700;

export function avisoDeDetraccion(args: { activa: boolean; moneda: string; total: number; boleta: boolean }): boolean {
  return !args.boleta && !args.activa && args.moneda === "PEN" && args.total > UMBRAL_SPOT;
}

function positivoHasta5(texto: string): boolean {
  const t = texto.trim();
  if (!/^\d*\.?\d+$/.test(t)) return false;
  const i = t.indexOf(".");
  return Number(t) > 0 && Number(t) <= 100 && (i < 0 || t.length - i - 1 <= 5);
}

/** Por qué la detracción no vale tal como está, o `null`. */
export function errorDeDetraccion(d: DetraccionForm, moneda: string): string | null {
  if (!d.activa) return null;
  if (d.codigo === "") return "Elija el bien o servicio sujeto a detracción (catálogo 54).";
  if (!positivoHasta5(d.porcentaje)) return "El porcentaje de la detracción es un número mayor que cero y hasta 100, con hasta 5 decimales.";
  if (d.cuenta.trim() === "") return "Falta la cuenta de detracciones del Banco de la Nación (3034).";
  if (moneda !== "PEN" && !/^\d*\.?\d+$/.test(d.montoEnSoles.trim())) return `En una factura en ${moneda} el monto de la detracción se indica en soles (3208).`;
  return null;
}

export type DetraccionApi = { codigo_bien_servicio: string; porcentaje: number; medio_pago: string; cuenta_banco_nacion: string; monto?: number };

export function detraccionAApi(d: DetraccionForm, moneda: string): DetraccionApi | undefined {
  if (!d.activa || errorDeDetraccion(d, moneda) !== null) return undefined;
  return {
    codigo_bien_servicio: d.codigo,
    porcentaje: Number(d.porcentaje),
    medio_pago: d.medioPago,
    cuenta_banco_nacion: d.cuenta.trim(),
    monto: moneda === "PEN" ? undefined : Number(d.montoEnSoles),
  };
}
