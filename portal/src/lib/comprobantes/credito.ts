import { interpolar, type Mensajes } from "@/i18n/mensajes";
import { sumarDias } from "@/lib/formato";
import es from "@/messages/es.json";

/**
 * #309: factura al crédito (RS 193-2020, reglas 3244–3260 y 3319–3321). El neto pendiente se reparte en cuotas con vencimiento posterior a la emisión; la suma de
 * las cuotas es el neto pendiente. Lo demás —que el neto pendiente no pase del total, la detracción y la retención que lo descuentan (3319)— lo valida el dominio
 * al calcular (`/calcular`, #405): su mensaje se muestra tal cual.
 */
export type CuotaForm = { monto: string; vencimiento: string };

export type CreditoForm = {
  /** Neto pendiente tecleado; vacío = el total del comprobante. */
  montoPendiente: string;
  cuotas: CuotaForm[];
};

export const CREDITO_VACIO: CreditoForm = { montoPendiente: "", cuotas: [] };

const aCentimos = (n: number) => Math.round(n * 100);
const deCentimos = (c: number) => c / 100;

/** Positivo y con hasta 2 decimales (3253). */
export function montoValido(texto: string): boolean {
  const t = texto.trim();
  if (!/^\d*\.?\d+$/.test(t)) return false;
  const i = t.indexOf(".");
  return Number(t) > 0 && (i < 0 || t.length - i - 1 <= 2);
}

/**
 * `n` cuotas iguales del `neto`, la primera `diasEntre` días después de la emisión y las demás cada `diasEntre`: los céntimos que no reparten exacto van a la
 * última, de modo que la suma sea exactamente el neto (3250).
 */
export function repartirEnCuotas(neto: number, n: number, emision: string, diasEntre = 30): CuotaForm[] {
  if (!(neto > 0) || n < 1) return [];
  const total = aCentimos(neto);
  const base = Math.floor(total / n);
  return Array.from({ length: n }, (_, i) => ({
    monto: deCentimos(i === n - 1 ? total - base * (n - 1) : base).toFixed(2),
    vencimiento: sumarDias(emision, diasEntre * (i + 1)),
  }));
}

/** Cuánto suman las cuotas tecleadas (las ilegibles cuentan como cero). */
export function sumaDeCuotas(cuotas: CuotaForm[]): number {
  return deCentimos(cuotas.reduce((s, q) => s + (montoValido(q.monto) ? aCentimos(Number(q.monto)) : 0), 0));
}

/** Por qué el crédito no vale tal como está escrito, o `null`. El resto de las reglas las dice el backend al calcular. */
export function errorDeCredito(credito: CreditoForm, neto: number, emision: string, messages: Mensajes = es): string | null {
  const t = messages.comprobantes.validacion.credito;
  const pendiente = credito.montoPendiente.trim() === "" ? neto : Number(credito.montoPendiente);
  if (credito.montoPendiente.trim() !== "" && !montoValido(credito.montoPendiente)) return t.netoPendiente;
  if (credito.cuotas.length === 0) return t.sinCuotas;
  for (const [i, q] of credito.cuotas.entries()) {
    if (!montoValido(q.monto)) return interpolar(t.cuotaMonto, { n: i + 1 });
    if (!q.vencimiento) return interpolar(t.cuotaSinVencimiento, { n: i + 1 });
    if (q.vencimiento <= emision) return interpolar(t.vencimientoTrasEmision, { n: i + 1 });
    if (i > 0 && q.vencimiento <= credito.cuotas[i - 1].vencimiento) return interpolar(t.vencimientoTrasCuota, { n: i + 1, anterior: i });
  }
  if (aCentimos(sumaDeCuotas(credito.cuotas)) !== aCentimos(pendiente))
    return interpolar(t.suma, { suma: sumaDeCuotas(credito.cuotas).toFixed(2), pendiente: pendiente.toFixed(2) });
  return null;
}

export type FormaPagoApi = { tipo: "contado" } | { tipo: "credito"; monto_pendiente: number; cuotas: Array<{ monto: number; vencimiento: string }> };

/** La forma de pago con la forma de la API; al contado no manda nada más. `neto` rellena el neto pendiente si se dejó vacío. */
export function formaPagoAApi(credito: CreditoForm | null, neto: number): FormaPagoApi {
  if (credito === null) return { tipo: "contado" };
  return {
    tipo: "credito",
    monto_pendiente: credito.montoPendiente.trim() === "" ? neto : Number(credito.montoPendiente),
    cuotas: credito.cuotas.map((q) => ({ monto: Number(q.monto), vencimiento: q.vencimiento })),
  };
}
