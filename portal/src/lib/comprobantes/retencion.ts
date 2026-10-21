import type { Mensajes } from "@/i18n/mensajes";
import es from "@/messages/es.json";

/**
 * #313: retención del IGV (catálogo 53, código 62). La informa la factura cuando el cliente es agente de retención: él paga el total menos la retención y la entera a SUNAT.
 * Es informativa (no cambia los totales del comprobante); el monto lo calcula el backend (total × tasa, 2 decimales) y baja el neto pendiente de un crédito.
 */
export type RetencionForm = {
  activa: boolean;
  /** Porcentaje tecleado; la tasa legal es 3. */
  porcentaje: string;
};

export const TASA_LEGAL_RETENCION = "3";
export const RETENCION_VACIA: RetencionForm = { activa: false, porcentaje: TASA_LEGAL_RETENCION };

/** Por qué la retención no vale tal como está, o `null`: mayor que 0 y menor que 100, con hasta 3 decimales (`RetencionIgv`). */
export function errorDeRetencion(r: RetencionForm, messages: Mensajes = es): string | null {
  if (!r.activa) return null;
  const t = r.porcentaje.trim();
  const decimales = t.includes(".") ? t.length - t.indexOf(".") - 1 : 0;
  if (!/^\d*\.?\d+$/.test(t) || Number(t) <= 0 || Number(t) >= 100 || decimales > 3)
    return messages.comprobantes.validacion.retencion;
  return null;
}

export type RetencionApi = { porcentaje: number };

export function retencionAApi(r: RetencionForm): RetencionApi | undefined {
  if (!r.activa || errorDeRetencion(r) !== null) return undefined;
  return { porcentaje: Number(r.porcentaje.trim()) };
}
